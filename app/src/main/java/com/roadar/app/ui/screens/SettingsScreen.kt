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
                    "Planned model" to "Fine-tuned YOLO26n",
                    "Runtime status" to "Not integrated",
                    "Model file" to "Not included"
                )
            )
        }
        item {
            InformationSection(
                title = "Offline and synchronization",
                rows = listOf(
                    "Local reports" to "Not implemented",
                    "Background sync" to "Not configured",
                    "Network status" to "Device connectivity only"
                )
            )
        }
        item {
            Text(
                text = "The interface is an early development milestone. Detection, GPS, local data storage, mapping, and synchronization require future implementation and validation.",
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
