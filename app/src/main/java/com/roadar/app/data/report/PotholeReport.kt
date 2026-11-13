package com.roadar.app.data.report

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

enum class ReportingMode {
    MANUAL,
    AUTOMATIC
}

enum class SynchronizationStatus {
    PENDING,
    SYNCED,
    FAILED
}

enum class LocationSource {
    DEVICE_LOCATION
}

class ReportTypeConverters {
    @TypeConverter
    fun reportingModeToString(value: ReportingMode): String = value.name

    @TypeConverter
    fun stringToReportingMode(value: String): ReportingMode = ReportingMode.valueOf(value)

    @TypeConverter
    fun syncStatusToString(value: SynchronizationStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SynchronizationStatus =
        SynchronizationStatus.valueOf(value)

    @TypeConverter
    fun locationSourceToString(value: LocationSource): String = value.name

    @TypeConverter
    fun stringToLocationSource(value: String): LocationSource = LocationSource.valueOf(value)
}

@Entity(tableName = "pothole_reports")
@TypeConverters(ReportTypeConverters::class)
data class PotholeReport(
    @PrimaryKey val id: String,
    val createdAtUtcMillis: Long,
    val detectionAtUtcMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Float,
    val locationFixAtUtcMillis: Long,
    val locationSource: LocationSource,
    val detectionConfidence: Float,
    val modelIdentifier: String,
    val modelVersion: String,
    val reportingMode: ReportingMode,
    val synchronizationStatus: SynchronizationStatus,
    val imageReference: String? = null,
    @ColumnInfo(defaultValue = "0")
    val syncEligible: Boolean = false
) {
    init {
        UUID.fromString(id)
        require(createdAtUtcMillis > 0) { "Creation time must be a positive UTC epoch value." }
        require(detectionAtUtcMillis in 1..createdAtUtcMillis) {
            "Detection time must be positive and no later than report creation."
        }
        require(locationFixAtUtcMillis in 1..createdAtUtcMillis) {
            "Location-fix time must be positive and no later than report creation."
        }
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be a finite value between -90 and 90."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be a finite value between -180 and 180."
        }
        require(horizontalAccuracyMeters.isFinite() && horizontalAccuracyMeters >= 0f) {
            "Horizontal accuracy must be a finite, non-negative value."
        }
        require(detectionConfidence.isFinite() && detectionConfidence in 0f..1f) {
            "Detection confidence must be between zero and one."
        }
        require(modelIdentifier.isNotBlank() && modelVersion.isNotBlank()) {
            "Model identifier and version are required."
        }
    }
}

@Entity(tableName = "firebase_sync_state")
data class FirebaseSyncState(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val ownerUid: String? = null,
    val lastSuccessfulSyncUtcMillis: Long? = null,
    val lastErrorCode: String? = null
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "pending_report_deletions",
    indices = [Index(value = ["queuedAtUtcMillis"])]
)
data class PendingReportDeletion(
    @PrimaryKey val reportId: String,
    val queuedAtUtcMillis: Long
)
