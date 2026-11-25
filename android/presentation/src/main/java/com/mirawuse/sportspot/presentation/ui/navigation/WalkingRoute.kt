package com.mirawuse.sportspot.presentation.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import java.net.URLEncoder

internal fun walkingRouteUrl(destination: GeoPoint): String {
    val latitude = destination.latitude.toBigDecimal().toPlainString()
    val longitude = destination.longitude.toBigDecimal().toPlainString()
    val coordinates = URLEncoder.encode("$latitude,$longitude", Charsets.UTF_8.name())
    return "https://www.google.com/maps/dir/?api=1&destination=$coordinates&travelmode=walking&dir_action=navigate"
}

internal class WalkingRouteLauncher(
    private val startActivity: (Intent) -> Unit,
    private val onUnavailable: () -> Unit,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private var lastAttempt: Long? = null

    fun open(destination: GeoPoint) {
        val now = elapsedMillis()
        if (lastAttempt?.let { now - it < MIN_OPEN_INTERVAL_MILLIS } == true) return
        lastAttempt = now

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(walkingRouteUrl(destination))))
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
        } catch (_: SecurityException) {
            onUnavailable()
        }
    }

    private companion object {
        const val MIN_OPEN_INTERVAL_MILLIS = 700L
    }
}
