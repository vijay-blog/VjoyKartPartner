package com.daily.nexamartpartner.features.delivery.presentation.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.atomic.AtomicBoolean

/** Gets a fresh-enough device location for delivery actions such as accepting an order. */
class DeliveryLocationProvider(private val context: Context) {
    fun getCurrentLocation(onResult: (Location?) -> Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onResult(null)
            return
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        if (manager == null || !isLocationEnabled(manager)) {
            onResult(null)
            return
        }

        val delivered = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val client = LocationServices.getFusedLocationProviderClient(context)
        fun finish(location: Location?) {
            if (delivered.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
                onResult(location?.takeIf(::isUsable))
            }
        }
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { current ->
                    if (isUsable(current)) finish(current)
                    else client.lastLocation
                        .addOnSuccessListener { cached -> finish(cached?.takeIf(::isUsable)) }
                        .addOnFailureListener { finish(null) }
                }
                .addOnFailureListener {
                    client.lastLocation
                        .addOnSuccessListener { cached -> finish(cached?.takeIf(::isUsable)) }
                        .addOnFailureListener { finish(null) }
                }
        } catch (_: SecurityException) {
            finish(null)
        }
        handler.postDelayed({
            finish(null)
        }, LOCATION_TIMEOUT_MS)
    }

    private fun isLocationEnabled(manager: android.location.LocationManager): Boolean =
        runCatching {
            manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

    private fun isUsable(location: Location?): Boolean {
        if (location == null || !location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        return System.currentTimeMillis() - location.time <= CACHE_MAX_AGE_MS
    }

    companion object {
        private const val LOCATION_TIMEOUT_MS = 20_000L
        private const val CACHE_MAX_AGE_MS = 120_000L
    }
}
