package com.mirawuse.sportspot.data.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

class AndroidDeviceLocationProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceLocationProvider {
        @SuppressLint("MissingPermission")
        override suspend fun getCurrentLocation(): GeoPoint? {
            val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            return try {
                if (!LocationManagerCompat.isLocationEnabled(manager)) return null
                val providers =
                    buildList {
                        if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
                    }
                withTimeoutOrNull(20_000L) {
                    for (provider in providers) {
                        val point = withTimeoutOrNull(10_000L) { readPoint(manager, provider) }
                        if (point != null) return@withTimeoutOrNull point
                    }
                    null
                }
            } catch (_: SecurityException) {
                // A permission can be revoked between the check and the system call.
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        @SuppressLint("MissingPermission")
        private suspend fun readPoint(
            manager: LocationManager,
            provider: String,
        ): GeoPoint? =
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    val point =
                        location
                            ?.takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
                            ?.let { GeoPoint(it.latitude, it.longitude) }
                    if (continuation.isActive) continuation.resume(point)
                }
            }
    }
