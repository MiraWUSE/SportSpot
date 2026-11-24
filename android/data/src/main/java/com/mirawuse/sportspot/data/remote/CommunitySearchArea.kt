package com.mirawuse.sportspot.data.remote

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

internal class CommunitySearchArea(
    request: SpotSearchRequest,
) {
    private val latitude = request.latitude.toDouble()
    private val longitude = request.longitude.toDouble()
    private val radius = request.radiusMeters / EARTH_RADIUS_METERS
    private val latitudeDelta = Math.toDegrees(radius)
    private val south = (latitude - latitudeDelta).coerceAtLeast(-90.0)
    private val north = (latitude + latitudeDelta).coerceAtMost(90.0)
    private val longitudeDelta =
        if (south == -90.0 || north == 90.0) 180.0 else Math.toDegrees(asin(sin(radius) / cos(Math.toRadians(latitude))))
    private val west = longitude - longitudeDelta
    private val east = longitude + longitudeDelta

    val bounds: String =
        if (longitudeDelta == 180.0 || west <= -180.0 || east >= 180.0) {
            "(latitude.gte.$south,latitude.lte.$north)"
        } else {
            "(latitude.gte.$south,latitude.lte.$north,longitude.gte.$west,longitude.lte.$east)"
        }

    // PostgREST combines the latitude AND group with this OR group across the antimeridian.
    val wrappedLongitudes: String? =
        when {
            longitudeDelta == 180.0 -> null
            west <= -180.0 -> "(longitude.gte.${west + 360.0},longitude.lte.$east)"
            east >= 180.0 -> "(longitude.gte.$west,longitude.lte.${east - 360.0})"
            else -> null
        }

    fun contains(point: GeoPoint): Boolean {
        val stepLatitude = sin(Math.toRadians(point.latitude - latitude) / 2.0)
        val stepLongitude = sin(Math.toRadians(point.longitude - longitude) / 2.0)
        val haversine =
            stepLatitude * stepLatitude +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(point.latitude)) * stepLongitude * stepLongitude
        return 2.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0))) <= radius
    }
}
