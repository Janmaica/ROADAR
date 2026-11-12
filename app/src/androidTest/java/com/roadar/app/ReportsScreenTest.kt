package com.roadar.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadar.app.data.report.LocationSource
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.PotholeReportRepository
import com.roadar.app.data.report.ReportingMode
import com.roadar.app.data.report.SynchronizationStatus
import com.roadar.app.ui.screens.ReportsScreen
import com.roadar.app.ui.theme.ROADARTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersStoredReportDetailsFromRepository() {
        val repository = InMemoryReportRepository(testReport())
        composeRule.setContent {
            ROADARTheme {
                ReportsScreen(repository)
            }
        }

        composeRule.onNodeWithText("Pothole reports").assertIsDisplayed()
        composeRule.onNodeWithText("Detection confidence 83.0%").assertIsDisplayed()
        composeRule.onNodeWithTag("report_details_${TEST_REPORT_ID}").performClick()
        composeRule.onNodeWithText("Report details").assertIsDisplayed()
        composeRule.onNodeWithText(TEST_REPORT_ID).assertIsDisplayed()
        composeRule.onNodeWithText("14.599500, 120.984200").assertIsDisplayed()
    }

    @Test
    fun deletionRequiresConfirmationAndUpdatesTheObservedList() {
        val repository = InMemoryReportRepository(testReport())
        composeRule.setContent {
            ROADARTheme {
                ReportsScreen(repository)
            }
        }

        composeRule.onNodeWithTag("report_delete_${TEST_REPORT_ID}").performClick()
        composeRule.onNodeWithText("Delete report?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_report_delete").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.observeReports().first().isEmpty() }
        }
        composeRule.onNodeWithText("No local reports yet").assertIsDisplayed()
    }

    @Test
    fun mapActionReturnsTheSelectedReportId() {
        val repository = InMemoryReportRepository(testReport())
        var selectedReportId: String? = null
        composeRule.setContent {
            ROADARTheme {
                ReportsScreen(repository, onShowOnMap = { selectedReportId = it })
            }
        }

        composeRule.onNodeWithTag("report_map_${TEST_REPORT_ID}").performClick()

        assertEquals(TEST_REPORT_ID, selectedReportId)
    }

    private class InMemoryReportRepository(initial: PotholeReport) : PotholeReportRepository {
        private val reports = MutableStateFlow(listOf(initial))

        override fun observeReports(): Flow<List<PotholeReport>> = reports

        override suspend fun getReports(): List<PotholeReport> = reports.value

        override suspend fun getReport(id: String): PotholeReport? =
            reports.value.firstOrNull { it.id == id }

        override suspend fun insert(report: PotholeReport) {
            reports.value = reports.value + report
        }

        override suspend fun delete(id: String) {
            reports.value = reports.value.filterNot { it.id == id }
        }

        override suspend fun updateSynchronizationStatus(
            id: String,
            status: SynchronizationStatus
        ) {
            reports.value = reports.value.map {
                if (it.id == id) it.copy(synchronizationStatus = status) else it
            }
        }
    }

    private companion object {
        const val TEST_REPORT_ID = "2b250fac-94df-4f07-971a-feb4e28131b2"

        fun testReport() = PotholeReport(
            id = TEST_REPORT_ID,
            createdAtUtcMillis = 1_800_000_000_000L,
            detectionAtUtcMillis = 1_799_999_999_000L,
            latitude = 14.5995,
            longitude = 120.9842,
            horizontalAccuracyMeters = 18f,
            locationFixAtUtcMillis = 1_799_999_999_500L,
            locationSource = LocationSource.DEVICE_LOCATION,
            detectionConfidence = 0.83f,
            modelIdentifier = "ROADAR-YOLO26n",
            modelVersion = "Stage 4",
            reportingMode = ReportingMode.MANUAL,
            synchronizationStatus = SynchronizationStatus.PENDING
        )
    }
}
