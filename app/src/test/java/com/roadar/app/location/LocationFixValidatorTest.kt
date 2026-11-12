package com.roadar.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocationFixValidatorTest {
    @Test
    fun acceptsRecentValidLocationWithoutTreatingZeroCoordinatesAsMissing() {
        val fix = DeviceLocationFix(
            latitude = 0.0,
            longitude = 0.0,
            horizontalAccuracyMeters = 35f,
            fixAtUtcMillis = NOW_UTC,
            fixElapsedRealtimeNanos = NOW_ELAPSED
        )

        assertEquals(
            fix,
            LocationFixValidator.validate(fix, NOW_UTC, NOW_ELAPSED)
        )
    }

    @Test
    fun rejectsStaleFix() {
        assertInvalid(
            fix(fixElapsedRealtimeNanos = NOW_ELAPSED - 31_000_000_000L),
            "stale"
        )
    }

    @Test
    fun rejectsInvalidCoordinatesAndPoorAccuracy() {
        assertInvalid(fix(latitude = Double.NaN), "invalid coordinates")
        assertInvalid(
            fix(horizontalAccuracyMeters = LocationFixValidator.MAX_HORIZONTAL_ACCURACY_METERS + 1f),
            "accuracy"
        )
        assertInvalid(
            fix(fixAtUtcMillis = NOW_UTC - LocationFixValidator.MAX_FIX_AGE_MILLIS - 1),
            "timestamp"
        )
    }

    private fun assertInvalid(fix: DeviceLocationFix, message: String) {
        try {
            LocationFixValidator.validate(fix, NOW_UTC, NOW_ELAPSED)
            fail("Expected location fix validation to fail.")
        } catch (exception: LocationFixException) {
            assertTrue(exception.message.orEmpty().contains(message, ignoreCase = true))
        }
    }

    private fun fix(
        latitude: Double = 14.5995,
        longitude: Double = 120.9842,
        horizontalAccuracyMeters: Float = 12f,
        fixAtUtcMillis: Long = NOW_UTC,
        fixElapsedRealtimeNanos: Long = NOW_ELAPSED
    ) = DeviceLocationFix(
        latitude = latitude,
        longitude = longitude,
        horizontalAccuracyMeters = horizontalAccuracyMeters,
        fixAtUtcMillis = fixAtUtcMillis,
        fixElapsedRealtimeNanos = fixElapsedRealtimeNanos
    )

    private companion object {
        const val NOW_UTC = 1_800_000_000_000L
        const val NOW_ELAPSED = 90_000_000_000L
    }
}
