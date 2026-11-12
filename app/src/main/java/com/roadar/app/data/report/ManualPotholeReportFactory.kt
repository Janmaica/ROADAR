package com.roadar.app.data.report

import com.roadar.app.ai.PotholeDetection
import com.roadar.app.location.DeviceLocationFix
import java.util.UUID

class StalePotholeDetectionException :
    IllegalArgumentException("The selected detection is no longer current.")

object ManualPotholeReportFactory {
    const val MAX_DETECTION_AGE_MILLIS = 15_000L
    const val MODEL_IDENTIFIER = "ROADAR-YOLO26n"
    const val MODEL_VERSION = "Stage 4"

    fun create(
        detection: PotholeDetection,
        detectionAtUtcMillis: Long,
        location: DeviceLocationFix,
        nowUtcMillis: Long,
        id: String = UUID.randomUUID().toString()
    ): PotholeReport {
        val ageMillis = nowUtcMillis - detectionAtUtcMillis
        if (detectionAtUtcMillis <= 0L || ageMillis !in 0..MAX_DETECTION_AGE_MILLIS) {
            throw StalePotholeDetectionException()
        }

        val createdAtUtcMillis = maxOf(nowUtcMillis, location.fixAtUtcMillis)
        return PotholeReport(
            id = id,
            createdAtUtcMillis = createdAtUtcMillis,
            detectionAtUtcMillis = detectionAtUtcMillis,
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters = location.horizontalAccuracyMeters,
            locationFixAtUtcMillis = location.fixAtUtcMillis,
            locationSource = LocationSource.DEVICE_LOCATION,
            detectionConfidence = detection.confidence,
            modelIdentifier = MODEL_IDENTIFIER,
            modelVersion = MODEL_VERSION,
            reportingMode = ReportingMode.MANUAL,
            synchronizationStatus = SynchronizationStatus.PENDING
        )
    }
}
