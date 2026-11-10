package com.mirawuse.sportspot.domain.model.location

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) {
            "Широта должна быть от -90 до 90"
        }

        require(longitude in -180.0..180.0) {
            "Долгота должна быть от -180 до 180"
        }
    }
}
