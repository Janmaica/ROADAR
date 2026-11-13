package com.roadar.app.data.sync

import com.roadar.app.data.report.FirebaseSyncState
import com.roadar.app.data.report.PendingReportDeletion
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.PotholeReportDao

class RoomReportSyncLocalStore(
    private val dao: PotholeReportDao
) : ReportSyncLocalStore {
    override suspend fun getOrCreateSyncState(): FirebaseSyncState =
        dao.getOrCreateSyncState()

    override suspend fun bindOwnerUid(uid: String): Boolean =
        dao.bindOwnerUid(uid)

    override suspend fun recordError(errorCode: String) {
        dao.setSyncError(errorCode)
    }

    override suspend fun clearError() {
        dao.clearSyncErrorIfStateExists()
    }

    override suspend fun nextPendingDeletion(): PendingReportDeletion? =
        dao.getNextPendingDeletion()

    override suspend fun confirmRemoteDeletion(
        reportId: String,
        timeUtcMillis: Long
    ) {
        dao.confirmRemoteDeletion(reportId, timeUtcMillis)
    }

    override suspend fun nextPendingReport(): PotholeReport? =
        dao.getNextPendingReportForSync()

    override suspend fun markReportSynced(
        reportId: String,
        timeUtcMillis: Long
    ): Boolean = dao.markReportSynced(reportId, timeUtcMillis)

    override suspend fun markReportFailed(reportId: String) {
        dao.markFailedIfPending(reportId)
    }

    override suspend fun hasPendingWork(): Boolean =
        dao.hasPendingDeletions() || dao.hasPendingReports()
}
