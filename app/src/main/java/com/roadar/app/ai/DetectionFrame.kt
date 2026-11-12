package com.roadar.app.ai

data class PotholeDetection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

data class DetectionFrame(
    val frameWidth: Int,
    val frameHeight: Int,
    val detectionAtUtcMillis: Long,
    val detections: List<PotholeDetection>,
    val inferenceTimeMs: Float,
    val processingLatencyMs: Float
)

enum class DetectorStatus {
    LOADING,
    READY,
    ERROR
}

data class DetectionUiState(
    val status: DetectorStatus = DetectorStatus.LOADING,
    val modelLoadTimeMs: Float? = null,
    val executionProvider: String? = null,
    val errorMessage: String? = null,
    val latestFrame: DetectionFrame? = null,
    val processedFps: Float? = null,
    val performance: InferencePerformanceSummary? = null
)
