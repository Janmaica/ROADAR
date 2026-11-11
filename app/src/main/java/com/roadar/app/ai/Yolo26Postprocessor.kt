package com.roadar.app.ai

internal object Yolo26Postprocessor {
    private const val OUTPUT_CHANNELS = 5

    private data class Candidate(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val confidence: Float
    )

    fun decode(
        output: FloatArray,
        anchorCount: Int,
        transform: ModelTransform,
        confidenceThreshold: Float = 0.25f,
        iouThreshold: Float = 0.7f,
        maxDetections: Int = 300
    ): List<PotholeDetection> {
        require(output.size == OUTPUT_CHANNELS * anchorCount) {
            "Expected $OUTPUT_CHANNELS output channels across $anchorCount anchors."
        }

        val candidates = ArrayList<Candidate>()
        for (anchor in 0 until anchorCount) {
            val confidence = output[(OUTPUT_CHANNELS - 1) * anchorCount + anchor]
            if (!confidence.isFinite() || confidence <= confidenceThreshold) continue

            val centerX = output[anchor]
            val centerY = output[anchorCount + anchor]
            val width = output[anchorCount * 2 + anchor]
            val height = output[anchorCount * 3 + anchor]
            if (!centerX.isFinite() || !centerY.isFinite() ||
                !width.isFinite() || !height.isFinite() ||
                width <= 0f || height <= 0f
            ) {
                continue
            }

            candidates += Candidate(
                left = centerX - width / 2f,
                top = centerY - height / 2f,
                right = centerX + width / 2f,
                bottom = centerY + height / 2f,
                confidence = confidence
            )
        }

        val sorted = candidates.sortedByDescending(Candidate::confidence)
        val kept = ArrayList<Candidate>(minOf(sorted.size, maxDetections))
        for (candidate in sorted) {
            if (kept.none { intersectionOverUnion(candidate, it) > iouThreshold }) {
                kept += candidate
                if (kept.size == maxDetections) break
            }
        }

        return kept.mapNotNull { candidate ->
            val left = ((candidate.left - transform.padLeft) / transform.scale)
                .coerceIn(0f, transform.sourceWidth.toFloat())
            val top = ((candidate.top - transform.padTop) / transform.scale)
                .coerceIn(0f, transform.sourceHeight.toFloat())
            val right = ((candidate.right - transform.padLeft) / transform.scale)
                .coerceIn(0f, transform.sourceWidth.toFloat())
            val bottom = ((candidate.bottom - transform.padTop) / transform.scale)
                .coerceIn(0f, transform.sourceHeight.toFloat())
            if (right <= left || bottom <= top) {
                null
            } else {
                PotholeDetection(left, top, right, bottom, candidate.confidence)
            }
        }
    }

    private fun intersectionOverUnion(first: Candidate, second: Candidate): Float {
        val intersectionWidth = (minOf(first.right, second.right) -
            maxOf(first.left, second.left)).coerceAtLeast(0f)
        val intersectionHeight = (minOf(first.bottom, second.bottom) -
            maxOf(first.top, second.top)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
