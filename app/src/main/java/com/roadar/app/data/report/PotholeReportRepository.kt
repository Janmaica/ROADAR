package com.roadar.app.data.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.roadar.app.data.sync.ReportSyncScheduler

interface PotholeReportRepository {
    fun observeReports(): Flow<List<PotholeReport>>
    fun observeSyncState(): Flow<FirebaseSyncState?>
    fun observePendingDeletionCount(): Flow<Int>
    suspend fun getReports(): List<PotholeReport>
    suspend fun getReport(id: String): PotholeReport?
    suspend fun insert(report: PotholeReport)
    suspend fun delete(id: String)
    suspend fun updateSynchronizationStatus(id: String, status: SynchronizationStatus)
    suspend fun retryFailedSynchronization(): Int
    fun requestSynchronization()
}

class RoomPotholeReportRepository(
    private val dao: PotholeReportDao,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val syncScheduler: ReportSyncScheduler = ReportSyncScheduler {},
    private val syncEnabled: () -> Boolean = { false }
) : PotholeReportRepository {
    override fun observeReports(): Flow<List<PotholeReport>> = dao.observeAll().flowOn(ioDispatcher)

    override fun observeSyncState(): Flow<FirebaseSyncState?> =
        dao.observeSyncState().flowOn(ioDispatcher)

    override fun observePendingDeletionCount(): Flow<Int> =
        dao.observePendingDeletionCount().flowOn(ioDispatcher)

    override suspend fun getReports(): List<PotholeReport> =
        withContext(ioDispatcher) { dao.getAll() }

    override suspend fun getReport(id: String): PotholeReport? =
        withContext(ioDispatcher) { dao.getById(id) }

    override suspend fun insert(report: PotholeReport) {
        val syncEligible = syncEnabled()
        withContext(ioDispatcher) {
            dao.insert(report.copy(syncEligible = syncEligible))
        }
        if (syncEligible) syncScheduler.enqueueSync()
    }

    override suspend fun delete(id: String) {
        val deleted = withContext(ioDispatcher) {
            dao.deleteAndQueueRemoteDeletion(id, System.currentTimeMillis())
        }
        if (deleted) syncScheduler.enqueueSync()
    }

    override suspend fun updateSynchronizationStatus(id: String, status: SynchronizationStatus) {
        withContext(ioDispatcher) {
            check(dao.updateSynchronizationStatus(id, status) == 1) {
                "No pothole report exists with id $id."
            }
        }
    }

    override suspend fun retryFailedSynchronization(): Int {
        val retried = withContext(ioDispatcher) { dao.retryFailedReports() }
        if (retried > 0) syncScheduler.enqueueSync()
        return retried
    }

    override fun requestSynchronization() {
        syncScheduler.enqueueSync()
    }
}
