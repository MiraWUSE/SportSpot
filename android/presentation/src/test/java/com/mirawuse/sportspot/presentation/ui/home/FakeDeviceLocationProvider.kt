package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider

internal class FakeDeviceLocationProvider(
    private val read: suspend () -> GeoPoint? = { null },
) : DeviceLocationProvider {
    var calls = 0
        private set

    override suspend fun getCurrentLocation(): GeoPoint? {
        calls++
        return read()
    }
}
