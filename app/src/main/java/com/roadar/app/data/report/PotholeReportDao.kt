package com.roadar.app.data.report

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PotholeReportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: PotholeReport)

    @Query("SELECT * FROM pothole_reports ORDER BY createdAtUtcMillis DESC")
    fun observeAll(): Flow<List<PotholeReport>>

    @Query("SELECT * FROM pothole_reports ORDER BY createdAtUtcMillis DESC")
    suspend fun getAll(): List<PotholeReport>

    @Query("SELECT * FROM pothole_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PotholeReport?

    @Query("DELETE FROM pothole_reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE pothole_reports SET synchronizationStatus = :status WHERE id = :id")
    suspend fun updateSynchronizationStatus(id: String, status: SynchronizationStatus): Int
}
