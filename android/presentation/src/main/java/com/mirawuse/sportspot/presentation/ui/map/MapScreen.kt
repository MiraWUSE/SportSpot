package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun MapScreen(
    state: HomeUiState,
    center: GeoPoint,
    locationLabel: String,
    focus: SpotId?,
    onRetry: () -> Unit,
    onOpenSpot: (SpotId) -> Unit,
) {
    var selectedSource by rememberSaveable(focus, center) { mutableStateOf(focus?.source) }
    var selectedValue by rememberSaveable(focus, center) { mutableStateOf(focus?.value) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var headingHeight by remember { mutableIntStateOf(0) }
        val headingDp = with(LocalDensity.current) { headingHeight.toDp() }
        // Keep the entire map and attribution visible in landscape as well.
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
                Text(text = stringResource(R.string.tab_map), style = MaterialTheme.typography.headlineLarge)
                Text(locationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            when (state) {
                HomeUiState.Loading -> SpotSearchLoading()
                HomeUiState.Empty ->
                    SpotSearchMessage(
                        stringResource(R.string.home_empty_title),
                        stringResource(R.string.home_empty_message),
                        onRetry,
                    )
                is HomeUiState.Error -> SpotSearchMessage(stringResource(R.string.home_error_title), state.message, onRetry)
                is HomeUiState.Content -> {
                    val source = selectedSource
                    val value = selectedValue
                    val selectedId = if (source != null && value != null) SpotId(source, value) else null
                    val visibleSpot = selectedMapSpot(state.spots, selectedId)
                    val focusedSpot = focus?.let(state::findSpot)
                    if (focus != null && focusedSpot == null) Text(stringResource(R.string.map_focus_unavailable))
                    OsmMap(
                        center = center,
                        spots = state.spots,
                        selectedId = visibleSpot?.id,
                        focus = focusedSpot,
                        isInteractive = true,
                        modifier = Modifier.fillMaxWidth().height(mapHeight).clip(MaterialTheme.shapes.large),
                        onSelect = { id ->
                            selectedSource = id.source
                            selectedValue = id.value
                        },
                    )
                    if (visibleSpot != null) {
                        val sharedCount = state.spots.count { it.location == visibleSpot.location }
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
                                SpotSummary(visibleSpot)
                                SportSpotButton(
                                    text = stringResource(R.string.map_details),
                                    onClick = { onOpenSpot(visibleSpot.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        Text(stringResource(R.string.details_not_found_message))
                    }
                }
            }
        }
    }
}
