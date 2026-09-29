package com.daily.nexamartpartner.features.delivery.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.daily.nexamartpartner.MainActivity
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.delivery.availability.domain.model.DeliveryAvailabilityUpdate
import com.daily.nexamartpartner.features.delivery.notifications.domain.model.DeliveryNotificationQuery
import com.daily.nexamartpartner.core.result.AppResult
import kotlinx.coroutines.*

/**
 * Keeps the delivery partner's location fresh while they are online and posts
 * local notifications for nearby order assignments received from the backend.
 * The backend remains the source of truth for radius matching and acceptance.
 */
class DeliveryLocationForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private var lastLocationSentAt = 0L
    private var started = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!started) return
            val now = System.currentTimeMillis()
            // Send at most once every 25 seconds even if the OS delivers more fixes.
            if (now - lastLocationSentAt < LOCATION_SEND_INTERVAL_MS) return
            lastLocationSentAt = now
            scope.launch { sendLocation(location) }
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated by Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, foregroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasLocationPermission() || !isOnlineRequested()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        started = true
        requestLocationUpdates()
        // A partner can receive the assignment while the app is in the background.
        scope.launch { notificationLoop() }
    }

    private fun requestLocationUpdates() {
        if (!hasLocationPermission()) return
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        runCatching {
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    20_000L,
                    50f,
                    locationListener,
                    mainLooper
                )
            }
        }
        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    20_000L,
                    100f,
                    locationListener,
                    mainLooper
                )
            }
        }
        // Push the last known position immediately so a newly-online partner
        // becomes eligible without waiting for the first GPS callback.
        runCatching {
            val lastGps = if (fine) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val last = listOfNotNull(lastGps, lastNetwork).maxByOrNull { it.time }
            if (last != null) {
                lastLocationSentAt = System.currentTimeMillis()
                scope.launch { sendLocation(last) }
            }
        }
    }

    private suspend fun sendLocation(location: Location) {
        val result = applicationContext.appContainer.provideUpdateDeliveryAvailabilityUseCase()(
            DeliveryAvailabilityUpdate(
                available = true,
                latitude = location.latitude,
                longitude = location.longitude
            )
        )
        if (result is AppResult.Failure && result.error.type.name == "UNAUTHORIZED") {
            stopTracking()
            stopSelf()
        }
    }

    private suspend fun notificationLoop() {
        val seen = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getStringSet(KEY_SEEN_NOTIFICATION_IDS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

        while (isActive && started) {
            try {
                when (val result = applicationContext.appContainer.provideGetDeliveryNotificationsUseCase()(
                    DeliveryNotificationQuery(page = 0, pageSize = 20, unreadOnly = true)
                )) {
                    is AppResult.Success -> {
                                    result.data.items
                            .asSequence()
                            .filter { it.type.equals("ORDER_ASSIGNED", ignoreCase = true) }
                            .filter { !it.id.isNullOrBlank() && !seen.contains(it.id) }
                            // Unread notifications are the server-side source of truth.
                            // IDs are persisted locally so a service restart does not repeatedly
                            // alert for the same order.
                            .forEach { notification ->
                                showOrderNotification(notification.title, notification.message, notification.orderId)
                                notification.id.takeIf { it.isNotBlank() }?.let { seen.add(it) }
                            }
                        while (seen.size > 100) seen.remove(seen.first())
                        getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit()
                            .putStringSet(KEY_SEEN_NOTIFICATION_IDS, seen)
                            .apply()
                    }
                    is AppResult.Failure -> if (result.error.type.name == "UNAUTHORIZED") {
                        stopTracking()
                        stopSelf()
                        return
                    }
                }
            } catch (_: Throwable) {
                // Location/notification heartbeat is best effort. The app UI still
                // exposes the server-backed notification list.
            }
            delay(NOTIFICATION_POLL_INTERVAL_MS)
        }
    }

    private fun showOrderNotification(title: String, message: String, orderId: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId?.hashCode() ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ORDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "New delivery order" })
            .setContentText(message.ifBlank { "A delivery order is available nearby." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(orderId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }

    private fun foregroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VJoyKart delivery tracking")
            .setContentText("Your delivery availability is online. Location is being shared for nearby orders.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL_ID,
                "Delivery tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when delivery availability is online." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ORDER_CHANNEL_ID,
                "Nearby delivery orders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts for delivery orders within the active radius." }
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isOnlineRequested(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ONLINE_REQUESTED, false)

    private fun stopTracking() {
        started = false
        runCatching { locationManager.removeUpdates(locationListener) }
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP = "com.daily.nexamartpartner.delivery.STOP_LOCATION"
        private const val NOTIFICATION_ID = 9001
        private const val TRACKING_CHANNEL_ID = "delivery_tracking"
        private const val ORDER_CHANNEL_ID = "delivery_orders"
        private const val PREFS = "delivery_location_service"
        private const val KEY_SEEN_NOTIFICATION_IDS = "seen_notification_ids"
        private const val KEY_ONLINE_REQUESTED = "online_requested"
        private const val LOCATION_SEND_INTERVAL_MS = 25_000L
        private const val NOTIFICATION_POLL_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ONLINE_REQUESTED, true).apply()
            val intent = Intent(context, DeliveryLocationForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ONLINE_REQUESTED, false).apply()
            val intent = Intent(context, DeliveryLocationForegroundService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
