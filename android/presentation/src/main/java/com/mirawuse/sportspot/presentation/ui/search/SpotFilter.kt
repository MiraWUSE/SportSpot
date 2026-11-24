package com.mirawuse.sportspot.presentation.ui.search

import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot

internal data class SearchFilterState(
    val query: String = "",
    val sport: SportType? = null,
)

internal fun filterSpots(
    spots: List<Spot>,
    filter: SearchFilterState,
    sportLabel: (Spot) -> String,
): List<Spot> {
    val query = filter.query.trim()
    val sport = filter.sport
    if (query.isEmpty() && sport == null) return spots
    return spots.filter { spot ->
        (sport == null || spot.supportsSport(sport)) &&
            (
                query.isEmpty() ||
                    spot.name.contains(query, ignoreCase = true) ||
                    spot.address?.contains(query, ignoreCase = true) == true ||
                    sportLabel(spot).contains(query, ignoreCase = true)
            )
    }
}
