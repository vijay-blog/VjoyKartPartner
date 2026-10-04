package com.daily.nexamartpartner.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

/** Requests the delivery partner permissions once on first install/first launch. */
object FirstLaunchPermissionManager {
    private const val PREFS = "vjoykart_permissions"
    private const val REQUESTED_KEY = "first_launch_permissions_requested"
    const val REQUEST_CODE = 4207

    fun requestIfFirstLaunch(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(REQUESTED_KEY, false)) return

        val requested = buildRequestedPermissions()
        val missing = requested.filter {
            ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        // Mark before requesting so a denial does not cause an annoying loop on every resume.
        prefs.edit().putBoolean(REQUESTED_KEY, true).apply()
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing, REQUEST_CODE)
        }
    }

    private fun buildRequestedPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        add(Manifest.permission.READ_PHONE_STATE)
    }
}
