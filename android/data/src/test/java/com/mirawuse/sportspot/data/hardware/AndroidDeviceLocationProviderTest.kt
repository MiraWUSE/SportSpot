package com.mirawuse.sportspot.data.hardware

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDeviceLocationProviderTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val provider = AndroidDeviceLocationProvider(context)

    @Test
    fun `current callback maps to domain for precise and approximate access`() =
        runTest {
            for (source in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (source == LocationManager.GPS_PROVIDER) {
                    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, source == LocationManager.GPS_PROVIDER)
                shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, source == LocationManager.NETWORK_PROVIDER)
                val result = async(UnconfinedTestDispatcher(testScheduler)) { provider.getCurrentLocation() }
                val fix =
                    Location(source).apply {
                        latitude = 48.8566
                        longitude = 2.3522
                        accuracy = 50f
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                shadowOf(manager).simulateLocation(fix)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(GeoPoint(fix.latitude, fix.longitude), result.await())
            }
        }

    @Test
    fun `revoked access cannot deliver a point from a pending callback`() =
        runTest {
            for (source in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, source == LocationManager.GPS_PROVIDER)
                shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, source == LocationManager.NETWORK_PROVIDER)
                val result = async(UnconfinedTestDispatcher(testScheduler)) { provider.getCurrentLocation() }
                if (source == LocationManager.GPS_PROVIDER) {
                    shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                val fix =
                    Location(source).apply {
                        latitude = 48.8566
                        longitude = 2.3522
                        accuracy = 50f
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                shadowOf(manager).simulateLocation(fix)
                shadowOf(Looper.getMainLooper()).idle()
                assertNull(result.await())
            }
        }

    @Test
    fun `cancelling the coroutine removes the system request`() =
        runTest {
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            val result = async(UnconfinedTestDispatcher(testScheduler)) { provider.getCurrentLocation() }
            assertEquals(1, shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).size)
            result.cancelAndJoin()
            assertTrue(shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
        }

    @Test
    fun `missing permission returns no point`() =
        runTest {
            shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            assertNull(provider.getCurrentLocation())
        }

    @Test
    fun `disabled location returns no point`() =
        runTest {
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            shadowOf(manager).setLocationEnabled(false)
            assertNull(provider.getCurrentLocation())
        }

    @Test
    fun `approximate permission does not use gps when network provider is unavailable`() =
        runTest {
            shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            assertNull(provider.getCurrentLocation())
        }

    @Test
    fun `waiting for a fix is bounded`() =
        runTest {
            for (source in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (source == LocationManager.GPS_PROVIDER) {
                    shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, source == LocationManager.GPS_PROVIDER)
                shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, source == LocationManager.NETWORK_PROVIDER)
                assertNull(provider.getCurrentLocation())
                assertTrue(shadowOf(manager).getLocationRequests(source).isEmpty())
            }
        }
}
