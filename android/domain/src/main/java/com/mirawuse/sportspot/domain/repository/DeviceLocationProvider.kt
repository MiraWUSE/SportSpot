package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.location.GeoPoint

interface DeviceLocationProvider {
    /** Returns a current point, or null when access or a location fix is unavailable. */
    suspend fun getCurrentLocation(): GeoPoint?
}
