package com.roadar.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class DeviceLocationFix(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Float,
    val fixAtUtcMillis: Long,
    val fixElapsedRealtimeNanos: Long
)

class LocationFixException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface DeviceLocationProvider {
    suspend fun getRecentLocation(): DeviceLocationFix
}

class FusedDeviceLocationProvider(context: Context) : DeviceLocationProvider {
    private val applicationContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(applicationContext)

    override suspend fun getRecentLocation(): DeviceLocationFix =
        suspendCancellableCoroutine { continuation ->
            val hasFinePermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarsePermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFinePermission && !hasCoarsePermission) {
                continuation.resumeWithException(
                    LocationFixException("Allow approximate or precise location to save a report.")
                )
                return@suspendCancellableCoroutine
            }

            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (!continuation.isActive) return@addOnSuccessListener
                        if (location == null) {
                            continuation.resumeWithException(
                                LocationFixException(
                                    "A recent location is unavailable. Check location services and retry."
                                )
                            )
                            return@addOnSuccessListener
                        }
                        if (!location.hasAccuracy()) {
                            continuation.resumeWithException(
                                LocationFixException(
                                    "The location provider did not return an accuracy estimate. Please retry."
                                )
                            )
                            return@addOnSuccessListener
                        }

                        try {
                            continuation.resume(location.toValidatedFix())
                        } catch (exception: LocationFixException) {
                            continuation.resumeWithException(exception)
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                LocationFixException(
                                    "Location could not be obtained. Check location services and retry.",
                                    exception
                                )
                            )
                        }
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) {
                            continuation.cancel()
                        }
                    }
            } catch (exception: SecurityException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        LocationFixException(
                            "Location permission changed. Allow location access and retry.",
                            exception
                        )
                    )
                }
            }
        }

    private fun Location.toValidatedFix(): DeviceLocationFix {
        val fix = DeviceLocationFix(
            latitude = latitude,
            longitude = longitude,
            horizontalAccuracyMeters = accuracy,
            fixAtUtcMillis = time,
            fixElapsedRealtimeNanos = elapsedRealtimeNanos
        )
        return LocationFixValidator.validate(
            fix = fix,
            nowUtcMillis = System.currentTimeMillis(),
            nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        )
    }
}

object LocationFixValidator {
    const val MAX_FIX_AGE_MILLIS = 30_000L
    const val MAX_HORIZONTAL_ACCURACY_METERS = 1_000f
    private const val MAX_CLOCK_SKEW_MILLIS = 5_000L

    fun validate(
        fix: DeviceLocationFix,
        nowUtcMillis: Long,
        nowElapsedRealtimeNanos: Long
    ): DeviceLocationFix {
        if (!fix.latitude.isFinite() || fix.latitude !in -90.0..90.0 ||
            !fix.longitude.isFinite() || fix.longitude !in -180.0..180.0
        ) {
            throw LocationFixException("The location fix has invalid coordinates.")
        }
        if (!fix.horizontalAccuracyMeters.isFinite() ||
            fix.horizontalAccuracyMeters !in 0f..MAX_HORIZONTAL_ACCURACY_METERS
        ) {
            throw LocationFixException(
                "Location accuracy is too low to save a useful report. Retry when the GPS signal improves."
            )
        }
        val ageUtcMillis = nowUtcMillis - fix.fixAtUtcMillis
        if (fix.fixAtUtcMillis <= 0L || ageUtcMillis < -MAX_CLOCK_SKEW_MILLIS ||
            ageUtcMillis > MAX_FIX_AGE_MILLIS
        ) {
            throw LocationFixException("The location fix has an invalid timestamp. Please retry.")
        }
        val ageNanos = nowElapsedRealtimeNanos - fix.fixElapsedRealtimeNanos
        if (fix.fixElapsedRealtimeNanos <= 0L || ageNanos < 0L ||
            ageNanos > MAX_FIX_AGE_MILLIS * 1_000_000L
        ) {
            throw LocationFixException("The location fix is stale. Please retry for a fresh location.")
        }
        return fix
    }
}
