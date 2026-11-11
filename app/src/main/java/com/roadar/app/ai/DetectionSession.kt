package com.roadar.app.ai

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import ai.onnxruntime.OrtException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class DetectionSession(
    context: Context,
    private val onStateChanged: (DetectionUiState) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val applicationContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val closed = AtomicBoolean(false)
    private var started = false
    private var detector: OnnxPotholeDetector? = null
    private var failed = false
    private var fpsWindowStarted = 0L
    private var framesInFpsWindow = 0
    private var processedFps: Float? = null

    fun start() {
        if (started || closed.get()) return
        started = true
        analyzerExecutor.execute {
            if (closed.get()) return@execute
            try {
                val loadedDetector = OnnxPotholeDetector(applicationContext)
                if (closed.get()) {
                    loadedDetector.close()
                    return@execute
                }
                detector = loadedDetector
                publish(
                    DetectionUiState(
                        status = DetectorStatus.READY,
                        modelLoadTimeMs = loadedDetector.modelLoadTimeMs,
                        executionProvider = loadedDetector.executionProvider
                    )
                )
            } catch (exception: FileNotFoundException) {
                Log.e(TAG, "The local YOLO26n ONNX asset is missing.", exception)
                fail("Model file not found. Add $YOLO26_MODEL_ASSET to app/src/main/assets.")
            } catch (exception: IOException) {
                Log.e(TAG, "The local YOLO26n ONNX asset could not be read.", exception)
                fail("The local pothole model could not be read.")
            } catch (exception: OrtException) {
                Log.e(TAG, "ONNX Runtime could not initialize the pothole model.", exception)
                fail("The pothole model could not be initialized on this device.")
            } catch (exception: IllegalArgumentException) {
                Log.e(TAG, "The ONNX pothole model has an unsupported tensor contract.", exception)
                fail(exception.message ?: "The pothole model has an unsupported tensor shape.")
            } catch (exception: IllegalStateException) {
                Log.e(TAG, "The ONNX pothole model has an unsupported structure.", exception)
                fail(exception.message ?: "The pothole model could not be initialized.")
            }
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (closed.get() || failed) return
            val activeDetector = detector ?: return
            val frame = activeDetector.detect(image)
            if (closed.get()) return

            val now = android.os.SystemClock.elapsedRealtimeNanos()
            if (fpsWindowStarted == 0L) fpsWindowStarted = now
            framesInFpsWindow += 1
            val windowDuration = now - fpsWindowStarted
            if (windowDuration >= FPS_WINDOW_NANOS) {
                processedFps = framesInFpsWindow * 1_000_000_000f / windowDuration
                framesInFpsWindow = 0
                fpsWindowStarted = now
            }
            publish(
                DetectionUiState(
                    status = DetectorStatus.READY,
                    modelLoadTimeMs = activeDetector.modelLoadTimeMs,
                    executionProvider = activeDetector.executionProvider,
                    latestFrame = frame,
                    processedFps = processedFps
                )
            )
        } catch (exception: OrtException) {
            Log.e(TAG, "ONNX Runtime inference failed.", exception)
            fail("Pothole inference stopped because the model runtime reported an error.")
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Pothole frame preprocessing failed.", exception)
            fail("The camera frame could not be prepared for the model.")
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Pothole frame inference failed.", exception)
            fail("Pothole inference stopped because the model output was invalid.")
        } finally {
            image.close()
        }
    }

    private fun fail(message: String) {
        if (failed || closed.get()) return
        failed = true
        publish(DetectionUiState(status = DetectorStatus.ERROR, errorMessage = message))
    }

    private fun publish(state: DetectionUiState) {
        mainExecutor.execute {
            if (!closed.get()) onStateChanged(state)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        analyzerExecutor.execute {
            detector?.close()
            detector = null
        }
        analyzerExecutor.shutdown()
    }

    private companion object {
        const val TAG = "ROADAR-Detection"
        const val FPS_WINDOW_NANOS = 1_000_000_000L
    }
}
