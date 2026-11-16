package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.home.SPOT_SEARCH_RADIUS_METERS
import com.mirawuse.sportspot.presentation.ui.home.spotSearchCenter
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import kotlin.math.cos

@Composable
internal fun MapScreen(
    state: HomeUiState,
    focus: SpotId?,
    onRetry: () -> Unit,
    onOpenSpot: (SpotId) -> Unit,
) {
    var selectedSource by rememberSaveable(focus) { mutableStateOf(focus?.source) }
    var selectedValue by rememberSaveable(focus) { mutableStateOf(focus?.value) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SportSpotSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
    ) {
        Text(text = stringResource(R.string.tab_map), style = MaterialTheme.typography.headlineLarge)
        Text(
            text = stringResource(R.string.home_demo_location),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
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
                val selected = if (source != null && value != null) state.findSpot(SpotId(source, value)) else state.spots.firstOrNull()
                Text(text = stringResource(R.string.map_scheme), style = MaterialTheme.typography.titleLarge)
                SpotMap(state.spots, selected?.id) { id ->
                    selectedSource = id.source
                    selectedValue = id.value
                }
                if (selected != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(SportSpotSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
                        ) {
                            SpotSummary(selected)
                            SportSpotButton(
                                text = stringResource(R.string.map_details),
                                onClick = { onOpenSpot(selected.id) },
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

@Composable
private fun SpotMap(
    spots: List<Spot>,
    selectedId: SpotId?,
    onSelect: (SpotId) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val markers = remember(spots) { spots.groupBy { mapPosition(it.location) } }
    val markerSize = 45.dp

    fun selectMarker(group: List<Spot>) {
        val index = group.indexOfFirst { it.id == selectedId }
        onSelect(group[(index + 1) % group.size].id)
    }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(lerp(colors.surfaceVariant, colors.primary, 0.08f))
                .pointerInput(markers, selectedId) {
                    // Use distance to each center when several markers overlap.
                    detectTapGestures { touch ->
                        val nearest = nearestMarker(markers.keys, touch, size, markerSize.toPx() / 2)
                        if (nearest != null) selectMarker(markers.getValue(nearest))
                    }
                },
    ) {
        Canvas(Modifier.matchParentSize()) {
            for (line in 1..3) {
                val x = size.width * line / 4
                val y = size.height * line / 4
                val grid = colors.outline.copy(alpha = 0.3f)
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        }
        Text(
            text = stringResource(R.string.map_north),
            modifier = Modifier.align(Alignment.TopEnd).padding(SportSpotSpacing.sm),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        markers.forEach { (point, group) ->
            val selectedIndex = group.indexOfFirst { it.id == selectedId }
            val isSelected = selectedIndex >= 0
            val spot = group[if (isSelected) selectedIndex else 0]
            val label = stringResource(R.string.map_marker, spot.name, stringResource(spot.sportType.labelRes()))
            Box(
                modifier =
                    Modifier
                        .absoluteOffset((maxWidth - markerSize) * point.x, (maxHeight - markerSize) * point.y)
                        .zIndex(if (isSelected) 1f else 0f)
                        .size(markerSize)
                        .semantics {
                            contentDescription = label
                            selected = isSelected
                            role = Role.Button
                            onClick {
                                selectMarker(group)
                                true
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(if (isSelected) markerSize else 37.dp)
                            .background(if (isSelected) colors.primary else colors.onPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isSelected) R.drawable.ic_map_pin_selected else R.drawable.ic_map_pin),
                        contentDescription = null,
                        tint = if (isSelected) colors.onPrimary else colors.primary,
                    )
                }
            }
        }
    }
}

internal fun nearestMarker(
    positions: Set<Offset>,
    touch: Offset,
    viewport: IntSize,
    radius: Float,
): Offset? {
    val diameter = 2 * radius
    var bestDistance = radius * radius
    var bestPosition: Offset? = null
    for (position in positions) {
        val center = Offset(radius + (viewport.width - diameter) * position.x, radius + (viewport.height - diameter) * position.y)
        val distance = (center - touch).getDistanceSquared()
        if (distance <= bestDistance) {
            bestDistance = distance
            bestPosition = position
        }
    }
    return bestPosition
}

internal fun mapPosition(location: GeoPoint): Offset {
    val metersPerDegree = 111_320.0
    val east = (location.longitude - spotSearchCenter.longitude) * metersPerDegree * cos(Math.toRadians(spotSearchCenter.latitude))
    val north = (location.latitude - spotSearchCenter.latitude) * metersPerDegree
    val diameter = 2 * SPOT_SEARCH_RADIUS_METERS
    return Offset(
        x = (0.5 + east / diameter).coerceIn(0.0, 1.0).toFloat(),
        y = (0.5 - north / diameter).coerceIn(0.0, 1.0).toFloat(),
    )
}
