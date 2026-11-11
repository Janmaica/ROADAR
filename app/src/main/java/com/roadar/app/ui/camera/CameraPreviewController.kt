package com.roadar.app.ui.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.CameraUnavailableException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.util.concurrent.ExecutionException

class CameraPreviewController(context: Context) {
    private val applicationContext = context.applicationContext
    private var generation = 0
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var previewView: PreviewView? = null
    private var streamObserver: Observer<PreviewView.StreamState>? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onStreamingChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        unbind()
        val bindGeneration = ++generation
        val providerFuture = ProcessCameraProvider.getInstance(applicationContext)

        providerFuture.addListener(
            {
                if (bindGeneration != generation) return@addListener

                val provider = try {
                    providerFuture.get()
                } catch (exception: InterruptedException) {
                    Thread.currentThread().interrupt()
                    onError("Camera initialization was interrupted. Please try again.")
                    return@addListener
                } catch (exception: ExecutionException) {
                    onError("The camera service could not be started. Please try again.")
                    return@addListener
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val observer = Observer<PreviewView.StreamState> { state ->
                    if (bindGeneration == generation) {
                        onStreamingChanged(state == PreviewView.StreamState.STREAMING)
                    }
                }
                previewView.previewStreamState.observe(lifecycleOwner, observer)

                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                    cameraProvider = provider
                    previewUseCase = preview
                    this.previewView = previewView
                    streamObserver = observer
                } catch (exception: SecurityException) {
                    previewView.previewStreamState.removeObserver(observer)
                    onError("Camera permission is required to open the rear camera.")
                } catch (exception: CameraUnavailableException) {
                    previewView.previewStreamState.removeObserver(observer)
                    onError("The rear camera is unavailable. Close other camera apps and try again.")
                } catch (exception: IllegalArgumentException) {
                    previewView.previewStreamState.removeObserver(observer)
                    onError("This device does not have a compatible rear camera.")
                } catch (exception: IllegalStateException) {
                    previewView.previewStreamState.removeObserver(observer)
                    onError("The camera could not be started. Please try again.")
                }
            },
            ContextCompat.getMainExecutor(applicationContext)
        )
    }

    fun unbind() {
        generation += 1
        val oldPreview = previewUseCase
        val oldPreviewView = previewView
        val oldObserver = streamObserver
        if (oldPreviewView != null && oldObserver != null) {
            oldPreviewView.previewStreamState.removeObserver(oldObserver)
        }
        if (oldPreview != null) {
            cameraProvider?.unbind(oldPreview)
        }
        cameraProvider = null
        previewUseCase = null
        previewView = null
        streamObserver = null
    }
}
