package com.roadar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roadar.app.ai.YOLO26_MODEL_ASSET
import com.roadar.app.data.report.PotholeReportRepository
import com.roadar.app.ui.components.EmptyStateCard
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.RoadarLogo
import com.roadar.app.ui.components.SectionHeading
import com.roadar.app.ui.components.StatusLine

@Composable
fun HomeScreen(
    onStartDetection: () -> Unit,
    onViewReports: () -> Unit,
    onOpenMap: () -> Unit,
    reportRepository: PotholeReportRepository
) {
    val context = LocalContext.current
    val reportFlow = remember(reportRepository) { reportRepository.observeReports() }
    val reports by reportFlow.collectAsState(initial = null)
    val hasModelAsset = remember(context) {
        context.assets.list("")?.contains(YOLO26_MODEL_ASSET) == true
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            DetectionIntroCard(
                hasModelAsset = hasModelAsset,
                onStartDetection = onStartDetection,
                onViewReports = onViewReports,
                onOpenMap = onOpenMap
            )
        }
        item {
            SectionHeading(
                title = "Project status",
                supportingText = "Prototype capabilities, not live detection results"
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        title = "Local reports",
                        value = reports?.let { "${it.size} saved" } ?: "Loading",
                        detail = "Stored on this device",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "Detection",
                        value = if (hasModelAsset) "Model available" else "Model required",
                        detail = if (hasModelAsset) {
                            "Offline inference is enabled"
                        } else {
                            "Add the local ONNX model asset"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                RoadarCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusLine(title = "Application", status = "Research prototype")
                        StatusLine(
                            title = "Camera / model",
                            status = if (hasModelAsset) "Inference available" else "Model asset missing"
                        )
                        StatusLine(
                            title = "Report storage",
                            status = reports?.let { "${it.size} saved locally" } ?: "Loading"
                        )
                    }
                }
            }
        }
        item {
            if (reports?.isEmpty() == true) {
                EmptyStateCard(
                    title = "No reports recorded",
                    description = "Confirm a real model detection and a recent device location to save a report."
                )
            }
        }
    }
}

@Composable
private fun DetectionIntroCard(
    hasModelAsset: Boolean,
    onStartDetection: () -> Unit,
    onViewReports: () -> Unit,
    onOpenMap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RoadarLogo(modifier = Modifier.size(56.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "ROAD CONDITION RESEARCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "YOLO26n · On-device AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                text = "Smart pothole detection",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (hasModelAsset) {
                    "Run inference on your device, then choose which detections to save with a verified location."
                } else {
                    "Camera preview is available. Add the separately supplied ONNX asset to enable pothole detection."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartDetection,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Text("Start detection", fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onViewReports,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Text("View reports")
                }
                Button(
                    onClick = onOpenMap,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Text("Open map")
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    RoadarCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
