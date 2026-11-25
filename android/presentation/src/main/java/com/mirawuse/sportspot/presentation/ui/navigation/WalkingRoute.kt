package com.mirawuse.sportspot.presentation.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import java.net.URLEncoder

@Composable
internal fun WalkingRouteButton(onClick: () -> Unit) {
    SportSpotButton(
        text = stringResource(R.string.spot_build_route),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        variant = SportSpotButtonVariant.SECONDARY,
    )
}

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
