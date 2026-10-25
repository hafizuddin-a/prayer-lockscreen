package com.prayerwallpaper.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** What asking the phone for its position gave. */
sealed interface LocationResult {
    data class Found(val latitude: Double, val longitude: Double) : LocationResult

    /** Location is switched off in the phone's settings. */
    data object TurnedOff : LocationResult

    /** The phone could not work out where it is in time (indoors, no network). */
    data object NoFix : LocationResult

    data object NoPermission : LocationResult
}

/**
 * One approximate position, asked for once, only when the user taps "Use my location". It uses the
 * platform's LocationManager with the coarse permission: no Google Play services, no background
 * location, no updates kept running, and nothing leaves the phone. The position is used to pick the
 * nearest city from the bundled list and is not stored.
 */
object DeviceLocation {
    private const val TIMEOUT_MS = 15_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked in hasPermission() just below
    suspend fun current(context: Context): LocationResult {
        if (!hasPermission(context)) return LocationResult.NoPermission
        val manager = context.getSystemService(LocationManager::class.java) ?: return LocationResult.NoFix
        // The fused provider is the best coarse source; the network provider is the older route to the same thing.
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { provider ->
            try {
                manager.isProviderEnabled(provider)
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        if (providers.isEmpty()) return LocationResult.TurnedOff

        for (provider in providers) {
            val fresh = withTimeoutOrNull(TIMEOUT_MS) { fresh(manager, provider, ContextCompat.getMainExecutor(context)) }
            val location = fresh ?: try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
            if (location != null) return LocationResult.Found(location.latitude, location.longitude)
        }
        return LocationResult.NoFix
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(manager: LocationManager, provider: String, executor: Executor): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, signal, executor) { location -> if (continuation.isActive) continuation.resume(location) }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
}
