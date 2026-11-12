package com.roadar.app.data.report

import com.roadar.app.ai.PotholeDetection
import com.roadar.app.location.DeviceLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class ManualPotholeReportFactoryTest {
    @Test
    fun createsManualReportFromTheConfirmedDetectionAndDeviceFix() {
        val report = ManualPotholeReportFactory.create(
            detection = PotholeDetection(10f, 20f, 80f, 100f, 0.83f),
            detectionAtUtcMillis = DETECTION_TIME,
            location = locationFix(),
            nowUtcMillis = NOW,
            id = REPORT_ID
        )

        assertEquals(REPORT_ID, report.id)
        assertEquals(DETECTION_TIME, report.detectionAtUtcMillis)
        assertEquals(14.5995, report.latitude, 0.0)
        assertEquals(120.9842, report.longitude, 0.0)
        assertEquals(18f, report.horizontalAccuracyMeters)
        assertEquals(0.83f, report.detectionConfidence)
        assertEquals(ReportingMode.MANUAL, report.reportingMode)
        assertEquals(SynchronizationStatus.PENDING, report.synchronizationStatus)
        assertEquals(LocationSource.DEVICE_LOCATION, report.locationSource)
    }

    @Test
    fun rejectsOldOrFutureDetectionInsteadOfSavingIt() {
        val detection = PotholeDetection(10f, 20f, 80f, 100f, 0.83f)

        assertThrows(StalePotholeDetectionException::class.java) {
            ManualPotholeReportFactory.create(
                detection,
                NOW - ManualPotholeReportFactory.MAX_DETECTION_AGE_MILLIS - 1,
                locationFix(),
                NOW
            )
        }
        assertThrows(StalePotholeDetectionException::class.java) {
            ManualPotholeReportFactory.create(
                detection,
                NOW + 1,
                locationFix(),
                NOW
            )
        }
    }

    private fun locationFix() = DeviceLocationFix(
        latitude = 14.5995,
        longitude = 120.9842,
        horizontalAccuracyMeters = 18f,
        fixAtUtcMillis = NOW - 500,
        fixElapsedRealtimeNanos = 100_000_000L
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DETECTION_TIME = NOW - 2_000
        val REPORT_ID: String = UUID.randomUUID().toString()
    }
}
