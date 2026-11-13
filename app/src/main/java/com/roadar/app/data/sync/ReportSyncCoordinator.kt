package com.roadar.app.data.sync

import com.roadar.app.data.report.FirebaseSyncState
import com.roadar.app.data.report.PendingReportDeletion
import com.roadar.app.data.report.PotholeReport

fun interface AnonymousIdentityProvider {
    suspend fun getUid(existingOwnerUid: String?): String
}

interface RemoteReportStore {
    suspend fun upsertReport(uid: String, report: PotholeReport)
    suspend fun deleteReport(uid: String, reportId: String)
}

interface ReportSyncLocalStore {
    suspend fun getOrCreateSyncState(): FirebaseSyncState
    suspend fun bindOwnerUid(uid: String): Boolean
    suspend fun recordError(errorCode: String)
    suspend fun clearError()
    suspend fun nextPendingDeletion(): PendingReportDeletion?
    suspend fun confirmRemoteDeletion(reportId: String, timeUtcMillis: Long)
    suspend fun nextPendingReport(): PotholeReport?
    suspend fun markReportSynced(reportId: String, timeUtcMillis: Long): Boolean
    suspend fun markReportFailed(reportId: String)
    suspend fun hasPendingWork(): Boolean
}

enum class SyncRunResult {
    COMPLETE,
    MORE_WORK,
    BLOCKED,
    DISABLED
}

class RetryableReportSyncException(
    val errorCode: String,
    cause: Throwable? = null
) : Exception("A transient report synchronization error occurred.", cause)

class PermanentReportSyncException(
    val errorCode: String,
    val reportId: String? = null,
    cause: Throwable? = null
) : Exception("A report synchronization operation needs attention.", cause)

class ReportSyncBlockedException(
    val errorCode: String
) : Exception("Report synchronization is blocked until account state is resolved.")

class ReportSyncCoordinator(
    private val localStore: ReportSyncLocalStore,
    private val identityProvider: AnonymousIdentityProvider,
    private val remoteStore: RemoteReportStore,
    private val isSyncEnabled: () -> Boolean = { true },
    private val nowUtcMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun synchronize(maxOperations: Int = DEFAULT_MAX_OPERATIONS): SyncRunResult {
        require(maxOperations > 0) { "The synchronization batch must be non-empty." }
        if (!isSyncEnabled()) return SyncRunResult.DISABLED

        val state = localStore.getOrCreateSyncState()
        if (!isSyncEnabled()) return SyncRunResult.DISABLED
        val uid = try {
            identityProvider.getUid(state.ownerUid)
        } catch (exception: ReportSyncBlockedException) {
            localStore.recordError(exception.errorCode)
            return SyncRunResult.BLOCKED
        } catch (exception: PermanentReportSyncException) {
            localStore.recordError(exception.errorCode)
            return SyncRunResult.BLOCKED
        } catch (exception: RetryableReportSyncException) {
            localStore.recordError(exception.errorCode)
            throw exception
        }
        if (!isSyncEnabled()) return SyncRunResult.DISABLED

        if (!localStore.bindOwnerUid(uid)) {
            localStore.recordError(SYNC_ERROR_ACCOUNT_MISMATCH)
            return SyncRunResult.BLOCKED
        }
        localStore.clearError()

        var operations = 0
        while (operations < maxOperations) {
            if (!isSyncEnabled()) return SyncRunResult.DISABLED
            val deletion = localStore.nextPendingDeletion()
            if (deletion != null) {
                if (!isSyncEnabled()) return SyncRunResult.DISABLED
                try {
                    remoteStore.deleteReport(uid, deletion.reportId)
                } catch (exception: PermanentReportSyncException) {
                    localStore.recordError(exception.errorCode)
                    return SyncRunResult.BLOCKED
                } catch (exception: RetryableReportSyncException) {
                    localStore.recordError(exception.errorCode)
                    throw exception
                }
                localStore.confirmRemoteDeletion(deletion.reportId, nowUtcMillis())
                operations += 1
                continue
            }

            val report = localStore.nextPendingReport() ?: return SyncRunResult.COMPLETE
            if (!isSyncEnabled()) return SyncRunResult.DISABLED
            try {
                remoteStore.upsertReport(uid, report)
            } catch (exception: PermanentReportSyncException) {
                val rejectedReportId = exception.reportId
                if (rejectedReportId != null) {
                    localStore.markReportFailed(rejectedReportId)
                }
                localStore.recordError(exception.errorCode)
                if (exception.reportId == null) return SyncRunResult.BLOCKED
                operations += 1
                continue
            } catch (exception: RetryableReportSyncException) {
                localStore.recordError(exception.errorCode)
                throw exception
            }

            localStore.markReportSynced(report.id, nowUtcMillis())
            operations += 1
        }

        return if (localStore.hasPendingWork()) {
            SyncRunResult.MORE_WORK
        } else {
            SyncRunResult.COMPLETE
        }
    }

    companion object {
        const val DEFAULT_MAX_OPERATIONS = 20
        const val SYNC_ERROR_ACCOUNT_MISMATCH = "ACCOUNT_IDENTITY_MISMATCH"
    }
}
