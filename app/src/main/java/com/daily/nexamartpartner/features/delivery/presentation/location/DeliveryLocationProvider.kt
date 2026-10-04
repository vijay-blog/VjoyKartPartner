package com.daily.nexamartpartner.features.delivery.presentation.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Gets a fresh-enough device location for delivery actions such as accepting an order. */
class DeliveryLocationProvider(private val context: Context) {
    fun getCurrentLocation(onResult: (Location?) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onResult(null)
            return
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            onResult(null)
            return
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            onResult(null)
            return
        }

        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (delivered.compareAndSet(false, true)) {
                    cleanup(manager, providers, this, handler)
                    onResult(location)
                }
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Android API 29") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        var requested = false
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, 1_000L, 5f, listener, Looper.getMainLooper())
                requested = true
            }
        }

        // A recent cached location is better than blocking the accept flow when GPS is slow.
        val cached = providers.asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time <= 120_000L }
            .maxByOrNull { it.time }
        if (cached != null && delivered.compareAndSet(false, true)) {
            cleanup(manager, providers, listener, handler)
            onResult(cached)
            return
        }

        if (!requested) {
            onResult(null)
            return
        }

        handler.postDelayed({
            if (delivered.compareAndSet(false, true)) {
                cleanup(manager, providers, listener, handler)
                onResult(null)
            }
        }, 10_000L)
    }

    private fun cleanup(manager: LocationManager, providers: List<String>, listener: LocationListener, handler: Handler) {
        providers.forEach { runCatching { manager.removeUpdates(listener) } }
        handler.removeCallbacksAndMessages(null)
    }
}
