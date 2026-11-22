package com.mirawuse.sportspot.data.hardware

import android.Manifest
import android.app.Application
import android.location.LocationManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDeviceLocationProviderTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val provider = AndroidDeviceLocationProvider(context)

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
            shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            assertNull(provider.getCurrentLocation())
        }
}
