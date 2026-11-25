package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.navigation.WalkingRouteButton
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.SpotFilterEmpty
import com.mirawuse.sportspot.presentation.ui.search.SpotSearchBar
import com.mirawuse.sportspot.presentation.ui.search.filteringCriteria
import com.mirawuse.sportspot.presentation.ui.search.rememberFilteredSpots
import com.mirawuse.sportspot.presentation.ui.search.rememberSortedSpots
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun MapScreen(
    state: HomeUiState,
    center: GeoPoint,
    locationLabel: String,
    focus: SpotId?,
    onRetry: () -> Unit,
    onOpenSpot: (SpotId) -> Unit,
    onRefresh: (() -> Unit)? = null,
    viewport: MapViewportState? = null,
    onViewportMoveStarted: (() -> Unit)? = null,
    onViewportIdle: ((MapViewport, MapViewport?) -> Unit)? = null,
    filter: SearchFilterState? = null,
    onSearchQuery: (String) -> Unit = {},
    onApplySearch: (SearchFilterState) -> Unit = {},
    onResetSearch: () -> Unit = {},
    showVisibleSpots: Boolean = false,
    onBuildRoute: ((GeoPoint) -> Unit)? = null,
) {
    var selectedSource by rememberSaveable(focus, center) { mutableStateOf(focus?.source) }
    var selectedValue by rememberSaveable(focus, center) { mutableStateOf(focus?.value) }
    var selectionFocusRequest by rememberSaveable(focus, center) { mutableIntStateOf(0) }
    val source = selectedSource
    val value = selectedValue
    val selectedId = if (source != null && value != null) SpotId(source, value) else null
    var visibility by remember(center, focus, showVisibleSpots) { mutableStateOf<MapSpotVisibility>(MapSpotVisibility.Loading) }
    var listExpanded by rememberSaveable { mutableStateOf(true) }
    val initial = state as? HomeUiState.Content
    val loadedSpots = viewport?.spots ?: initial?.spots.orEmpty()
    val focusedSpot = focus?.let { id -> loadedSpots.firstOrNull { it.id == id } ?: initial?.findSpot(id) }
    val availableSpots =
        remember(loadedSpots, focusedSpot) {
            if (focusedSpot != null && loadedSpots.none { it.id == focusedSpot.id }) listOf(focusedSpot) + loadedSpots else loadedSpots
        }
    val currentFilter = filter ?: SearchFilterState()
    val criteria = currentFilter.filteringCriteria()
    val entryCriteria = remember(focus, center) { criteria }
    val keepExplicitFocus = focusedSpot != null && selectedId == focus && criteria == entryCriteria
    val matched = rememberFilteredSpots(availableSpots, currentFilter)
    val spots =
        remember(matched, focusedSpot, keepExplicitFocus) {
            if (focusedSpot != null && keepExplicitFocus && matched.orEmpty().none { it.id == focus }) {
                listOf(focusedSpot) + matched.orEmpty()
            } else {
                matched.orEmpty()
            }
        }
    val visible = visibility as? MapSpotVisibility.Ready
    val visibleIds = visible?.ids
    val visibleSpots =
        remember(visibleIds, spots, matched != null) {
            if (matched == null) null else visibleIds?.let { ids -> spots.filter { it.id in ids }.distinctBy(Spot::id) }
        }
    val choices = if (showVisibleSpots) visibleSpots.orEmpty() else spots
    val sortingCenter = visible?.center ?: center
    val sortedChoices = rememberSortedSpots(choices, currentFilter.sort, sortingCenter)
    val selectedSpot = selectedMapSpot(visibleSpots ?: spots, selectedId)
    val canResolveSelection = matched != null && (!showVisibleSpots || visibleSpots != null)
    if (canResolveSelection && selectedId != null && spots.none { it.id == selectedId } && availableSpots.any { it.id == selectedId }) {
        LaunchedEffect(selectedId, selectedSpot?.id) {
            selectedSource = selectedSpot?.id?.source
            selectedValue = selectedSpot?.id?.value
        }
    }
    val selectSpot: (SpotId) -> Unit = { id ->
        selectedSource = id.source
        selectedValue = id.value
    }
    val status = viewport?.status ?: MapSearchStatus.INITIAL
    val loading = status == MapSearchStatus.LOADING || (status == MapSearchStatus.INITIAL && state == HomeUiState.Loading)
    val error =
        when {
            status == MapSearchStatus.ERROR -> stringResource(R.string.map_search_error)
            status == MapSearchStatus.INITIAL && state is HomeUiState.Error -> state.message
            else -> null
        }
    val empty =
        availableSpots.isEmpty() &&
            (status == MapSearchStatus.READY || status == MapSearchStatus.INITIAL && state == HomeUiState.Empty)
    val noMatches =
        matched != null &&
            (if (showVisibleSpots) !visibleIds.isNullOrEmpty() else availableSpots.isNotEmpty()) &&
            choices.isEmpty() &&
            (status == MapSearchStatus.READY || status == MapSearchStatus.INITIAL && initial != null)
    val searchMessage =
        when {
            error != null -> error
            status == MapSearchStatus.RATE_LIMITED -> stringResource(R.string.map_search_rate_limited)
            status == MapSearchStatus.WAITING -> stringResource(R.string.map_search_waiting)
            loading -> stringResource(R.string.map_search_loading)
            viewport?.limited == true || status == MapSearchStatus.ZOOM_IN -> stringResource(R.string.map_search_zoom_in)
            else -> null
        }
    val listMessage =
        if (showVisibleSpots) {
            when (visibility) {
                MapSpotVisibility.Loading -> stringResource(R.string.map_list_loading)
                MapSpotVisibility.Moving -> stringResource(R.string.map_list_updating)
                MapSpotVisibility.Unavailable -> stringResource(R.string.map_list_unavailable)
                is MapSpotVisibility.Ready ->
                    when {
                        searchMessage != null && !visibleSpots.isNullOrEmpty() && status != MapSearchStatus.READY ->
                            stringResource(R.string.map_list_previous, searchMessage)
                        searchMessage != null -> searchMessage
                        matched == null || sortedChoices == null -> stringResource(R.string.search_filters_updating)
                        empty -> stringResource(R.string.map_search_empty)
                        noMatches -> null
                        visibleSpots.isNullOrEmpty() -> stringResource(R.string.map_list_empty)
                        else -> null
                    }
            }
        } else {
            null
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var headingHeight by remember { mutableIntStateOf(0) }
        val headingDp = with(LocalDensity.current) { headingHeight.toDp() }
        val mapHeight =
            minOf(
                maxWidth - SportSpotSpacing.lg * 2,
                (maxHeight - headingDp - SportSpotSpacing.lg * 2 - SportSpotSpacing.md)
                    .coerceAtLeast(if (showVisibleSpots) 180.dp else 48.dp),
            )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
        ) {
            item(key = "map") {
                // Keep the native camera and projection alive when the list scrolls past the map.
                val container = LocalPinnableContainer.current
                DisposableEffect(container) {
                    val pin = container?.pin()
                    onDispose { pin?.release() }
                }
                Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md)) {
                    Column(
                        Modifier.onSizeChanged { headingHeight = it.height },
                        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.tab_map),
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (onRefresh != null) {
                                TextButton(onClick = onRefresh, enabled = !loading && status != MapSearchStatus.ZOOM_IN) {
                                    Text(stringResource(R.string.content_refresh))
                                }
                            }
                        }
                        Text(
                            locationLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (filter != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
                                SpotSearchBar(filter, onSearchQuery, onApplySearch, onResetSearch)
                                Text(
                                    stringResource(R.string.map_search_scope),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SpotPickerButton(sortedChoices.orEmpty(), selectedSpot?.id) { id ->
                                selectSpot(id)
                                selectionFocusRequest++
                            }
                            if (filter != null) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(filter.sport?.labelRes() ?: R.string.home_filter_all),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (choices.isNotEmpty() || noMatches) {
                                        Text(
                                            stringResource(R.string.home_results_count, choices.size),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OsmMap(
                        center = center,
                        spots = spots,
                        selectedId = selectedSpot?.id,
                        focus = focusedSpot,
                        isInteractive = true,
                        selectionFocusRequest = selectionFocusRequest,
                        selectionFocusId = selectedId,
                        modifier = Modifier.fillMaxWidth().height(mapHeight).clip(MaterialTheme.shapes.large),
                        onSelect = selectSpot,
                        onViewportMoveStarted = onViewportMoveStarted,
                        onViewportIdle = onViewportIdle,
                        onSpotVisibility = if (showVisibleSpots) ({ visibility = it }) else null,
                        visibilitySpots = availableSpots,
                    ) {
                        val message = searchMessage ?: if (empty) stringResource(R.string.map_search_empty) else null
                        if (message != null) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            ) {
                                Column {
                                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text(
                                        message,
                                        Modifier.padding(horizontal = SportSpotSpacing.sm, vertical = SportSpotSpacing.xs),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                    if (error != null || status == MapSearchStatus.RATE_LIMITED) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.map_search_retry)) }
                    }
                    if (noMatches) {
                        SpotFilterEmpty(stringResource(R.string.home_no_matches), onResetSearch)
                    }
                    if (focus != null && selectedId == focus && availableSpots.isNotEmpty() && availableSpots.none { it.id == focus }) {
                        Text(stringResource(R.string.map_focus_unavailable))
                    }
                    if (selectedSpot != null) {
                        SelectedSpotCard(selectedSpot, spots, onOpenSpot, onBuildRoute)
                    }
                }
            }
            if (showVisibleSpots) {
                item(key = "visible-spots") {
                    VisibleMapSpotsHeader(
                        count = visibleSpots?.size?.takeIf { it > 0 || searchMessage == null },
                        expanded = listExpanded,
                        onToggle = { listExpanded = !listExpanded },
                    )
                }
                if (listExpanded) {
                    if (listMessage != null) {
                        item(key = "visible-spots-status") {
                            Text(listMessage, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(sortedChoices.orEmpty(), key = { "${it.id.source.length}:${it.id.source}${it.id.value}" }) { spot ->
                        VisibleMapSpotRow(
                            spot = spot,
                            selected = selectedSpot?.id == spot.id,
                            onSelect = { selectSpot(spot.id) },
                            onOpen = { onOpenSpot(spot.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedSpotCard(
    spot: Spot,
    spots: List<Spot>,
    onOpenSpot: (SpotId) -> Unit,
    onBuildRoute: ((GeoPoint) -> Unit)?,
) {
    val sharedCount = spots.count { it.location == spot.location }
    if (sharedCount > 1) {
        Text(stringResource(R.string.map_shared_location, sharedCount), style = MaterialTheme.typography.bodySmall)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
        ) {
            SpotSummary(spot)
            SportSpotButton(
                text = stringResource(R.string.map_details),
                onClick = { onOpenSpot(spot.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (onBuildRoute != null) WalkingRouteButton { onBuildRoute(spot.location) }
        }
    }
}
