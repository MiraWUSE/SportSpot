package com.mirawuse.sportspot.presentation.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Null is local work in progress. Keep this distinct from a completed empty list.
@Composable
internal fun rememberFilteredSpots(
    spots: List<Spot>,
    filter: SearchFilterState,
): List<Spot>? {
    val criteria = filter.filteringCriteria()
    if (spots.isEmpty() || criteria == SearchFilterState()) return spots
    val resources = LocalResources.current
    val locales = LocalConfiguration.current.locales
    val result = remember(spots, criteria, resources, locales) { mutableStateOf<List<Spot>?>(null) }
    LaunchedEffect(result) {
        result.value = withContext(Dispatchers.Default) { filterSpots(spots, criteria) { it.sportLabel(resources::getString) } }
    }
    return result.value
}

@Composable
internal fun rememberSortedSpots(
    spots: List<Spot>,
    sort: SpotSort,
    center: GeoPoint,
): List<Spot>? {
    if (sort == SpotSort.DEFAULT || spots.size < 2) return spots
    val origin = center.takeIf { sort == SpotSort.NEAREST }
    val result = remember(spots, sort, origin) { mutableStateOf<List<Spot>?>(null) }
    LaunchedEffect(result) {
        result.value = withContext(Dispatchers.Default) { sortSpots(spots, sort, center) }
    }
    return result.value
}
