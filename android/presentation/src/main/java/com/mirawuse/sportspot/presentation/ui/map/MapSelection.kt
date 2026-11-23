package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

// OSM raster tiles use Web Mercator, which does not represent the poles.
internal fun isMapCoordinate(
    latitude: Double,
    longitude: Double,
): Boolean = latitude.isFinite() && longitude.isFinite() && latitude in -85.05112878..85.05112878 && longitude in -180.0..180.0

internal fun mapMarkerGroups(spots: List<Spot>): Map<GeoPoint, List<Spot>> =
    spots.filter { isMapCoordinate(it.location.latitude, it.location.longitude) }.groupBy { it.location }

internal fun selectedMapSpot(
    spots: List<Spot>,
    selectedId: SpotId?,
): Spot? = spots.firstOrNull { it.id == selectedId } ?: spots.firstOrNull()

internal fun nextMapSpot(
    spots: List<Spot>,
    markerId: SpotId,
    selectedId: SpotId?,
): SpotId? {
    val marker = spots.firstOrNull { it.id == markerId } ?: return null
    val group = spots.filter { it.location == marker.location }
    val current = group.indexOfFirst { it.id == selectedId }
    return group[(current + 1) % group.size].id
}

internal fun mapCenterKey(center: GeoPoint): String = "${center.latitude}:${center.longitude}"

internal fun mapFocusKey(focus: Spot?): String? = focus?.let { "${it.id.source}|${it.id.value}|${mapCenterKey(it.location)}" }

internal data class MapCameraTarget(
    val point: GeoPoint,
    val zoom: Double,
)

internal fun mapCameraTarget(
    center: GeoPoint,
    focus: Spot?,
    previousCenter: String?,
    previousFocus: String?,
    hasSavedCamera: Boolean,
    overviewZoom: Double = 13.5,
): MapCameraTarget? {
    if (previousCenter != null && previousCenter != mapCenterKey(center)) return MapCameraTarget(center, overviewZoom)
    if (hasSavedCamera && previousFocus == mapFocusKey(focus)) return null
    val target = focus?.location?.takeIf { isMapCoordinate(it.latitude, it.longitude) }
    return MapCameraTarget(target ?: center, if (target != null) 15.5 else overviewZoom)
}
