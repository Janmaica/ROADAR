package com.roadar.app.ui.screens

import android.Manifest
import android.database.sqlite.SQLiteException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.ComponentActivity
import com.roadar.app.ai.DetectionFrame
import com.roadar.app.ai.DetectionSession
import com.roadar.app.ai.DetectionTrackingUiState
import com.roadar.app.ai.DetectionUiState
import com.roadar.app.ai.DetectorStatus
import com.roadar.app.ai.PotholeDetection
import com.roadar.app.ai.PotholeDetectionTracker
import com.roadar.app.data.report.ManualPotholeReportFactory
import com.roadar.app.data.report.PotholeReportRepository
import com.roadar.app.data.report.ReportingMode
import com.roadar.app.data.report.StalePotholeDetectionException
import com.roadar.app.location.DeviceLocationProvider
import com.roadar.app.location.LocationFixException
import com.roadar.app.ui.camera.CameraPreviewController
import com.roadar.app.ui.camera.CameraPreviewSurface
import com.roadar.app.ui.camera.DetectionOverlay
import com.roadar.app.ui.components.PageHeader
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.SectionHeading
import com.roadar.app.ui.components.StatusLine
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val CAMERA_PERMISSION_PREFERENCES = "roadar_camera_permission"
private const val CAMERA_PERMISSION_REQUESTED = "requested"

private enum class CameraPermissionState {
    NOT_REQUESTED,
    DENIED,
    PERMANENTLY_DENIED,
    GRANTED,
    REQUESTING
}

private data class ConfirmedDetection(
    val detection: PotholeDetection,
    val detectionAtUtcMillis: Long
)

private const val TAG = "ROADAR-ManualReport"

