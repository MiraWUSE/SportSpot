package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.SpotFilterEmpty
import com.mirawuse.sportspot.presentation.ui.search.SpotSearchBar
import com.mirawuse.sportspot.presentation.ui.search.filterSpots
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
    onSearchSport: (SportType?) -> Unit = {},
    onResetSearch: () -> Unit = {},
) {
    var selectedSource by rememberSaveable(focus, center) { mutableStateOf(focus?.source) }
    var selectedValue by rememberSaveable(focus, center) { mutableStateOf(focus?.value) }
    var selectionFocusRequest by rememberSaveable(focus, center) { mutableIntStateOf(0) }
    val source = selectedSource
    val value = selectedValue
    val selectedId = if (source != null && value != null) SpotId(source, value) else null
    val initial = state as? HomeUiState.Content
    val availableSpots = viewport?.spots ?: initial?.spots.orEmpty()
    val resources = LocalResources.current
    val spots = if (filter == null) availableSpots else filterSpots(availableSpots, filter) { it.sportLabel(resources::getString) }
    val visibleSpot = selectedMapSpot(spots, selectedId)
    if (selectedId != null && selectedId != visibleSpot?.id && availableSpots.any { it.id == selectedId }) {
        LaunchedEffect(selectedId, visibleSpot?.id) {
            selectedSource = visibleSpot?.id?.source
            selectedValue = visibleSpot?.id?.value
        }
    }
    val focusedSpot = focus?.let { initial?.findSpot(it) }
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
        availableSpots.isNotEmpty() &&
            spots.isEmpty() &&
            (status == MapSearchStatus.READY || status == MapSearchStatus.INITIAL && initial != null)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var headingHeight by remember { mutableIntStateOf(0) }
        val headingDp = with(LocalDensity.current) { headingHeight.toDp() }
        val mapHeight =
            minOf(
                maxWidth - SportSpotSpacing.lg * 2,
                (maxHeight - headingDp - SportSpotSpacing.lg * 2 - SportSpotSpacing.md).coerceAtLeast(48.dp),
            )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
        ) {
            Column(Modifier.onSizeChanged { headingHeight = it.height }, verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md)) {
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
                Text(locationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (filter != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
                        SpotSearchBar(filter, onSearchQuery, onSearchSport)
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
                    SpotPickerButton(spots, visibleSpot?.id) { id ->
                        selectedSource = id.source
                        selectedValue = id.value
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
                            if (spots.isNotEmpty() || noMatches) {
                                Text(stringResource(R.string.home_results_count, spots.size), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            OsmMap(
                center = center,
                spots = spots,
                selectedId = visibleSpot?.id,
                focus = focusedSpot,
                isInteractive = true,
                selectionFocusRequest = selectionFocusRequest,
                modifier = Modifier.fillMaxWidth().height(mapHeight).clip(MaterialTheme.shapes.large),
                onSelect = { id ->
                    selectedSource = id.source
                    selectedValue = id.value
                },
                onViewportMoveStarted = onViewportMoveStarted,
                onViewportIdle = onViewportIdle,
            ) {
                val message =
                    when {
                        error != null -> error
                        status == MapSearchStatus.RATE_LIMITED -> stringResource(R.string.map_search_rate_limited)
                        status == MapSearchStatus.WAITING -> stringResource(R.string.map_search_waiting)
                        loading -> stringResource(R.string.map_search_loading)
                        viewport?.limited == true -> stringResource(R.string.map_search_zoom_in)
                        empty -> stringResource(R.string.map_search_empty)
                        else -> null
                    }
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
                SpotFilterEmpty(stringResource(R.string.map_no_matches), onResetSearch)
            }
            if (focus != null && selectedId == focus && availableSpots.isNotEmpty() && availableSpots.none { it.id == focus }) {
                Text(stringResource(R.string.map_focus_unavailable))
            }
            if (visibleSpot != null) {
                SelectedSpotCard(visibleSpot, spots, onOpenSpot)
            }
        }
    }
}

@Composable
private fun SelectedSpotCard(
    spot: Spot,
    spots: List<Spot>,
    onOpenSpot: (SpotId) -> Unit,
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
        }
    }
}
