package com.roadar.app.data.report

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query(
        """
        SELECT * FROM pothole_reports
        WHERE syncEligible = 1 AND synchronizationStatus = 'PENDING'
          AND NOT EXISTS (
              SELECT 1 FROM pending_report_deletions
              WHERE pending_report_deletions.reportId = pothole_reports.id
          )
        ORDER BY createdAtUtcMillis ASC
        LIMIT 1
        """
    )
    suspend fun getNextPendingReportForSync(): PotholeReport?

    @Query(
        "UPDATE pothole_reports SET synchronizationStatus = 'SYNCED' " +
            "WHERE id = :id AND syncEligible = 1 AND synchronizationStatus = 'PENDING' " +
            "AND NOT EXISTS (SELECT 1 FROM pending_report_deletions WHERE reportId = :id)"
    )
    suspend fun markSyncedIfStillPendingAndNotDeleted(id: String): Int

    @Query(
        "UPDATE pothole_reports SET synchronizationStatus = 'FAILED' " +
            "WHERE id = :id AND syncEligible = 1 AND synchronizationStatus = 'PENDING'"
    )
    suspend fun markFailedIfPending(id: String): Int

    @Query(
        "UPDATE pothole_reports SET synchronizationStatus = 'PENDING' " +
            "WHERE syncEligible = 1 AND synchronizationStatus = 'FAILED'"
    )
    suspend fun retryFailedReports(): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM pothole_reports " +
            "WHERE syncEligible = 1 AND synchronizationStatus = 'PENDING' " +
            "AND id NOT IN (SELECT reportId FROM pending_report_deletions))"
    )
    suspend fun hasPendingReports(): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM pothole_reports " +
            "WHERE synchronizationStatus = 'FAILED')"
    )
    suspend fun hasFailedReports(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDeletion(deletion: PendingReportDeletion)

    @Query(
        "SELECT * FROM pending_report_deletions " +
            "ORDER BY queuedAtUtcMillis ASC LIMIT 1"
    )
    suspend fun getNextPendingDeletion(): PendingReportDeletion?

    @Query("DELETE FROM pending_report_deletions WHERE reportId = :reportId")
    suspend fun clearPendingDeletion(reportId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM pending_report_deletions)")
    suspend fun hasPendingDeletions(): Boolean

    @Query("SELECT COUNT(*) FROM pending_report_deletions")
    fun observePendingDeletionCount(): Flow<Int>

    @Transaction
    suspend fun deleteAndQueueRemoteDeletion(id: String, queuedAtUtcMillis: Long): Boolean {
        val report = getById(id) ?: return false
        if (report.syncEligible) {
            insertPendingDeletion(PendingReportDeletion(id, queuedAtUtcMillis))
        }
        deleteById(id)
        return true
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncStateIfMissing(state: FirebaseSyncState)

    @Query("SELECT * FROM firebase_sync_state WHERE id = ${FirebaseSyncState.SINGLETON_ID}")
    suspend fun getSyncState(): FirebaseSyncState?

    @Query("SELECT * FROM firebase_sync_state WHERE id = ${FirebaseSyncState.SINGLETON_ID}")
    fun observeSyncState(): Flow<FirebaseSyncState?>

    @Query(
        "UPDATE firebase_sync_state SET ownerUid = :uid " +
            "WHERE id = ${FirebaseSyncState.SINGLETON_ID} AND ownerUid IS NULL"
    )
    suspend fun setOwnerUidIfUnbound(uid: String): Int

    @Query(
        "UPDATE firebase_sync_state SET lastSuccessfulSyncUtcMillis = :timeUtcMillis " +
            "WHERE id = ${FirebaseSyncState.SINGLETON_ID}"
    )
    suspend fun recordSuccessfulSync(timeUtcMillis: Long)

    @Query(
        "UPDATE firebase_sync_state SET lastErrorCode = :errorCode " +
            "WHERE id = ${FirebaseSyncState.SINGLETON_ID}"
    )
    suspend fun recordSyncError(errorCode: String)

    @Query(
        "UPDATE firebase_sync_state SET lastErrorCode = NULL " +
            "WHERE id = ${FirebaseSyncState.SINGLETON_ID}"
    )
    suspend fun clearSyncError()

    @Transaction
    suspend fun getOrCreateSyncState(): FirebaseSyncState {
        insertSyncStateIfMissing(FirebaseSyncState())
        return checkNotNull(getSyncState())
    }

    @Transaction
    suspend fun bindOwnerUid(uid: String): Boolean {
        val state = getOrCreateSyncState()
        if (state.ownerUid != null) return state.ownerUid == uid
        setOwnerUidIfUnbound(uid)
        return getSyncState()?.ownerUid == uid
    }

    @Transaction
    suspend fun markReportSynced(id: String, timeUtcMillis: Long): Boolean {
        val updated = markSyncedIfStillPendingAndNotDeleted(id) == 1
        if (updated) {
            recordSuccessfulSync(timeUtcMillis)
            if (!hasFailedReports()) clearSyncError()
        }
        return updated
    }

    @Transaction
    suspend fun setSyncError(errorCode: String) {
        getOrCreateSyncState()
        recordSyncError(errorCode)
    }

    @Transaction
    suspend fun recordSyncSuccess(timeUtcMillis: Long) {
        getOrCreateSyncState()
        recordSuccessfulSync(timeUtcMillis)
        if (!hasFailedReports()) clearSyncError()
    }

    @Transaction
    suspend fun confirmRemoteDeletion(reportId: String, timeUtcMillis: Long): Boolean {
        val removed = clearPendingDeletion(reportId) == 1
        if (removed) recordSyncSuccess(timeUtcMillis)
        return removed
    }

    @Transaction
    suspend fun clearSyncErrorIfStateExists() {
        getOrCreateSyncState()
        if (!hasFailedReports()) clearSyncError()
    }
}