@Composable
fun DetectionScreen(
    reportRepository: PotholeReportRepository,
    locationProvider: DeviceLocationProvider
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val preferences = remember(context) {
        context.getSharedPreferences(CAMERA_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)
    }
    var permissionState by remember(context, activity) {
        mutableStateOf(resolveCameraPermissionState(context, activity, preferences))
    }
    var isPreviewStreaming by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var detectionState by remember { mutableStateOf(DetectionUiState()) }
    var pendingDetection by remember { mutableStateOf<ConfirmedDetection?>(null) }
    var isSavingReport by remember { mutableStateOf(false) }
    var reportFeedback by remember { mutableStateOf<String?>(null) }
    var showLocationSettingsAction by remember { mutableStateOf(false) }
    var reportingMode by remember { mutableStateOf(ReportingMode.MANUAL) }
    var automaticTrackingEnabled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val appSettingsIntent = remember(context) {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }
    val cameraController = remember(context) { CameraPreviewController(context) }
    val detectionSession = remember(context) {
        DetectionSession(context) { state -> detectionState = state }
    }
    LaunchedEffect(automaticTrackingEnabled) {
        detectionSession.setAutomaticTrackingEnabled(automaticTrackingEnabled)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = when {
            granted -> CameraPermissionState.GRANTED
            activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.CAMERA
                )
            } == true -> CameraPermissionState.DENIED

            else -> CameraPermissionState.PERMANENTLY_DENIED
        }
    }

    val saveConfirmedReport: () -> Unit = {
        val confirmed = pendingDetection
        if (confirmed != null && !isSavingReport) {
            isSavingReport = true
            coroutineScope.launch {
                reportFeedback = null
                showLocationSettingsAction = false
                try {
                    val fix = locationProvider.getRecentLocation()
                    val report = ManualPotholeReportFactory.create(
                        detection = confirmed.detection,
                        detectionAtUtcMillis = confirmed.detectionAtUtcMillis,
                        location = fix,
                        nowUtcMillis = System.currentTimeMillis()
                    )
                    reportRepository.insert(report)
                    reportFeedback =
                        "Report saved locally with the device position associated with this detection."
                    pendingDetection = null
                } catch (exception: StalePotholeDetectionException) {
                    reportFeedback =
                        "That detection is no longer current. Wait for a new model result and retry."
                    showLocationSettingsAction = false
                } catch (exception: LocationFixException) {
                    reportFeedback = exception.message
                        ?: "A recent device location is unavailable. Retry when ready."
                    showLocationSettingsAction = true
                } catch (exception: SQLiteException) {
                    Log.e(TAG, "Room could not save the confirmed pothole report.", exception)
                    reportFeedback = "The report could not be saved to this device. Please retry."
                } catch (exception: IllegalArgumentException) {
                    Log.e(TAG, "The confirmed report failed validation.", exception)
                    reportFeedback = "The report data was invalid and was not saved."
                } finally {
                    isSavingReport = false
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResults ->
        if (permissionResults.values.any { it }) {
            saveConfirmedReport()
        } else {
            reportFeedback = "Location permission was denied. Allow approximate or precise location to save a report."
            showLocationSettingsAction = true
        }
    }

    DisposableEffect(activity, context) {
        detectionSession.start()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = resolveCameraPermissionState(context, activity, preferences)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            detectionSession.setAutomaticTrackingEnabled(false)
            activity?.lifecycle?.removeObserver(observer)
            cameraController.unbind()
            detectionSession.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageHeader(
            eyebrow = "Detection workspace",
            title = "Live camera preview",
            description = "Pothole inference runs locally. Manually confirmed detections can be saved with a recent device location."
        )

        SectionHeading(
            title = "Camera preview",
            supportingText = when {
                permissionState != CameraPermissionState.GRANTED -> "Camera access is required to show the preview"
                cameraError != null -> "Camera could not be started"
                isPreviewStreaming -> "Rear camera is active"
                else -> "Connecting to the rear camera"
            }
        )

        when {
            permissionState == CameraPermissionState.GRANTED &&
                cameraError == null &&
                activity != null -> {
                CameraPreviewCard(
                    lifecycleOwner = activity,
                    controller = cameraController,
                    analyzer = detectionSession,
                    analyzerExecutor = detectionSession.analyzerExecutor,
                    isStreaming = isPreviewStreaming,
                    detectionFrame = detectionState.latestFrame,
                    onStreamingChanged = {
                        isPreviewStreaming = it
                        if (it) cameraError = null
                    },
                    onError = {
                        cameraError = it
                        isPreviewStreaming = false
                    }
                )
            }

            permissionState == CameraPermissionState.GRANTED -> {
                CameraMessageCard(
                    title = "Camera unavailable",
                    message = cameraError
                        ?: "The camera preview could not attach to the app lifecycle.",
                    actionLabel = "Try again",
                    onAction = {
                        cameraError = null
                        isPreviewStreaming = false
                    }
                )
            }

            permissionState == CameraPermissionState.PERMANENTLY_DENIED -> {
                CameraMessageCard(
                    title = "Camera permission is disabled",
                    message = "Allow camera access in app settings to use the live preview.",
                    actionLabel = "Open app settings",
                    onAction = {
                        val settingsIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(settingsIntent)
                    }
                )
            }

            permissionState == CameraPermissionState.REQUESTING -> {
                CameraMessageCard(
                    title = "Waiting for camera permission",
                    message = "Choose whether ROADAR can access the camera in the Android permission prompt."
                )
            }

            else -> {
                CameraMessageCard(
                    title = if (permissionState == CameraPermissionState.DENIED) {
                        "Camera permission needed"
                    } else {
                        "Enable camera access"
                    },
                    message = if (permissionState == CameraPermissionState.DENIED) {
                        "Camera access was declined. You can try again whenever you are ready."
                    } else {
                        "ROADAR uses the rear camera for local pothole detection. Reports are saved only after confirmation and a fresh location fix."
                    },
                    actionLabel = "Enable camera",
                    onAction = {
                        preferences.edit()
                            .putBoolean(CAMERA_PERMISSION_REQUESTED, true)
                            .apply()
                        permissionState = CameraPermissionState.REQUESTING
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }

        if (detectionState.status == DetectorStatus.ERROR) {
            CameraMessageCard(
                title = "Pothole model unavailable",
                message = detectionState.errorMessage
                    ?: "The local pothole model could not be initialized."
            )
        }

        RoadarCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                SectionHeading("Detection status")
                StatusLine(
                    title = "Camera",
                    status = when {
                        permissionState != CameraPermissionState.GRANTED -> "Permission required"
                        cameraError != null -> "Unavailable"
                        isPreviewStreaming -> "Preview active"
                        else -> "Opening preview"
                    }
                )
                StatusLine(
                    title = "YOLO26n model",
                    status = when (detectionState.status) {
                        DetectorStatus.LOADING -> "Loading local model"
                        DetectorStatus.READY -> "Ready"
                        DetectorStatus.ERROR -> "Unavailable"
                    }
                )
                detectionState.executionProvider?.let { executionProvider ->
                    StatusLine(title = "Runtime", status = executionProvider)
                }
            }
        }

        ReportingModeSelector(
            selectedMode = reportingMode,
            onModeSelected = { mode ->
                reportingMode = mode
                automaticTrackingEnabled = mode == ReportingMode.AUTOMATIC
                pendingDetection = null
            }
        )
        if (reportingMode == ReportingMode.MANUAL) {
            ManualReportingCard(
                frame = detectionState.latestFrame,
                isSaving = isSavingReport,
                feedback = reportFeedback,
                showLocationSettingsAction = showLocationSettingsAction,
                onReviewDetection = { detection, timestamp ->
                    pendingDetection = ConfirmedDetection(detection, timestamp)
                    reportFeedback = null
                },
                onRetryLocation = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                onOpenLocationSettings = {
                    context.startActivity(appSettingsIntent)
                }
            )
        } else {
            AutomaticTrackingCard(
                isEnabled = automaticTrackingEnabled,
                tracking = detectionState.tracking,
                modelReady = detectionState.status == DetectorStatus.READY,
                cameraStreaming = isPreviewStreaming,
                errorMessage = detectionState.errorMessage,
                onEnabledChange = { automaticTrackingEnabled = it }
            )
        }

        SectionHeading(
            title = "Performance",
            supportingText = "Live device measurements; frame rate counts completed inferences"
        )
        val latestFrame = detectionState.latestFrame
        val highestConfidence = latestFrame?.detections?.maxOfOrNull { it.confidence }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Confidence",
                value = highestConfidence?.let { "${(it * 100).roundToInt()}%" }
                    ?: if (latestFrame == null) "—" else "None",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Inference",
                value = latestFrame?.let { formatTime(it.inferenceTimeMs) } ?: "—",
                valueTestTag = "detection_metric_inference",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Latency",
                value = latestFrame?.let { formatTime(it.processingLatencyMs) } ?: "—",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Processed FPS",
                value = detectionState.processedFps?.let { String.format(Locale.US, "%.1f", it) }
                    ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = detectionState.modelLoadTimeMs?.let {
                "Model load: ${formatTime(it)}"
            } ?: "Model load: —",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    pendingDetection?.let { confirmed ->
        AlertDialog(
            onDismissRequest = {
                if (!isSavingReport) pendingDetection = null
            },
            title = { Text("Confirm pothole report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Save this real YOLO26n detection (${(confirmed.detection.confidence * 100).roundToInt()}% confidence) " +
                            "with a fresh device location? The coordinate represents the phone's position, not a surveyed pothole location."
                    )
                    reportFeedback?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (showLocationSettingsAction) {
                        TextButton(
                            onClick = {
                                pendingDetection = null
                                context.startActivity(appSettingsIntent)
                            }
                        ) {
                            Text("Open app settings")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSavingReport,
                    onClick = {
                        if (hasLocationPermission(context)) {
                            saveConfirmedReport()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                    }
                ) {
                    Text(if (isSavingReport) "Saving…" else "Confirm and save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSavingReport,
                    onClick = { pendingDetection = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReportingModeSelector(
    selectedMode: ReportingMode,
    onModeSelected: (ReportingMode) -> Unit
) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeading(
                title = "Reporting mode",
                supportingText = "Manual is the default. Automatic tracking is experimental."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedMode == ReportingMode.MANUAL) {
                    Button(
                        onClick = { onModeSelected(ReportingMode.MANUAL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reporting_mode_manual")
                    ) {
                        Text("MANUAL")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeSelected(ReportingMode.MANUAL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reporting_mode_manual")
                    ) {
                        Text("MANUAL")
                    }
                }
                if (selectedMode == ReportingMode.AUTOMATIC) {
                    Button(
                        onClick = { onModeSelected(ReportingMode.AUTOMATIC) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reporting_mode_automatic")
                    ) {
                        Text("AUTOMATIC · EXPERIMENTAL")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeSelected(ReportingMode.AUTOMATIC) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reporting_mode_automatic")
                    ) {
                        Text("AUTOMATIC · EXPERIMENTAL")
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomaticTrackingCard(
    isEnabled: Boolean,
    tracking: DetectionTrackingUiState,
    modelReady: Boolean,
    cameraStreaming: Boolean,
    errorMessage: String?,
    onEnabledChange: (Boolean) -> Unit
) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading(
                title = "Automatic reporting · Experimental",
                supportingText = "Real model boxes are tracked across frames; persistence remains disabled until field validation."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnabled) "Experimental tracking enabled" else "Tracking paused",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("automatic_tracking_switch")
                )
            }
            StatusLine(
                title = "Session",
                status = when {
                    !isEnabled -> "Paused"
                    !modelReady -> "Waiting for the local model"
                    !cameraStreaming -> "Waiting for the camera"
                    tracking.isEnabled -> "Tracking live detections"
                    else -> "Starting tracker"
                }
            )
            StatusLine(
                title = "Detection confidence",
                status = tracking.latestConfidence?.let {
                    "${(it * 100).roundToInt()}% · threshold " +
                        String.format(
                            Locale.US,
                            "%.0f%%",
                            PotholeDetectionTracker.DEFAULT_CONFIDENCE_THRESHOLD * 100f
                        )
                } ?: "Waiting for a detection at or above 60%"
            )
            StatusLine(
                title = "Track confirmation",
                status = "${tracking.confirmedTrackCount} confirmed · " +
                    "${tracking.activeTrackCount} active · " +
                    "${tracking.confirmationProgress}/${PotholeDetectionTracker.DEFAULT_CONFIRMATION_FRAMES} frames"
            )
            StatusLine(
                title = "GPS",
                status = "Not requested. Automatic report persistence is disabled."
            )
            StatusLine(title = "Automatically saved this session", status = "0")
            Text(
                text = "Automatic persistence is paused: IoU tracks can split or switch as the phone and camera move. GPS proximity alone cannot reliably deduplicate nearby potholes. Tracking uses actual detector output only; no automatic reports are being written.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tracking.confirmationsThisFrame > 0) {
                Text(
                    text = "${tracking.confirmationsThisFrame} track(s) reached temporal confirmation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("automatic_tracking_confirmation")
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("automatic_tracking_error")
                )
            }
        }
    }
}

@Composable
private fun ManualReportingCard(
    frame: DetectionFrame?,
    isSaving: Boolean,
    feedback: String?,
    showLocationSettingsAction: Boolean,
    onReviewDetection: (PotholeDetection, Long) -> Unit,
    onRetryLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading(
                title = "Manual reporting",
                supportingText = "Reports require a real model detection and a recent device location."
            )
            val currentFrame = frame?.takeIf {
                val age = System.currentTimeMillis() - it.detectionAtUtcMillis
                age in 0..ManualPotholeReportFactory.MAX_DETECTION_AGE_MILLIS
            }
            val detections = currentFrame?.detections.orEmpty()
            if (detections.isEmpty()) {
                Text(
                    "No current pothole detection is available to report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                detections.forEachIndexed { index, detection ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pothole ${index + 1} · ${(detection.confidence * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            enabled = !isSaving,
                            onClick = {
                                currentFrame?.let {
                                    onReviewDetection(detection, it.detectionAtUtcMillis)
                                }
                            }
                        ) {
                            Text("Review")
                        }
                    }
                }
            }

            feedback?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.startsWith("Report saved")) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.testTag("report_feedback")
                )
                if (showLocationSettingsAction) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRetryLocation) {
                            Text("Retry location")
                        }
                        TextButton(onClick = onOpenLocationSettings) {
                            Text("App settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewCard(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    controller: CameraPreviewController,
    analyzer: androidx.camera.core.ImageAnalysis.Analyzer,
    analyzerExecutor: java.util.concurrent.Executor,
    isStreaming: Boolean,
    detectionFrame: DetectionFrame?,
    onStreamingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary)
        ) {
            CameraPreviewSurface(
                lifecycleOwner = lifecycleOwner,
                controller = controller,
                analyzer = analyzer,
                analyzerExecutor = analyzerExecutor,
                modifier = Modifier.fillMaxSize(),
                onStreamingChanged = onStreamingChanged,
                onError = onError
            )
            DetectionOverlay(frame = detectionFrame)
            if (!isStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.height(32.dp)
                        )
                        Text(
                            text = "Opening rear camera…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraMessageCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    RoadarCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                if (actionLabel == "Enable camera" || actionLabel == "Try again") {
                    Button(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(actionLabel)
                    }
                } else {
                    OutlinedButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    valueTestTag: String? = null,
    modifier: Modifier = Modifier
) {
    RoadarCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                modifier = if (valueTestTag == null) Modifier else Modifier.testTag(valueTestTag),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }

}

private fun formatTime(milliseconds: Float): String =
    String.format(Locale.US, "%.1f ms", milliseconds)

private fun resolveCameraPermissionState(
    context: Context,
    activity: ComponentActivity?,
    preferences: android.content.SharedPreferences
): CameraPermissionState {
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return CameraPermissionState.GRANTED
    }

    val hasRequestedBefore = preferences.getBoolean(CAMERA_PERMISSION_REQUESTED, false)
    if (!hasRequestedBefore) return CameraPermissionState.NOT_REQUESTED

    return if (
        activity != null &&
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    ) {
        CameraPermissionState.DENIED
    } else {
        CameraPermissionState.PERMANENTLY_DENIED
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

private fun Context.findComponentActivity(): ComponentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) return currentContext
        currentContext = currentContext.baseContext
    }
    return currentContext as? ComponentActivity
}
