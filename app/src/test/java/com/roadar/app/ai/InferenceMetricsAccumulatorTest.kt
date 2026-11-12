package com.roadar.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceMetricsAccumulatorTest {
    @Test
    fun summarizesLatencyPercentilesFpsAndDetectionCounts() {
        val accumulator = InferenceMetricsAccumulator(maximumSamples = 10)
        val samples = listOf(10f, 20f, 30f, 40f, 50f)

        val summary = samples.mapIndexed { index, latency ->
            accumulator.record(
                inferenceTimeMs = latency,
                processingLatencyMs = latency + 10f,
                detectionCount = index
            )
        }.last()

        assertEquals(5L, summary.processedFrameCount)
        assertEquals(10L, summary.detectedPotholeCount)
        assertEquals(5, summary.sampleCount)
        assertEquals(30f, summary.averageInferenceTimeMs, 0.001f)
        assertEquals(10f, summary.minimumInferenceTimeMs, 0.001f)
        assertEquals(50f, summary.maximumInferenceTimeMs, 0.001f)
        assertEquals(30f, summary.medianInferenceTimeMs, 0.001f)
        assertEquals(50f, summary.p95InferenceTimeMs, 0.001f)
        assertEquals(40f, summary.averageProcessingLatencyMs, 0.001f)
        assertEquals(25f, summary.processingCapacityFps, 0.001f)
    }

    @Test
    fun latencyStatisticsUseBoundedRecentSamplesButKeepSessionCounts() {
        val accumulator = InferenceMetricsAccumulator(maximumSamples = 2)

        accumulator.record(10f, 20f, 1)
        accumulator.record(20f, 30f, 2)
        val summary = accumulator.record(30f, 40f, 3)

        assertEquals(3L, summary.processedFrameCount)
        assertEquals(6L, summary.detectedPotholeCount)
        assertEquals(2, summary.sampleCount)
        assertEquals(25f, summary.averageInferenceTimeMs, 0.001f)
        assertEquals(20f, summary.minimumInferenceTimeMs, 0.001f)
        assertEquals(30f, summary.maximumInferenceTimeMs, 0.001f)
        assertEquals(25f, summary.medianInferenceTimeMs, 0.001f)
        assertEquals(30f, summary.p95InferenceTimeMs, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidMeasurementInput() {
        InferenceMetricsAccumulator().record(
            inferenceTimeMs = Float.NaN,
            processingLatencyMs = 10f,
            detectionCount = -1
        )
    }
}
