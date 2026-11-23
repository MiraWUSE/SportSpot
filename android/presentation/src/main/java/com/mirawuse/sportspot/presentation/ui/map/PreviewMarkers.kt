package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import kotlin.math.cos

internal const val MAX_PREVIEW_MARKERS = 8

internal fun previewMapSpots(
    spots: List<Spot>,
    center: GeoPoint,
): List<Spot> {
    val remaining =
        spots
            .filter { isMapCoordinate(it.location.latitude, it.location.longitude) }
            .sortedWith(compareBy({ it.location.latitude }, { it.location.longitude }, { it.id.source }, { it.id.value }))
            .distinctBy { it.location }
            .toMutableList()
    if (remaining.size <= MAX_PREVIEW_MARKERS) return remaining

    val longitudeScale = cos(Math.toRadians(center.latitude))

    fun distanceSquared(
        first: GeoPoint,
        second: GeoPoint,
    ): Double {
        val north = first.latitude - second.latitude
        val east = ((first.longitude - second.longitude + 540) % 360 - 180) * longitudeScale
        return north * north + east * east
    }

    // Start near the search center, then cover areas furthest from the chosen points.
    // This only selects existing coordinates; MapLibre still projects every marker.
    val selected = mutableListOf(remaining.minBy { distanceSquared(it.location, center) })
    remaining.remove(selected.first())
    while (selected.size < MAX_PREVIEW_MARKERS && remaining.isNotEmpty()) {
        val next =
            remaining.maxBy { candidate ->
                selected.minOf { distanceSquared(candidate.location, it.location) }
            }
        selected.add(next)
        remaining.remove(next)
    }
    return selected
}
