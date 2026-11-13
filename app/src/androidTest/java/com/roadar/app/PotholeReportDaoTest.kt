package com.roadar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadar.app.data.report.LocationSource
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.ReportingMode
import com.roadar.app.data.report.RoadarDatabase
import com.roadar.app.data.report.RoomPotholeReportRepository
import com.roadar.app.data.report.SynchronizationStatus
import com.roadar.app.data.sync.ReportSyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun newReportsAreEligibleOnlyWhenSyncWasEnabledAtInsertion() = runBlocking {
        var disabledEnqueues = 0
        val disabledRepository = RoomPotholeReportRepository(
            database.potholeReportDao(),
            syncScheduler = ReportSyncScheduler { disabledEnqueues += 1 },
            syncEnabled = { false }
        )
        val localReport = sampleReport()
        disabledRepository.insert(localReport)

        assertFalse(database.potholeReportDao().getById(localReport.id)?.syncEligible ?: true)
        assertEquals(0, disabledEnqueues)

        var enabledEnqueues = 0
        val enabledRepository = RoomPotholeReportRepository(
            database.potholeReportDao(),
            syncScheduler = ReportSyncScheduler { enabledEnqueues += 1 },
            syncEnabled = { true }
        )
        val eligibleReport = sampleReport().copy(id = UUID.randomUUID().toString())
        enabledRepository.insert(eligibleReport)

        assertTrue(database.potholeReportDao().getById(eligibleReport.id)?.syncEligible ?: false)
        assertEquals(1, enabledEnqueues)
    }

    @Test
    fun localOnlyDeletionDoesNotQueueRemoteWorkButEligibleDeletionPersistsTombstone() =
        runBlocking {
            val dao = database.potholeReportDao()
            val localReport = sampleReport()
            dao.insert(localReport)
            assertTrue(dao.deleteAndQueueRemoteDeletion(localReport.id, 1_800_000_001_000L))
            assertNull(dao.getNextPendingDeletion())

            val eligibleReport = sampleReport().copy(
                id = UUID.randomUUID().toString(),
                syncEligible = true
            )
            dao.insert(eligibleReport)
            assertTrue(
                dao.deleteAndQueueRemoteDeletion(eligibleReport.id, 1_800_000_002_000L)
            )
            assertNull(dao.getById(eligibleReport.id))
            assertEquals(eligibleReport.id, dao.getNextPendingDeletion()?.reportId)

            database.close()
            database = newDatabase()

            assertEquals(eligibleReport.id, database.potholeReportDao().getNextPendingDeletion()?.reportId)
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
