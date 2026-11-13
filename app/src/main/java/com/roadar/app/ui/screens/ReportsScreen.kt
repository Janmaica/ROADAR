package com.roadar.app.ui.screens

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roadar.app.data.report.PotholeReport
import com.roadar.app.data.report.PotholeReportRepository
import com.roadar.app.data.report.FirebaseSyncState
import com.roadar.app.data.report.SynchronizationStatus
import com.roadar.app.BuildConfig
import com.roadar.app.ui.components.EmptyStateCard
import com.roadar.app.ui.components.PageHeader
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.SectionHeading
import com.roadar.app.ui.components.StatusLine
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val reportTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).withZone(ZoneOffset.UTC)

@Composable
fun ReportsScreen(
    reportRepository: PotholeReportRepository,
    onShowOnMap: (String) -> Unit = {}
) {
    val reportFlow = remember(reportRepository) { reportRepository.observeReports() }
    val reports by reportFlow.collectAsState(initial = null)
    val syncStateFlow = remember(reportRepository) { reportRepository.observeSyncState() }
    val syncState by syncStateFlow.collectAsState(initial = null)
    val pendingDeletionFlow = remember(reportRepository) {
        reportRepository.observePendingDeletionCount()
    }
    val pendingDeletionCount by pendingDeletionFlow.collectAsState(initial = 0)
    val loadedReports = reports
    val scope = rememberCoroutineScope()
    var selectedReport by remember { mutableStateOf<PotholeReport?>(null) }
    var reportPendingDelete by remember { mutableStateOf<PotholeReport?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "Local history",
                title = "Pothole reports",
                description = "Reports are stored on this device. Coordinates represent the device position associated with each detection."
            )
        }
        item {
            CloudSyncCard(
                isConfigured = BuildConfig.FIREBASE_CONFIGURED,
                reports = loadedReports.orEmpty(),
                syncState = syncState,
                pendingDeletionCount = pendingDeletionCount,
                onSyncNow = reportRepository::requestSynchronization,
                onRetryFailed = {
                    scope.launch {
                        try {
                            reportRepository.retryFailedSynchronization()
                            errorMessage = null
                        } catch (exception: SQLiteException) {
                            Log.e(TAG, "Could not retry failed report synchronization.", exception)
                            errorMessage = "Synchronization could not be retried. Please try again."
                        }
                    }
                }
            )
        }
        item {
            SectionHeading(
                title = "Recent reports",
                supportingText = loadedReports?.let { "${it.size} locally stored" } ?: "Loading local records"
            )
        }
        if (loadedReports == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading saved reports…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (loadedReports.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No local reports yet",
                    description = "A report appears here after you confirm a real pothole detection and a valid device location is saved.",
                    marker = "R"
                )
            }
        } else {
            items(loadedReports, key = PotholeReport::id) { report ->
                ReportCard(
                    report = report,
                    onOpen = { selectedReport = report },
                    onDelete = { reportPendingDelete = report },
                    onShowOnMap = { onShowOnMap(report.id) }
                )
            }
        }
        errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    selectedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport = null },
            title = { Text("Report details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DetailLine("Report ID", report.id)
                    DetailLine("Created", formatReportTime(report.createdAtUtcMillis))
                    DetailLine("Detection confidence", formatConfidence(report.detectionConfidence))
                    DetailLine("Device position", formatCoordinates(report.latitude, report.longitude))
                    DetailLine(
                        "Horizontal accuracy",
                        "${formatMeters(report.horizontalAccuracyMeters)} m"
                    )
                    DetailLine("Location fix", formatReportTime(report.locationFixAtUtcMillis))
                    DetailLine("Location source", report.locationSource.name)
                    DetailLine("Model", "${report.modelIdentifier} (${report.modelVersion})")
                    DetailLine("Sync status", report.synchronizationStatus.name)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedReport = null }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    reportPendingDelete = report
                    selectedReport = null
                }) {
                    Text("Delete…")
                }
            }
        )
    }

    reportPendingDelete?.let { report ->
        AlertDialog(
            onDismissRequest = { reportPendingDelete = null },
            title = { Text("Delete report?") },
            text = { Text("This permanently removes report ${report.id} from this device.") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm_report_delete"),
                    onClick = {
                        reportPendingDelete = null
                        scope.launch {
                            try {
                                reportRepository.delete(report.id)
                                errorMessage = null
                            } catch (exception: SQLiteException) {
                                Log.e(TAG, "Could not delete report ${report.id}.", exception)
                                errorMessage = "The report could not be deleted. Please try again."
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CloudSyncCard(
    isConfigured: Boolean,
    reports: List<PotholeReport>,
    syncState: FirebaseSyncState?,
    pendingDeletionCount: Int,
    onSyncNow: () -> Unit,
    onRetryFailed: () -> Unit
) {
    val pendingReports = reports.count {
        it.synchronizationStatus == SynchronizationStatus.PENDING
    }
    val failedReports = reports.count {
        it.synchronizationStatus == SynchronizationStatus.FAILED
    }
    val synchronizedReports = reports.count {
        it.synchronizationStatus == SynchronizationStatus.SYNCED
    }
    val statusText = when {
        !isConfigured ->
            "Firebase is not configured. Reports remain saved locally on this device."
        syncState?.lastErrorCode == "ACCOUNT_IDENTITY_MISMATCH" ->
            "Sync is paused because the saved anonymous identity changed. ROADAR will not upload reports under a different account."
        syncState?.lastErrorCode != null ->
            syncErrorMessage(syncState.lastErrorCode)
        pendingReports > 0 || pendingDeletionCount > 0 ->
            "Waiting for network access and server acknowledgment."
        syncState?.lastSuccessfulSyncUtcMillis != null ->
            "Last server acknowledgment: ${formatReportTime(syncState.lastSuccessfulSyncUtcMillis)}"
        else ->
            "No server synchronization has completed on this installation."
    }

    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cloud synchronization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (syncState?.lastErrorCode != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            StatusLine(title = "Pending uploads", status = pendingReports.toString())
            StatusLine(title = "Synchronized", status = synchronizedReports.toString())
            StatusLine(title = "Failed uploads", status = failedReports.toString())
            StatusLine(title = "Pending deletions", status = pendingDeletionCount.toString())
            if (isConfigured) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSyncNow) {
                        Text("Sync now")
                    }
                    if (failedReports > 0) {
                        OutlinedButton(onClick = onRetryFailed) {
                            Text("Retry failed")
                        }
                    }
                }
            }
        }
    }
}

private fun syncErrorMessage(errorCode: String): String = when (errorCode) {
    "FIRESTORE_PERMISSION_DENIED" ->
        "Firestore denied the operation. Check Authentication and published Firestore rules."
    "ANONYMOUS_AUTHENTICATION_FAILED" ->
        "Anonymous Authentication is unavailable. Check the Firebase project configuration."
    "FIREBASE_INITIALIZATION_FAILED" ->
        "Firebase could not initialize. Local reports remain available."
    "FIRESTORE_REJECTED_REPORT" ->
        "Firestore rejected a report. Review the report schema and published rules."
    else ->
        "Synchronization needs attention ($errorCode). Local reports remain available."
}

@Composable
private fun ReportCard(
    report: PotholeReport,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShowOnMap: () -> Unit
) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = formatReportTime(report.createdAtUtcMillis),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = report.synchronizationStatus.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "ID ${report.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Detection confidence ${formatConfidence(report.detectionConfidence)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Device position ${formatCoordinates(report.latitude, report.longitude)} " +
                    "(±${formatMeters(report.horizontalAccuracyMeters)} m)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("report_details_${report.id}")
                ) {
                    Text("Details")
                }
                TextButton(
                    onClick = onShowOnMap,
                    modifier = Modifier.testTag("report_map_${report.id}")
                ) {
                    Text("Map")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("report_delete_${report.id}")
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun formatReportTime(timeUtcMillis: Long): String =
    reportTimeFormatter.format(Instant.ofEpochMilli(timeUtcMillis))

private fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

private fun formatConfidence(confidence: Float): String =
    String.format(Locale.US, "%.1f%%", confidence * 100f)

private fun formatMeters(meters: Float): String = String.format(Locale.US, "%.0f", meters)

private const val TAG = "ROADAR-Reports"
