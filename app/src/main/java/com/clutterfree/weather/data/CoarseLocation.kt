package com.clutterfree.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One approximate fix. Nothing here is written to disk or sent off the device. */
class CoarseLocation(private val context: Context) {
    fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun read(): Pair<Double, Double>? {
        if (!permitted()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = providers()
        for (provider in providers) {
            last(manager, provider)?.let { return it.latitude to it.longitude }
        }
        for (provider in providers) {
            current(manager, provider)?.let { return it.latitude to it.longitude }
        }
        return null
    }

    private fun providers(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }

    private fun last(manager: LocationManager, provider: String): Location? =
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()

    private suspend fun current(manager: LocationManager, provider: String): Location? {
        if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) return null
        return withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    val started = runCatching {
                        manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
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

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 12_000L
    }
}
