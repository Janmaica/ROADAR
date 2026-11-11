package com.roadar.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.ComponentActivity
import com.roadar.app.ui.camera.CameraPreviewController
import com.roadar.app.ui.camera.CameraPreviewSurface
import com.roadar.app.ui.components.PageHeader
import com.roadar.app.ui.components.RoadarCard
import com.roadar.app.ui.components.SectionHeading
import com.roadar.app.ui.components.StatusLine

private const val CAMERA_PERMISSION_PREFERENCES = "roadar_camera_permission"
private const val CAMERA_PERMISSION_REQUESTED = "requested"

private enum class CameraPermissionState {
    NOT_REQUESTED,
    DENIED,
    PERMANENTLY_DENIED,
    GRANTED,
    REQUESTING
}

@Composable
fun DetectionScreen() {
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
    val cameraController = remember(context) { CameraPreviewController(context) }

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

    DisposableEffect(activity, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = resolveCameraPermissionState(context, activity, preferences)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            cameraController.unbind()
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
            description = "Rear-camera preview is ready for future model integration. No inference is running."
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
                    isStreaming = isPreviewStreaming,
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
                        "ROADAR uses the rear camera for a live preview. Camera frames are not analyzed or saved in this phase."
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
                StatusLine(title = "Model", status = "YOLO26n not integrated")
            }
        }

        SectionHeading(
            title = "Performance",
            supportingText = "No inference metrics are available before model integration"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(title = "Confidence", value = "—", modifier = Modifier.weight(1f))
            MetricCard(title = "Inference", value = "— ms", modifier = Modifier.weight(1f))
            MetricCard(title = "FPS", value = "—", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CameraPreviewCard(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    controller: CameraPreviewController,
    isStreaming: Boolean,
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
                modifier = Modifier.fillMaxSize(),
                onStreamingChanged = onStreamingChanged,
                onError = onError
            )
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
    modifier: Modifier = Modifier
) {
    RoadarCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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

private fun Context.findComponentActivity(): ComponentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) return currentContext
        currentContext = currentContext.baseContext
    }
    return currentContext as? ComponentActivity
}
