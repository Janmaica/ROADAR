package com.roadar.app.ai

data class TrackedPothole(
    val trackId: Long,
    val detection: PotholeDetection,
    val consecutiveFrames: Int,
    val isConfirmed: Boolean,
    val wasReported: Boolean
)

data class DetectionTrackingSnapshot(
    val frameIndex: Long,
    val tracks: List<TrackedPothole>,
    val qualifyingDetectionCount: Int,
    val newlyConfirmedTrackIds: Set<Long>
) {
    val confirmedTrackCount: Int
        get() = tracks.count(TrackedPothole::isConfirmed)

    val highestConfidence: Float?
        get() = tracks.maxOfOrNull { it.detection.confidence }

    val confirmationProgress: Int
        get() = tracks.asSequence()
            .filterNot(TrackedPothole::isConfirmed)
            .maxOfOrNull(TrackedPothole::consecutiveFrames)
            ?: 0
}

class PotholeDetectionTracker(
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    val confirmationFrames: Int = DEFAULT_CONFIRMATION_FRAMES,
    val minimumIou: Float = DEFAULT_MINIMUM_IOU,
    val maximumMissedFrames: Int = DEFAULT_MAXIMUM_MISSED_FRAMES
) {
    private data class MutableTrack(
        val id: Long,
        var detection: PotholeDetection,
        var lastSeenFrame: Long,
        var missedFrames: Int = 0,
        var consecutiveFrames: Int = 1,
        var confirmed: Boolean = false,
        var reported: Boolean = false
    )

    private data class Match(val trackId: Long, val detectionIndex: Int, val iou: Float)

    private val tracks = linkedMapOf<Long, MutableTrack>()
    private val reportedTrackIds = mutableSetOf<Long>()
    private var frameIndex = 0L
    private var nextTrackId = 1L

    init {
        require(confidenceThreshold in 0f..1f)
        require(confirmationFrames >= 1)
        require(minimumIou in 0f..1f)
        require(maximumMissedFrames >= 0)
    }

    @Synchronized
    fun update(detections: List<PotholeDetection>): DetectionTrackingSnapshot {
        frameIndex += 1
        val qualifying = detections.filter(::isQualifying)
        val availableTracks = tracks.values
            .filter { it.missedFrames <= maximumMissedFrames }
        val matches = availableTracks.flatMap { track ->
            qualifying.indices.mapNotNull { detectionIndex ->
                val overlap = intersectionOverUnion(track.detection, qualifying[detectionIndex])
                if (overlap >= minimumIou) Match(track.id, detectionIndex, overlap) else null
            }
        }.sortedByDescending(Match::iou)

        val matchedTrackIds = mutableSetOf<Long>()
        val matchedDetectionIndices = mutableSetOf<Int>()
        val newlyConfirmed = mutableSetOf<Long>()
        matches.forEach { match ->
            if (matchedTrackIds.add(match.trackId) &&
                matchedDetectionIndices.add(match.detectionIndex)
            ) {
                val track = tracks.getValue(match.trackId)
                val wasConsecutive = track.lastSeenFrame == frameIndex - 1
                track.consecutiveFrames = if (wasConsecutive) track.consecutiveFrames + 1 else 1
                track.detection = qualifying[match.detectionIndex]
                track.lastSeenFrame = frameIndex
                track.missedFrames = 0
                if (!track.confirmed && track.consecutiveFrames >= confirmationFrames) {
                    track.confirmed = true
                    newlyConfirmed += track.id
                }
            }
        }

        tracks.values.forEach { track ->
            if (track.id !in matchedTrackIds) {
                track.missedFrames += 1
                track.consecutiveFrames = 0
            }
        }
        tracks.entries.removeAll { (_, track) -> track.missedFrames > maximumMissedFrames }

        qualifying.forEachIndexed { index, detection ->
            if (index !in matchedDetectionIndices) {
                tracks[nextTrackId] = MutableTrack(
                    id = nextTrackId,
                    detection = detection,
                    lastSeenFrame = frameIndex
                )
                nextTrackId += 1
            }
        }

        return DetectionTrackingSnapshot(
            frameIndex = frameIndex,
            tracks = tracks.values.map { track ->
                TrackedPothole(
                    trackId = track.id,
                    detection = track.detection,
                    consecutiveFrames = track.consecutiveFrames,
                    isConfirmed = track.confirmed,
                    wasReported = track.reported
                )
            },
            qualifyingDetectionCount = qualifying.size,
            newlyConfirmedTrackIds = newlyConfirmed
        )
    }

    @Synchronized
    fun markReported(trackId: Long): Boolean {
        val track = tracks[trackId] ?: return false
        if (!track.confirmed || !reportedTrackIds.add(trackId)) return false
        track.reported = true
        return true
    }

    @Synchronized
    fun clearActiveTracks() {
        tracks.clear()
    }

    private fun isQualifying(detection: PotholeDetection): Boolean =
        detection.confidence.isFinite() &&
            detection.confidence >= confidenceThreshold &&
            detection.left.isFinite() &&
            detection.top.isFinite() &&
            detection.right.isFinite() &&
            detection.bottom.isFinite() &&
            detection.right > detection.left &&
            detection.bottom > detection.top

    private fun intersectionOverUnion(first: PotholeDetection, second: PotholeDetection): Float {
        val intersectionWidth =
            (minOf(first.right, second.right) - maxOf(first.left, second.left)).coerceAtLeast(0f)
        val intersectionHeight =
            (minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection
        return if (union > 0f) intersection / union else 0f
    }

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.60f
        const val DEFAULT_CONFIRMATION_FRAMES = 3
        const val DEFAULT_MINIMUM_IOU = 0.30f
        const val DEFAULT_MAXIMUM_MISSED_FRAMES = 1
    }
}
