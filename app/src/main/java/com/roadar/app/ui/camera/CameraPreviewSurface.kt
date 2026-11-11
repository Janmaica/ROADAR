package com.roadar.app.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

@Composable
fun CameraPreviewSurface(
    lifecycleOwner: LifecycleOwner,
    controller: CameraPreviewController,
    analyzer: ImageAnalysis.Analyzer,
    analyzerExecutor: Executor,
    modifier: Modifier = Modifier,
    onStreamingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )

    DisposableEffect(controller, lifecycleOwner, previewView) {
        controller.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = analyzer,
            analyzerExecutor = analyzerExecutor,
            onStreamingChanged = onStreamingChanged,
            onError = onError
        )
        onDispose {
            controller.unbind()
        }
    }
}
