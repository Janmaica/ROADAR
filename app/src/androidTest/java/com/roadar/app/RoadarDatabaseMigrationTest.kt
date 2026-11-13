package com.roadar.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadar.app.data.report.MIGRATION_1_2
import com.roadar.app.data.report.MIGRATION_2_3
import com.roadar.app.data.report.RoadarDatabase
import com.roadar.app.data.report.SynchronizationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoadarDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RoadarDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationKeepsExistingReportsLocalAndPreservesEligibleDeletionOutbox() = runBlocking {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO pothole_reports (
                    id, createdAtUtcMillis, detectionAtUtcMillis, latitude, longitude,
                    horizontalAccuracyMeters, locationFixAtUtcMillis, locationSource,
                    detectionConfidence, modelIdentifier, modelVersion, reportingMode,
                    synchronizationStatus, imageReference
                ) VALUES (
                    '$TEST_REPORT_ID', 1800000000000, 1799999999000, 14.5995, 120.9842,
                    12.0, 1799999999500, 'DEVICE_LOCATION', 0.87, 'ROADAR-YOLO26n',
                    'Stage 4', 'MANUAL', 'PENDING', NULL
                )
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            3,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3
        ).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(
            context,
            RoadarDatabase::class.java,
            TEST_DATABASE_NAME
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

        try {
            val dao = database.potholeReportDao()
            val preserved = dao.getById(TEST_REPORT_ID)
            assertEquals(TEST_REPORT_ID, preserved?.id)
            assertEquals(SynchronizationStatus.PENDING, preserved?.synchronizationStatus)
            assertFalse(preserved?.syncEligible ?: true)
            assertNull(dao.getNextPendingReportForSync())
            assertNull(dao.getSyncState()?.ownerUid)

            val syncState = dao.getOrCreateSyncState()
            assertNull(syncState.ownerUid)
            val eligibleReport = checkNotNull(preserved).copy(
                id = TEST_CLOUD_REPORT_ID,
                syncEligible = true
            )
            dao.insert(eligibleReport)
            assertTrue(
                dao.deleteAndQueueRemoteDeletion(TEST_CLOUD_REPORT_ID, 1_800_000_002_000L)
            )
            assertNull(dao.getById(TEST_CLOUD_REPORT_ID))
            assertEquals(TEST_CLOUD_REPORT_ID, dao.getNextPendingDeletion()?.reportId)

            database.close()
            val reopened = Room.databaseBuilder(
                context,
                RoadarDatabase::class.java,
                TEST_DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
            try {
                assertEquals(
                    TEST_CLOUD_REPORT_ID,
                    reopened.potholeReportDao().getNextPendingDeletion()?.reportId
                )
                assertFalse(
                    reopened.potholeReportDao().getById(TEST_REPORT_ID)?.syncEligible ?: true
                )
            } finally {
                reopened.close()
            }
        } finally {
            database.close()
            context.deleteDatabase(TEST_DATABASE_NAME)
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "roadar-reports-migration-test.db"
        const val TEST_REPORT_ID = "2b250fac-94df-4f07-971a-feb4e28131b2"
        const val TEST_CLOUD_REPORT_ID = "c1d9a971-e810-49fb-a641-62fc7a3daf65"
    }
}
