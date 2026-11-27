package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

internal sealed interface MapSpotVisibility {
    data object Loading : MapSpotVisibility

    data object Moving : MapSpotVisibility

    data object Unavailable : MapSpotVisibility

    data class Ready(
        val ids: Set<SpotId>,
        val center: GeoPoint,
    ) : MapSpotVisibility
}

internal fun projectedSpotIds(
    spots: List<Spot>,
    size: IntSize,
    project: (GeoPoint) -> Offset,
): Set<SpotId> =
    buildSet {
        for (spot in spots) {
            if (isMapCoordinate(spot.location.latitude, spot.location.longitude) && project(spot.location).isInMap(size)) {
                add(spot.id)
            }
        }
    }

internal fun Offset.isInMap(size: IntSize): Boolean =
    size.width > 0 && size.height > 0 && x in 0f..size.width.toFloat() && y in 0f..size.height.toFloat()
