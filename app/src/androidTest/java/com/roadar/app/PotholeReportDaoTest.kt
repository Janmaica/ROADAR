package com.roadar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadar.app.data.report.LocationSource
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.ReportingMode
import com.roadar.app.data.report.RoadarDatabase
import com.roadar.app.data.report.SynchronizationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PotholeReportDaoTest {
    private lateinit var context: Context
    private lateinit var database: RoadarDatabase
    private val databaseName = "roadar-reports-test-${UUID.randomUUID()}.db"

    @Before
    fun createDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = newDatabase()
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun reportRoundTripsAndObserversReceiveInsertDeleteAndSyncUpdates() = runBlocking {
        val dao = database.potholeReportDao()
        val report = sampleReport()

        assertEquals(emptyList<PotholeReport>(), dao.observeAll().first())
        dao.insert(report)
        assertEquals(report, dao.getById(report.id))
        assertEquals(listOf(report), dao.observeAll().first())

        assertEquals(1, dao.updateSynchronizationStatus(report.id, SynchronizationStatus.FAILED))
        assertEquals(SynchronizationStatus.FAILED, dao.getById(report.id)?.synchronizationStatus)

        dao.deleteById(report.id)
        assertNull(dao.getById(report.id))
        assertEquals(emptyList<PotholeReport>(), dao.observeAll().first())
    }

    @Test
    fun storesUtcTimestampsAndEnumValuesWithoutChangingThem() = runBlocking {
        val report = sampleReport()
        database.potholeReportDao().insert(report)

        val stored = database.potholeReportDao().getById(report.id)
        assertNotNull(stored)
        assertEquals(report.createdAtUtcMillis, stored?.createdAtUtcMillis)
        assertEquals(report.detectionAtUtcMillis, stored?.detectionAtUtcMillis)
        assertEquals(report.locationFixAtUtcMillis, stored?.locationFixAtUtcMillis)
        assertEquals(ReportingMode.MANUAL, stored?.reportingMode)
        assertEquals(LocationSource.DEVICE_LOCATION, stored?.locationSource)
    }

    @Test
    fun reportPersistsAfterDatabaseIsClosedAndReopened() = runBlocking {
        val report = sampleReport()
        database.potholeReportDao().insert(report)
        database.close()

        database = newDatabase()

        assertEquals(report, database.potholeReportDao().getById(report.id))
    }

    private fun newDatabase() =
        Room.databaseBuilder(context, RoadarDatabase::class.java, databaseName).build()

    private fun sampleReport() = PotholeReport(
        id = UUID.randomUUID().toString(),
        createdAtUtcMillis = 1_800_000_000_000L,
        detectionAtUtcMillis = 1_799_999_999_000L,
        latitude = 14.5995,
        longitude = 120.9842,
        horizontalAccuracyMeters = 12f,
        locationFixAtUtcMillis = 1_799_999_999_500L,
        locationSource = LocationSource.DEVICE_LOCATION,
        detectionConfidence = 0.87f,
        modelIdentifier = "ROADAR-YOLO26n",
        modelVersion = "Stage 4",
        reportingMode = ReportingMode.MANUAL,
        synchronizationStatus = SynchronizationStatus.PENDING
    )
}
