package com.roadar.app.data.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

interface PotholeReportRepository {
    fun observeReports(): Flow<List<PotholeReport>>
    suspend fun getReports(): List<PotholeReport>
    suspend fun getReport(id: String): PotholeReport?
    suspend fun insert(report: PotholeReport)
    suspend fun delete(id: String)
    suspend fun updateSynchronizationStatus(id: String, status: SynchronizationStatus)
}

class RoomPotholeReportRepository(
    private val dao: PotholeReportDao,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : PotholeReportRepository {
    override fun observeReports(): Flow<List<PotholeReport>> = dao.observeAll().flowOn(ioDispatcher)

    override suspend fun getReports(): List<PotholeReport> =
        withContext(ioDispatcher) { dao.getAll() }

    override suspend fun getReport(id: String): PotholeReport? =
        withContext(ioDispatcher) { dao.getById(id) }

    override suspend fun insert(report: PotholeReport) {
        withContext(ioDispatcher) { dao.insert(report) }
    }

    override suspend fun delete(id: String) {
        withContext(ioDispatcher) { dao.deleteById(id) }
    }

    override suspend fun updateSynchronizationStatus(id: String, status: SynchronizationStatus) {
        withContext(ioDispatcher) {
            check(dao.updateSynchronizationStatus(id, status) == 1) {
                "No pothole report exists with id $id."
            }
        }
    }
}
