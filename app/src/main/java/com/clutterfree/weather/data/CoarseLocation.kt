package com.clutterfree.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One approximate fix from the network provider. Nothing here is written to disk. */
class CoarseLocation(private val context: Context) {
    fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun read(): Pair<Double, Double>? {
        if (!permitted()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = LocationManager.NETWORK_PROVIDER
        val location = current(manager, provider) ?: last(manager, provider)
        return location?.let { it.latitude to it.longitude }
    }

    private fun last(manager: LocationManager, provider: String): Location? =
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()

    private suspend fun current(manager: LocationManager, provider: String): Location? {
        if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) return null
        return suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val started = runCatching {
                    manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }.isSuccess
                if (!started && continuation.isActive) continuation.resume(null)
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                val started = runCatching {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }.isSuccess
                if (!started && continuation.isActive) continuation.resume(null)
            }
        }
    }
}
