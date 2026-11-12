package com.roadar.app.ai

import kotlin.math.ceil

data class InferencePerformanceSummary(
    val processedFrameCount: Long,
    val detectedPotholeCount: Long,
    val sampleCount: Int,
    val averageInferenceTimeMs: Float,
    val minimumInferenceTimeMs: Float,
    val maximumInferenceTimeMs: Float,
    val medianInferenceTimeMs: Float,
    val p95InferenceTimeMs: Float,
    val averageProcessingLatencyMs: Float,
    val processingCapacityFps: Float
)

class InferenceMetricsAccumulator(
    private val maximumSamples: Int = DEFAULT_MAXIMUM_SAMPLES
) {
    private val inferenceSamples = ArrayDeque<Float>()
    private val processingSamples = ArrayDeque<Float>()
    private var processedFrameCount = 0L
    private var detectedPotholeCount = 0L

    init {
        require(maximumSamples > 0)
    }

    fun record(
        inferenceTimeMs: Float,
        processingLatencyMs: Float,
        detectionCount: Int
    ): InferencePerformanceSummary {
        require(inferenceTimeMs.isFinite() && inferenceTimeMs > 0f)
        require(processingLatencyMs.isFinite() && processingLatencyMs >= inferenceTimeMs)
        require(detectionCount >= 0)

        processedFrameCount += 1
        detectedPotholeCount += detectionCount
        inferenceSamples.addLast(inferenceTimeMs)
        processingSamples.addLast(processingLatencyMs)
        if (inferenceSamples.size > maximumSamples) {
            inferenceSamples.removeFirst()
            processingSamples.removeFirst()
        }

        val sortedInference = inferenceSamples.sorted()
        val averageInference = sortedInference.average().toFloat()
        val median = if (sortedInference.size % 2 == 0) {
            val middle = sortedInference.size / 2
            (sortedInference[middle - 1] + sortedInference[middle]) / 2f
        } else {
            sortedInference[sortedInference.size / 2]
        }
        val p95Index = (ceil(sortedInference.size * P95).toInt() - 1)
            .coerceIn(0, sortedInference.lastIndex)
        val totalProcessingSeconds = processingSamples.sum() / MILLIS_PER_SECOND

        return InferencePerformanceSummary(
            processedFrameCount = processedFrameCount,
            detectedPotholeCount = detectedPotholeCount,
            sampleCount = sortedInference.size,
            averageInferenceTimeMs = averageInference,
            minimumInferenceTimeMs = sortedInference.first(),
            maximumInferenceTimeMs = sortedInference.last(),
            medianInferenceTimeMs = median,
            p95InferenceTimeMs = sortedInference[p95Index],
            averageProcessingLatencyMs = processingSamples.average().toFloat(),
            processingCapacityFps = inferenceSamples.size / totalProcessingSeconds
        )
    }

    private companion object {
        const val DEFAULT_MAXIMUM_SAMPLES = 600
        const val MILLIS_PER_SECOND = 1_000f
        const val P95 = 0.95
    }
}
