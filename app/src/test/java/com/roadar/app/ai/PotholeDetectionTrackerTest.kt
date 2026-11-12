package com.roadar.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PotholeDetectionTrackerTest {
    private val tracker = PotholeDetectionTracker()

    @Test
    fun confirmsOneTrackAfterThreeHighConfidenceMatchingFrames() {
        val first = tracker.update(listOf(box(0f, 0f, 100f, 100f, 0.82f)))
        val second = tracker.update(listOf(box(3f, 2f, 103f, 102f, 0.86f)))
        val third = tracker.update(listOf(box(4f, 3f, 104f, 103f, 0.91f)))

        assertEquals(1, first.tracks.single().consecutiveFrames)
        assertFalse(first.tracks.single().isConfirmed)
        assertEquals(first.tracks.single().trackId, second.tracks.single().trackId)
        assertEquals(first.tracks.single().trackId, third.newlyConfirmedTrackIds.single())
        assertTrue(third.tracks.single().isConfirmed)
    }

    @Test
    fun associatesMultipleObjectsIndependentlyWhenDetectionOrderChanges() {
        val first = tracker.update(
            listOf(
                box(0f, 0f, 50f, 50f, 0.80f),
                box(200f, 0f, 250f, 50f, 0.90f)
            )
        )
        val firstIds = first.tracks.associateBy { it.detection.left }

        val second = tracker.update(
            listOf(
                box(197f, 0f, 247f, 50f, 0.92f),
                box(3f, 0f, 53f, 50f, 0.81f)
            )
        )

        assertEquals(firstIds.getValue(0f).trackId, second.tracks.single { it.detection.left == 3f }.trackId)
        assertEquals(firstIds.getValue(200f).trackId, second.tracks.single { it.detection.left == 197f }.trackId)
        assertEquals(2, second.tracks.size)
    }

    @Test
    fun ignoresLowConfidenceAndInvalidBoxes() {
        val snapshot = tracker.update(
            listOf(
                box(0f, 0f, 10f, 10f, 0.59f),
                box(0f, 0f, Float.NaN, 10f, 0.95f),
                box(5f, 5f, 5f, 10f, 0.99f)
            )
        )

        assertEquals(0, snapshot.qualifyingDetectionCount)
        assertTrue(snapshot.tracks.isEmpty())
    }

    @Test
    fun trackExpiresAfterMissesAndReacquisitionGetsANewIdentity() {
        val originalId = tracker.update(listOf(box(0f, 0f, 50f, 50f, 0.8f))).tracks.single().trackId
        tracker.update(emptyList())
        tracker.update(emptyList())
        val reacquired = tracker.update(listOf(box(0f, 0f, 50f, 50f, 0.8f))).tracks.single()

        assertTrue(reacquired.trackId > originalId)
        assertEquals(1, reacquired.consecutiveFrames)
        assertFalse(reacquired.isConfirmed)
    }

    @Test
    fun confirmedTrackCanOnlyBeMarkedReportedOnce() {
        var trackId = -1L
        repeat(3) {
            trackId = tracker.update(listOf(box(0f, 0f, 50f, 50f, 0.8f))).tracks.single().trackId
        }

        assertTrue(tracker.markReported(trackId))
        assertFalse(tracker.markReported(trackId))
        assertTrue(tracker.update(listOf(box(0f, 0f, 50f, 50f, 0.8f))).tracks.single().wasReported)
    }

    private fun box(left: Float, top: Float, right: Float, bottom: Float, confidence: Float) =
        PotholeDetection(left, top, right, bottom, confidence)
}
