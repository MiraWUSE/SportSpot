package com.mirawuse.sportspot.presentation.ui.search

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import java.text.Collator
import java.util.Locale

internal enum class SpotSort { DEFAULT, NEAREST, NAME_ASC }

internal enum class LightingFilter { ALL, LIT, UNLIT, UNKNOWN }

internal enum class SurfaceFilter { ALL, RUBBER, HARD, GRASS, SAND, OTHER, UNKNOWN }

internal const val DEFAULT_SEARCH_RADIUS_METERS = 1000
internal val searchRadiusOptions = listOf(500, DEFAULT_SEARCH_RADIUS_METERS, 2000, 3000)

internal data class SearchFilterState(
    val query: String = "",
    val sport: SportType? = null,
    val sort: SpotSort = SpotSort.DEFAULT,
    val lighting: LightingFilter = LightingFilter.ALL,
    val surface: SurfaceFilter = SurfaceFilter.ALL,
    val radiusMeters: Int = DEFAULT_SEARCH_RADIUS_METERS,
)

internal fun validSearchRadius(radius: Int?): Int = radius?.takeIf { it in searchRadiusOptions } ?: DEFAULT_SEARCH_RADIUS_METERS

internal fun filterSpots(
    spots: List<Spot>,
    filter: SearchFilterState,
    sportLabel: (Spot) -> String,
): List<Spot> {
    val query = filter.query.trim()
    val sport = filter.sport
    if (query.isEmpty() && sport == null && filter.lighting == LightingFilter.ALL && filter.surface == SurfaceFilter.ALL) return spots
    return spots.filter { spot ->
        (sport == null || spot.supportsSport(sport)) &&
            filter.lighting.matches(spot.lighting) &&
            filter.surface.matches(spot.surface) &&
            spot.matchesQuery(query, sportLabel)
    }
}

internal fun SearchFilterState.withoutConflicts(
    spot: Spot,
    sportLabel: (Spot) -> String,
): SearchFilterState =
    copy(
        query = query.takeIf { spot.matchesQuery(it.trim(), sportLabel) }.orEmpty(),
        sport = sport?.takeIf(spot::supportsSport),
        lighting = lighting.takeIf { it.matches(spot.lighting) } ?: LightingFilter.ALL,
        surface = surface.takeIf { it.matches(spot.surface) } ?: SurfaceFilter.ALL,
    )

private fun Spot.matchesQuery(
    query: String,
    sportLabel: (Spot) -> String,
): Boolean =
    query.isEmpty() ||
        name.contains(query, ignoreCase = true) ||
        address?.contains(query, ignoreCase = true) == true ||
        sportLabel(this).contains(query, ignoreCase = true)

private fun LightingFilter.matches(lighting: Boolean?): Boolean =
    when (this) {
        LightingFilter.ALL -> true
        LightingFilter.LIT -> lighting == true
        LightingFilter.UNLIT -> lighting == false
        LightingFilter.UNKNOWN -> lighting == null
    }

private fun SurfaceFilter.matches(surface: String?): Boolean {
    if (this == SurfaceFilter.ALL) return true
    val groups = surface.orEmpty().split(';').mapNotNull { surfaceGroup(it.trim().lowercase(Locale.ROOT)) }
    return when (this) {
        SurfaceFilter.UNKNOWN -> groups.isEmpty()
        SurfaceFilter.OTHER -> groups.isNotEmpty() && groups.all { it == SurfaceFilter.OTHER }
        else -> this in groups
    }
}

private fun surfaceGroup(token: String): SurfaceFilter? =
    when (token) {
        "" -> null
        "rubber" -> SurfaceFilter.RUBBER
        "asphalt", "concrete", "paved", "concrete:lanes", "concrete:plates" -> SurfaceFilter.HARD
        "grass", "artificial_turf" -> SurfaceFilter.GRASS
        "sand" -> SurfaceFilter.SAND
        else -> SurfaceFilter.OTHER
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
