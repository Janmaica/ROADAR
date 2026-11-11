package com.roadar.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadar.app.ai.OnnxPotholeDetector
import com.roadar.app.ai.YOLO26_MODEL_ASSET
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val STAGE4_SAMPLE_IMAGE = "roadar-stage4-pothole.jpg"

@RunWith(AndroidJUnit4::class)
class PotholeInferenceInstrumentedTest {
    @Test
    fun stageFourHeldOutImageProducesARealPotholeDetection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val applicationContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val hasModel = applicationContext.assets.list("")?.contains(YOLO26_MODEL_ASSET) == true
        val hasImage = testContext.assets.list("")?.contains(STAGE4_SAMPLE_IMAGE) == true
        assumeTrue("Local model and held-out image assets are not installed.", hasModel && hasImage)

        val bitmap = testContext.assets.open(STAGE4_SAMPLE_IMAGE).use { input ->
            requireNotNull(BitmapFactory.decodeStream(input))
        }
        val detector = OnnxPotholeDetector(applicationContext)
        try {
            val inferenceTimes = ArrayList<Float>()
            val processingLatencies = ArrayList<Float>()
            var detectionCount = 0
            repeat(6) { iteration ->
                val result = detector.detect(bitmap)
                detectionCount = result.detections.size
                assertFalse(
                    "The held-out Stage 4 pothole image produced no detections.",
                    result.detections.isEmpty()
                )
                if (iteration > 0) {
                    inferenceTimes += result.inferenceTimeMs
                    processingLatencies += result.processingLatencyMs
                }
            }
            val medianInference = inferenceTimes.sorted()[inferenceTimes.size / 2]
            val medianProcessing = processingLatencies.sorted()[processingLatencies.size / 2]
            Log.i(
                "ROADAR-Model-Test",
                "warmRuns=${inferenceTimes.size}, loadMs=${detector.modelLoadTimeMs}, " +
                    "medianInferenceMs=$medianInference, medianProcessingMs=$medianProcessing, " +
                    "detections=$detectionCount"
            )
            assertTrue(detectionCount > 0)
        } finally {
            detector.close()
            bitmap.recycle()
        }
    }
}
