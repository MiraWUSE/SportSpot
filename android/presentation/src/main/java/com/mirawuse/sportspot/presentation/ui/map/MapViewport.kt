package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MapSearchRequest(
    val center: GeoPoint,
    val radiusMeters: Int,
)

internal data class MapViewport(
    val center: GeoPoint,
    val visibleRadius: Double,
    // A view of a region or country is zoomed out too far for a meaningful local search.
    val request: MapSearchRequest?,
    val limited: Boolean,
) {
    fun isSimilarTo(other: MapViewport): Boolean =
        limited == other.limited &&
            (request == null) == (other.request == null) &&
            distanceMeters(center, other.center) < maxOf(50.0, minOf(visibleRadius, other.visibleRadius, 3000.0) * 0.12) &&
            abs(visibleRadius - other.visibleRadius) < maxOf(50.0, minOf(visibleRadius, other.visibleRadius) * 0.15)
}

internal fun mapViewport(
    center: GeoPoint,
    corners: List<GeoPoint>,
    zoom: Double,
): MapViewport? {
    if (!isMapCoordinate(center.latitude, center.longitude) || !zoom.isFinite() || corners.size != 4) return null
    if (zoom < 8.0) return MapViewport(center, 12_000.0, request = null, limited = true)

    val visibleRadius = corners.maxOf { distanceMeters(center, it) }
    // Nearby visits reuse the same Room key. Measure from the snapped center too, so
    // rounding the center does not leave a gap at the edge of the viewport.
    val requestCenter =
        GeoPoint(
            (round(center.latitude * 1000) / 1000).coerceIn(-85.051, 85.051),
            wrappedMapLongitude(round(center.longitude * 1000) / 1000),
        )
    val required = corners.maxOf { distanceMeters(requestCenter, it) }
    val request =
        if (required <= 12_000) {
            MapSearchRequest(requestCenter, (ceil(required / 100) * 100).toInt().coerceIn(100, 3000))
        } else {
            null
        }
    return MapViewport(center, visibleRadius, request, limited = required > 3000)
}

internal fun wrappedMapLongitude(longitude: Double): Double =
    if (longitude >= -180.0 && longitude < 180.0) longitude else ((longitude + 180) % 360 + 360) % 360 - 180

private fun distanceMeters(
    first: GeoPoint,
    second: GeoPoint,
): Double {
    val latitude = sin(Math.toRadians(second.latitude - first.latitude) / 2)
    val longitude = sin(Math.toRadians(wrappedMapLongitude(second.longitude - first.longitude)) / 2)
    val haversine =
        latitude * latitude + cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) * longitude * longitude
    return 2 * 6_371_000 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}
