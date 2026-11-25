package com.mirawuse.sportspot.presentation.ui.search

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import java.text.Collator
import java.util.Locale

internal enum class SpotSort { DEFAULT, NEAREST, NAME_ASC }

internal data class SearchFilterState(
    val query: String = "",
    val sport: SportType? = null,
    val sort: SpotSort = SpotSort.DEFAULT,
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

internal fun sortSpots(
    spots: List<Spot>,
    sort: SpotSort,
    center: GeoPoint,
): List<Spot> =
    when (sort) {
        SpotSort.DEFAULT -> spots
        SpotSort.NEAREST ->
            spots
                .map { it to center.distanceMetersTo(it.location) }
                .sortedWith(compareBy<Pair<Spot, Double>> { it.second }.thenBy { it.first.id.source }.thenBy { it.first.id.value })
                .map { it.first }
        SpotSort.NAME_ASC -> {
            val collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.SECONDARY }
            spots.sortedWith(compareBy(collator, Spot::name).thenBy { it.id.source }.thenBy { it.id.value })
        }
    }
