package com.daily.nexamartpartner.features.delivery.presentation.location

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens Google Maps safely. We deliberately do not force the Google Maps package:
 * the user may have Maps disabled, unavailable, or use another maps application.
 */
object DeliveryNavigationHelper {
    fun openDestination(context: Context, address: String): Boolean {
        val clean = address.trim()
        if (clean.isEmpty()) return false

        val googleNavigation = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(clean)}&travelmode=driving"
        )
        return startView(context, googleNavigation)
            || startView(context, Uri.parse("geo:0,0?q=${Uri.encode(clean)}"))
            || startView(context, googleNavigation, forceBrowser = true)
    }

    fun openMapsSearch(context: Context, address: String): Boolean {
        val clean = address.trim()
        if (clean.isEmpty()) return false
        val search = Uri.parse(
            "https://www.google.com/maps/search/?api=1&query=${Uri.encode(clean)}"
        )
        return startView(context, search)
            || startView(context, Uri.parse("geo:0,0?q=${Uri.encode(clean)}"))
            || startView(context, search, forceBrowser = true)
    }

    private fun startView(context: Context, uri: Uri, forceBrowser: Boolean = false): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                if (forceBrowser) addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) return false
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
