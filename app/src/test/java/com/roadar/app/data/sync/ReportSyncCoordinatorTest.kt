package com.roadar.app.data.sync

import com.roadar.app.data.report.FirebaseSyncState
import com.roadar.app.data.report.LocationSource
import com.roadar.app.data.report.PendingReportDeletion
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.ReportingMode
import com.roadar.app.data.report.SynchronizationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSyncCoordinatorTest {
    @Test
    fun serverAcknowledgedUploadMarksTheExistingReportSynced() = runBlocking {
        val report = report(REPORT_ID_1)
        val local = FakeLocalStore(listOf(report))
        val remote = FakeRemoteStore()

        val result = coordinator(local, remote).synchronize()

        assertEquals(SyncRunResult.COMPLETE, result)
        assertEquals(SynchronizationStatus.SYNCED, local.reports.getValue(report.id).synchronizationStatus)
        assertEquals(setOf(report.id), remote.documents.keys.map { it.second }.toSet())
        assertTrue((local.syncState.lastSuccessfulSyncUtcMillis ?: 0L) > 0L)
    }

    @Test
    fun retryAfterLostAcknowledgmentReusesTheSameDocumentId() = runBlocking {
        val report = report(REPORT_ID_1)
        val local = FakeLocalStore(listOf(report))
        val remote = FakeRemoteStore().apply { failAfterFirstWrite = true }
        val coordinator = coordinator(local, remote)

        try {
            coordinator.synchronize()
            throw AssertionError("The first attempt should have a transient acknowledgment failure.")
        } catch (_: RetryableReportSyncException) {
            assertEquals(SynchronizationStatus.PENDING, local.reports.getValue(report.id).synchronizationStatus)
        }

        assertEquals(SyncRunResult.COMPLETE, coordinator.synchronize())
        assertEquals(2, remote.uploadedDocumentIds.size)
        assertEquals(listOf(report.id, report.id), remote.uploadedDocumentIds)
        assertEquals(1, remote.documents.size)
    }

    @Test
    fun multiplePendingReportsAreProcessedInBoundedBatches() = runBlocking {
        val first = report(REPORT_ID_1)
        val second = report(REPORT_ID_2, createdAt = first.createdAtUtcMillis + 1_000)
        val local = FakeLocalStore(listOf(first, second))
        val remote = FakeRemoteStore()
        val coordinator = coordinator(local, remote)

        assertEquals(SyncRunResult.MORE_WORK, coordinator.synchronize(maxOperations = 1))
        assertEquals(1, remote.documents.size)
        assertEquals(SyncRunResult.COMPLETE, coordinator.synchronize(maxOperations = 1))
        assertEquals(2, remote.documents.size)
    }

    @Test
    fun permanentReportRejectionMarksOnlyThatReportFailed() = runBlocking {
        val rejected = report(REPORT_ID_1)
        val accepted = report(REPORT_ID_2, createdAt = rejected.createdAtUtcMillis + 1_000)
        val local = FakeLocalStore(listOf(rejected, accepted))
        val remote = FakeRemoteStore().apply {
            rejectReportId = rejected.id
        }

        assertEquals(SyncRunResult.COMPLETE, coordinator(local, remote).synchronize())
        assertEquals(
            SynchronizationStatus.FAILED,
            local.reports.getValue(rejected.id).synchronizationStatus
        )
        assertEquals(
            SynchronizationStatus.SYNCED,
            local.reports.getValue(accepted.id).synchronizationStatus
        )
        assertEquals("FIRESTORE_PERMISSION_DENIED", local.syncState.lastErrorCode)
    }

    @Test
    fun pendingDeletionRemovesTheRemoteCopyAndClearsTombstoneAfterAcknowledgment() = runBlocking {
        val report = report(REPORT_ID_1, SynchronizationStatus.SYNCED)
        val local = FakeLocalStore(emptyList()).apply {
            ownerUid = OWNER_UID
            remoteDeleteLocally(report)
        }
        val remote = FakeRemoteStore().apply {
            documents[OWNER_UID to report.id] = report
        }

        assertEquals(SyncRunResult.COMPLETE, coordinator(local, remote).synchronize())
        assertFalse(remote.documents.containsKey(OWNER_UID to report.id))
        assertTrue(local.deletions.isEmpty())
    }

    @Test
    fun interruptedRemoteDeletionKeepsTheDurableTombstoneForRetry() = runBlocking {
        val report = report(REPORT_ID_1, SynchronizationStatus.SYNCED)
        val local = FakeLocalStore(emptyList()).apply {
            ownerUid = OWNER_UID
            remoteDeleteLocally(report)
        }
        val remote = FakeRemoteStore().apply {
            documents[OWNER_UID to report.id] = report
            failDelete = true
        }
        val coordinator = coordinator(local, remote)

        try {
            coordinator.synchronize()
            throw AssertionError("The delete interruption should be retried.")
        } catch (_: RetryableReportSyncException) {
            assertTrue(local.deletions.containsKey(report.id))
            assertFalse(local.reports.containsKey(report.id))
        }

        remote.failDelete = false
        assertEquals(SyncRunResult.COMPLETE, coordinator.synchronize())
        assertTrue(local.deletions.isEmpty())
        assertFalse(remote.documents.containsKey(OWNER_UID to report.id))
    }

    @Test
    fun deletionDuringAnUploadCannotRecreateTheRemoteDocument() = runBlocking {
        val report = report(REPORT_ID_1)
        val local = FakeLocalStore(listOf(report))
        val remote = FakeRemoteStore().apply {
            beforeUpload = { local.remoteDeleteLocally(report) }
        }

        assertEquals(SyncRunResult.COMPLETE, coordinator(local, remote).synchronize())
        assertTrue(local.reports.isEmpty())
        assertTrue(local.deletions.isEmpty())
        assertFalse(remote.documents.containsKey(OWNER_UID to report.id))
    }

    @Test
    fun changedAnonymousIdentityBlocksUploadsAndPreservesPendingReports() = runBlocking {
        val report = report(REPORT_ID_1)
        val local = FakeLocalStore(listOf(report)).apply {
            ownerUid = OWNER_UID
        }
        val remote = FakeRemoteStore()

        assertEquals(
            SyncRunResult.BLOCKED,
            coordinator(local, remote, uid = "different-owner").synchronize()
        )
        assertEquals(SynchronizationStatus.PENDING, local.reports.getValue(report.id).synchronizationStatus)
        assertEquals("ACCOUNT_IDENTITY_MISMATCH", local.syncState.lastErrorCode)
        assertTrue(remote.uploadedDocumentIds.isEmpty())
    }

    @Test
    fun syncedReportsAreNotUploadedAgain() = runBlocking {
        val report = report(REPORT_ID_1, SynchronizationStatus.SYNCED)
        val local = FakeLocalStore(listOf(report))
        val remote = FakeRemoteStore()

        assertEquals(SyncRunResult.COMPLETE, coordinator(local, remote).synchronize())
        assertTrue(remote.uploadedDocumentIds.isEmpty())
    }

    @Test
    fun deletingBeforeFirstSyncQueuesDeletionInsteadOfUploading() = runBlocking {
        val report = report(REPORT_ID_1)
        val local = FakeLocalStore(listOf(report)).apply {
            remoteDeleteLocally(report)
        }
        val remote = FakeRemoteStore()

        assertEquals(SyncRunResult.COMPLETE, coordinator(local, remote).synchronize())
        assertTrue(remote.uploadedDocumentIds.isEmpty())
        assertTrue(remote.deletedDocumentIds.contains(report.id))
        assertTrue(local.deletions.isEmpty())
    }

    @Test
    fun disabledSyncDoesNotAuthenticateUploadOrDeleteAndKeepsTombstones() = runBlocking {
        var identityRequests = 0
        val report = report(REPORT_ID_1)
        val uploadLocal = FakeLocalStore(listOf(report))
        val uploadRemote = FakeRemoteStore()
        val disabledCoordinator = ReportSyncCoordinator(
            localStore = uploadLocal,
            identityProvider = AnonymousIdentityProvider {
                identityRequests += 1
                OWNER_UID
            },
            remoteStore = uploadRemote,
            isSyncEnabled = { false }
        )

        assertEquals(SyncRunResult.DISABLED, disabledCoordinator.synchronize())
        assertEquals(0, identityRequests)
        assertTrue(uploadRemote.uploadedDocumentIds.isEmpty())
        assertEquals(
            SynchronizationStatus.PENDING,
            uploadLocal.reports.getValue(report.id).synchronizationStatus
        )

        val deleteLocal = FakeLocalStore(listOf(report)).apply {
            remoteDeleteLocally(report)
        }
        val deleteRemote = FakeRemoteStore().apply {
            documents[OWNER_UID to report.id] = report
        }
        val disabledDeleteCoordinator = ReportSyncCoordinator(
            localStore = deleteLocal,
            identityProvider = AnonymousIdentityProvider {
                identityRequests += 1
                OWNER_UID
            },
            remoteStore = deleteRemote,
            isSyncEnabled = { false }
        )

        assertEquals(SyncRunResult.DISABLED, disabledDeleteCoordinator.synchronize())
        assertEquals(0, identityRequests)
        assertTrue(deleteRemote.deletedDocumentIds.isEmpty())
        assertTrue(deleteLocal.deletions.containsKey(report.id))
    }

    @Test
    fun settingTurnedOffAfterIdentityCheckStopsBeforeRemoteOperation() = runBlocking {
        var syncEnabled = true
        val report = report(REPORT_ID_1)
        val local = FakeLocalStore(listOf(report))
        val remote = FakeRemoteStore()
        val coordinator = ReportSyncCoordinator(
            localStore = local,
            identityProvider = AnonymousIdentityProvider {
                syncEnabled = false
                OWNER_UID
            },
            remoteStore = remote,
            isSyncEnabled = { syncEnabled }
        )

        assertEquals(SyncRunResult.DISABLED, coordinator.synchronize())
        assertTrue(remote.uploadedDocumentIds.isEmpty())
        assertEquals(SynchronizationStatus.PENDING, local.reports.getValue(report.id).synchronizationStatus)
    }

    private fun coordinator(
        local: FakeLocalStore,
        remote: FakeRemoteStore,
        uid: String = OWNER_UID,
        isSyncEnabled: () -> Boolean = { true }
    ) = ReportSyncCoordinator(
        localStore = local,
        identityProvider = AnonymousIdentityProvider { uid },
        remoteStore = remote,
        isSyncEnabled = isSyncEnabled,
        nowUtcMillis = { NOW }
    )

    private class FakeLocalStore(
        initialReports: List<PotholeReport>
    ) : ReportSyncLocalStore {
        val reports = initialReports.associateByTo(linkedMapOf(), PotholeReport::id)
        val deletions = linkedMapOf<String, PendingReportDeletion>()
        var syncState = FirebaseSyncState()
            private set
        var ownerUid: String?
            get() = syncState.ownerUid
            set(value) {
                syncState = syncState.copy(ownerUid = value)
            }

        fun remoteDeleteLocally(report: PotholeReport) {
            reports.remove(report.id)
            if (report.syncEligible) {
                deletions[report.id] = PendingReportDeletion(report.id, NOW)
            }
        }

        override suspend fun getOrCreateSyncState(): FirebaseSyncState = syncState

        override suspend fun bindOwnerUid(uid: String): Boolean {
            if (syncState.ownerUid == null) {
                syncState = syncState.copy(ownerUid = uid)
            }
            return syncState.ownerUid == uid
        }

        override suspend fun recordError(errorCode: String) {
            syncState = syncState.copy(lastErrorCode = errorCode)
        }

        override suspend fun clearError() {
            if (reports.values.none { it.synchronizationStatus == SynchronizationStatus.FAILED }) {
                syncState = syncState.copy(lastErrorCode = null)
            }
        }

        override suspend fun nextPendingDeletion(): PendingReportDeletion? =
            deletions.values.firstOrNull()

        override suspend fun confirmRemoteDeletion(reportId: String, timeUtcMillis: Long) {
            deletions.remove(reportId)
            syncState = syncState.copy(lastSuccessfulSyncUtcMillis = timeUtcMillis)
        }

        override suspend fun nextPendingReport(): PotholeReport? =
            reports.values
                .filter {
                    it.syncEligible &&
                        it.synchronizationStatus == SynchronizationStatus.PENDING &&
                        it.id !in deletions
                }
                .minByOrNull(PotholeReport::createdAtUtcMillis)

        override suspend fun markReportSynced(
            reportId: String,
            timeUtcMillis: Long
        ): Boolean {
            val report = reports[reportId] ?: return false
            if (
                !report.syncEligible ||
                reportId in deletions ||
                report.synchronizationStatus != SynchronizationStatus.PENDING
            ) {
                return false
            }
            reports[reportId] = report.copy(synchronizationStatus = SynchronizationStatus.SYNCED)
            syncState = syncState.copy(
                lastSuccessfulSyncUtcMillis = timeUtcMillis,
                lastErrorCode = if (
                    reports.values.any {
                        it.synchronizationStatus == SynchronizationStatus.FAILED
                    }
                ) {
                    syncState.lastErrorCode
                } else {
                    null
                }
            )
            return true
        }

        override suspend fun markReportFailed(reportId: String) {
            reports[reportId]?.let {
                if (it.syncEligible) {
                    reports[reportId] = it.copy(synchronizationStatus = SynchronizationStatus.FAILED)
                }
            }
        }

        override suspend fun hasPendingWork(): Boolean =
            deletions.isNotEmpty() || nextPendingReport() != null
    }

    private class FakeRemoteStore : RemoteReportStore {
        val documents = linkedMapOf<Pair<String, String>, PotholeReport>()
        val uploadedDocumentIds = mutableListOf<String>()
        val deletedDocumentIds = mutableListOf<String>()
        var failAfterFirstWrite = false
        var failDelete = false
        var rejectReportId: String? = null
        var beforeUpload: (suspend () -> Unit)? = null

        override suspend fun upsertReport(uid: String, report: PotholeReport) {
            uploadedDocumentIds += report.id
            beforeUpload?.invoke()
            if (rejectReportId == report.id) {
                throw PermanentReportSyncException("FIRESTORE_PERMISSION_DENIED", report.id)
            }
            documents[uid to report.id] = report
            if (failAfterFirstWrite) {
                failAfterFirstWrite = false
                throw RetryableReportSyncException("FIRESTORE_TEMPORARILY_UNAVAILABLE")
            }
        }

        override suspend fun deleteReport(uid: String, reportId: String) {
            deletedDocumentIds += reportId
            if (failDelete) {
                throw RetryableReportSyncException("FIRESTORE_TEMPORARILY_UNAVAILABLE")
            }
            documents.remove(uid to reportId)
        }
    }

    private companion object {
        const val OWNER_UID = "anonymous-owner"
        const val REPORT_ID_1 = "2b250fac-94df-4f07-971a-feb4e28131b2"
        const val REPORT_ID_2 = "c1d9a971-e810-49fb-a641-62fc7a3daf65"
        const val NOW = 1_800_000_100_000L

        fun report(
            id: String,
            status: SynchronizationStatus = SynchronizationStatus.PENDING,
            createdAt: Long = NOW - 1_000
        ) = PotholeReport(
            id = id,
            createdAtUtcMillis = createdAt,
            detectionAtUtcMillis = createdAt - 1_000,
            latitude = 14.5995,
            longitude = 120.9842,
            horizontalAccuracyMeters = 12f,
            locationFixAtUtcMillis = createdAt - 500,
            locationSource = LocationSource.DEVICE_LOCATION,
            detectionConfidence = 0.87f,
            modelIdentifier = "ROADAR-YOLO26n",
            modelVersion = "Stage 4",
            reportingMode = ReportingMode.MANUAL,
            synchronizationStatus = status,
            syncEligible = true
        )
    }
}
