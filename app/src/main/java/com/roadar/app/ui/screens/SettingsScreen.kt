package com.roadar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.roadar.app.BuildConfig
import com.roadar.app.data.sync.FirebaseSyncPreferences
import com.roadar.app.data.sync.WorkManagerReportSyncScheduler
import com.roadar.app.ui.components.PageHeader
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.SectionHeading
import com.roadar.app.ui.components.StatusLine

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val syncScheduler = remember(context) { WorkManagerReportSyncScheduler(context) }
    var syncEnabled by remember(context) {
        mutableStateOf(FirebaseSyncPreferences.isEnabled(context))
    }
    var confirmEnable by remember { mutableStateOf(false) }
    var syncSettingError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            PageHeader(
                eyebrow = "Application",
                title = "Settings",
                description = "Project and capability information for this research prototype."
            )
        }
        item {
            RoadarCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Firebase Sync",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = when {
                                !BuildConfig.FIREBASE_CONFIGURED ->
                                    "Add local Firebase configuration to enable sync."
                                syncEnabled ->
                                    "Only reports created after enabling sync. Older reports stay local."
                                else ->
                                    "Off. Existing reports remain on this device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncEnabled,
                        enabled = BuildConfig.FIREBASE_CONFIGURED || syncEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                confirmEnable = true
                            } else {
                                try {
                                    FirebaseSyncPreferences.setEnabled(context, false)
                                    syncEnabled = false
                                    syncScheduler.cancelSync()
                                    syncSettingError = null
                                } catch (_: IllegalStateException) {
                                    syncSettingError =
                                        "The sync setting could not be saved or paused."
                                }
                            }
                        },
                        modifier = Modifier.testTag("firebase_sync_switch")
                    )
                }
            }
            syncSettingError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        item {
            InformationSection(
                title = "Application",
                rows = listOf(
                    "Name" to "ROADAR",
                    "Version" to "1.0",
                    "Build" to "Research prototype"
                )
            )
        }
        item {
            InformationSection(
                title = "Detection model",
                rows = listOf(
                    "Model" to "Fine-tuned YOLO26n · Stage 4",
                    "Runtime" to "ONNX Runtime · CPU",
                    "Model file" to "Local ignored asset required"
                )
            )
        }
        item {
            InformationSection(
                title = "Reports and location",
                rows = listOf(
                    "Storage" to "Room · on device",
                    "Reporting" to "Manual confirmation required",
                    "Location" to "Foreground approximate or precise fix",
                    "Coordinates" to "Device position associated with detection"
                )
            )
        }
        item {
            InformationSection(
                title = "Mapping and synchronization",
                rows = listOf(
                    "Interactive map" to "MapLibre · MapTiler Streets online",
                    "Offline map tiles" to "Unavailable · basemap requires internet",
                    "Cloud synchronization" to if (BuildConfig.FIREBASE_CONFIGURED) {
                        if (syncEnabled) "On · new reports only" else "Off"
                    } else {
                        "Not configured · local reports only"
                    },
                    "Network status" to "Device connectivity only"
                )
            )
        }
        item {
            Text(
                text = "Reports are stored locally in Room and remain available offline. Saving requires a real detection and a fresh valid device location. Map tiles require internet. When enabled, Firebase Sync sends only eligible reports created after opt-in and queues deletions for eligible cloud reports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmEnable) {
        AlertDialog(
            onDismissRequest = { confirmEnable = false },
            title = { Text("Enable Firebase Sync?") },
            text = {
                Text(
                    "Only reports created after you enable sync may be uploaded. " +
                        "Existing reports will remain local. New eligible report details " +
                        "and later deletions will sync to the configured Firebase project."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            FirebaseSyncPreferences.setEnabled(context, true)
                            syncEnabled = true
                            syncSettingError = null
                            syncScheduler.enqueueSync()
                        } catch (_: IllegalStateException) {
                            syncSettingError = "The sync setting could not be saved."
                        } finally {
                            confirmEnable = false
                        }
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnable = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InformationSection(
    title: String,
    rows: List<Pair<String, String>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading(title)
        RoadarCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                rows.forEach { (label, value) ->
                    StatusLine(title = label, status = value)
                }
            }
        }
    }
}
