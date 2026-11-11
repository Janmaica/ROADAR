package com.roadar.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Yolo26PostprocessorTest {
    @Test
    fun removesOverlappingCandidatesAndKeepsHighestConfidence() {
        val output = output(
            Candidate(320f, 320f, 100f, 100f, 0.9f),
            Candidate(322f, 322f, 100f, 100f, 0.8f),
            Candidate(100f, 100f, 40f, 40f, 0.7f)
        )

        val detections = Yolo26Postprocessor.decode(
            output = output,
            anchorCount = 3,
            transform = ModelTransform(640, 640, 1f, 0, 0)
        )

        assertEquals(2, detections.size)
        assertEquals(0.9f, detections[0].confidence, 0.0001f)
        assertEquals(0.7f, detections[1].confidence, 0.0001f)
    }

    @Test
    fun appliesConfidenceThresholdAndReversesLetterboxPadding() {
        val output = output(
            Candidate(180f, 250f, 20f, 40f, 0.24f),
            Candidate(180f, 250f, 20f, 40f, 0.8f)
        )

        val detections = Yolo26Postprocessor.decode(
            output = output,
            anchorCount = 2,
            transform = ModelTransform(
                sourceWidth = 320,
                sourceHeight = 640,
                scale = 1f,
                padLeft = 160,
                padTop = 0
            )
        )

        assertEquals(1, detections.size)
        assertEquals(10f, detections.single().left, 0.0001f)
        assertEquals(230f, detections.single().top, 0.0001f)
        assertEquals(30f, detections.single().right, 0.0001f)
        assertEquals(270f, detections.single().bottom, 0.0001f)
        assertTrue(detections.single().confidence > 0.25f)
    }

    @Test
    fun clipsBoxesToTheSourceImageAndDropsEmptyBoxes() {
        val output = output(
            Candidate(5f, 5f, 20f, 20f, 0.9f),
            Candidate(320f, 320f, 10f, 10f, 0.8f)
        )

        val detections = Yolo26Postprocessor.decode(
            output = output,
            anchorCount = 2,
            transform = ModelTransform(100, 100, 1f, 0, 0)
        )

        assertEquals(1, detections.size)
        assertEquals(0f, detections.single().left, 0.0001f)
        assertEquals(0f, detections.single().top, 0.0001f)
        assertEquals(15f, detections.single().right, 0.0001f)
        assertEquals(15f, detections.single().bottom, 0.0001f)
    }

    private fun output(vararg candidates: Candidate): FloatArray {
        val anchorCount = candidates.size
        val output = FloatArray(anchorCount * 5)
        candidates.forEachIndexed { index, candidate ->
            output[index] = candidate.centerX
            output[anchorCount + index] = candidate.centerY
            output[anchorCount * 2 + index] = candidate.width
            output[anchorCount * 3 + index] = candidate.height
            output[anchorCount * 4 + index] = candidate.confidence
        }
        return output
    }

    private data class Candidate(
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float
    )
}
