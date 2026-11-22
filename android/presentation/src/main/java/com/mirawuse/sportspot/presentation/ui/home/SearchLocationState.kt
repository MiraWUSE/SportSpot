package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.presentation.R

enum class LocationSource { DEFAULT, DEVICE, APPROXIMATE }

data class SearchLocationState(
    val center: GeoPoint = spotSearchCenter,
    val source: LocationSource = LocationSource.DEFAULT,
    val locating: Boolean = false,
    val message: Int? = null,
) {
    val labelRes: Int
        get() =
            when (source) {
                LocationSource.DEFAULT -> R.string.home_demo_location
                LocationSource.DEVICE -> R.string.location_current
                LocationSource.APPROXIMATE -> R.string.location_approximate
            }
}
