package com.mirawuse.sportspot.domain.model.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    fun distanceMetersTo(other: GeoPoint): Double {
        val longitudeDelta = other.longitude - longitude
        val wrappedDelta =
            if (longitudeDelta in -180.0..<180.0) longitudeDelta else ((longitudeDelta + 180) % 360 + 360) % 360 - 180
        val latitudeSine = sin(Math.toRadians(other.latitude - latitude) / 2)
        val longitudeSine = sin(Math.toRadians(wrappedDelta) / 2)
        val haversine =
            latitudeSine * latitudeSine +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) * longitudeSine * longitudeSine
        return 2 * 6_371_000 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
