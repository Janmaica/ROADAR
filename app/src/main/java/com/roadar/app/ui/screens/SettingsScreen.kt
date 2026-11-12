package com.roadar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roadar.app.ui.components.PageHeader
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.SectionHeading
import com.roadar.app.ui.components.StatusLine

@Composable
fun SettingsScreen() {
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
                    "Report synchronization" to "Blocked · no authorized backend configured",
                    "Network status" to "Device connectivity only"
                )
            )
        }
        item {
            Text(
                text = "Reports are stored locally in Room and remain available offline. Saving requires a real detection and a fresh valid device location. Map tiles require internet. Synchronization is not implemented because no authorized server or upload API has been provided.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
