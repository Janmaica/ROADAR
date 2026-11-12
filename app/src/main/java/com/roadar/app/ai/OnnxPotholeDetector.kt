package com.roadar.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import androidx.camera.core.ImageProxy
import java.io.IOException
import java.nio.FloatBuffer

internal const val YOLO26_MODEL_ASSET = "roadar_yolo26n.onnx"
private const val YOLO26_ANCHOR_COUNT = 8400

internal class OnnxPotholeDetector(context: Context) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    val executionProvider = "CPU"
    val modelLoadTimeMs: Float

    init {
        val loadStarted = SystemClock.elapsedRealtimeNanos()
        val modelBytes = context.assets.open(YOLO26_MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        val createdSession = try {
            options.setIntraOpNumThreads(2)
            environment.createSession(modelBytes, options)
        } finally {
            options.close()
        }

        try {
            inputName = createdSession.inputNames.singleOrNull()
                ?: throw IllegalStateException("The model must have exactly one input.")
            val inputInfo = createdSession.inputInfo[inputName]?.info as? TensorInfo
                ?: throw IllegalStateException("The model input is not a tensor.")
            require(inputInfo.shape.contentEquals(longArrayOf(1, 3, 640, 640))) {
                "The model input must have shape [1, 3, 640, 640]."
            }

            val outputName = createdSession.outputNames.singleOrNull()
                ?: throw IllegalStateException("The model must have exactly one output.")
            val outputInfo = createdSession.outputInfo[outputName]?.info as? TensorInfo
                ?: throw IllegalStateException("The model output is not a tensor.")
            require(outputInfo.shape.contentEquals(longArrayOf(1, 5, YOLO26_ANCHOR_COUNT.toLong()))) {
                "The model output must have shape [1, 5, $YOLO26_ANCHOR_COUNT]."
            }
            session = createdSession
        } catch (exception: IllegalArgumentException) {
            createdSession.close()
            throw exception
        } catch (exception: IllegalStateException) {
            createdSession.close()
            throw exception
        }

        modelLoadTimeMs = (SystemClock.elapsedRealtimeNanos() - loadStarted) / 1_000_000f
    }

    fun detect(image: ImageProxy): DetectionFrame {
        val started = SystemClock.elapsedRealtimeNanos()
        val detectionAtUtcMillis = System.currentTimeMillis()
        return infer(Yolo26Preprocessor.fromImageProxy(image), started, detectionAtUtcMillis)
    }

    fun detect(bitmap: Bitmap): DetectionFrame {
        val started = SystemClock.elapsedRealtimeNanos()
        val detectionAtUtcMillis = System.currentTimeMillis()
        return infer(Yolo26Preprocessor.fromBitmap(bitmap), started, detectionAtUtcMillis)
    }

    private fun infer(
        input: PreparedModelInput,
        processingStartedNanos: Long,
        detectionAtUtcMillis: Long
    ): DetectionFrame {
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input.nchwRgb),
            longArrayOf(1, 3, 640, 640)
        )
        val inferenceStarted = SystemClock.elapsedRealtimeNanos()
        val result = try {
            session.run(mapOf(inputName to tensor))
        } catch (exception: OrtException) {
            tensor.close()
            throw exception
        }
        val inferenceFinished = SystemClock.elapsedRealtimeNanos()

        try {
            val output = result[0] as? OnnxTensor
                ?: throw IllegalStateException("The model returned a non-tensor output.")
            val outputBuffer = output.floatBuffer
            val outputValues = FloatArray(outputBuffer.remaining())
            outputBuffer.get(outputValues)
            val detections = Yolo26Postprocessor.decode(
                output = outputValues,
                anchorCount = YOLO26_ANCHOR_COUNT,
                transform = input.transform
            )
            val finished = SystemClock.elapsedRealtimeNanos()
            return DetectionFrame(
                frameWidth = input.transform.sourceWidth,
                frameHeight = input.transform.sourceHeight,
                detectionAtUtcMillis = detectionAtUtcMillis,
                detections = detections,
                inferenceTimeMs = (inferenceFinished - inferenceStarted) / 1_000_000f,
                processingLatencyMs = (finished - processingStartedNanos) / 1_000_000f
            )
        } finally {
            result.close()
            tensor.close()
        }
    }

    override fun close() {
        session.close()
    }
}
