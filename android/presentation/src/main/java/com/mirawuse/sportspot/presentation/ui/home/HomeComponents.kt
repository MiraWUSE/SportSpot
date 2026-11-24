package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotPhotoImage
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import com.mirawuse.sportspot.presentation.ui.map.OsmMap
import com.mirawuse.sportspot.presentation.ui.map.previewMapSpots
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun HomeMapPreview(
    spots: List<Spot>,
    center: GeoPoint,
    onOpenMap: () -> Unit,
) {
    val view = LocalView.current
    val markers = remember(spots, center) { previewMapSpots(spots, center) }
    var visible by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                visible = bounds.width > 0 && bounds.height > 0 && bounds.top < view.rootView.height && bounds.bottom > 0
            },
    ) {
        // LazyColumn may precompose this item offscreen; do not load unseen tiles.
        if (visible) {
            OsmMap(
                center = center,
                spots = markers,
                selectedId = null,
                isInteractive = false,
                modifier = Modifier.fillMaxSize(),
            ) {
                HomeMapControls(spots.size, onOpenMap, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun HomeMapControls(
    count: Int,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    Row(
        modifier = modifier.fillMaxWidth().padding(SportSpotSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_results_count, count),
                modifier =
                    Modifier
                        .clip(SportSpotPillShape)
                        .background(if (dark) colors.surfaceVariant else colors.surface)
                        .padding(horizontal = SportSpotSpacing.md, vertical = SportSpotSpacing.sm),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = stringResource(R.string.home_open_map),
                modifier =
                    Modifier
                        .clip(SportSpotPillShape)
                        .background(colors.primary)
                        .clickable(role = Role.Button, onClick = onOpenMap)
                        .padding(horizontal = SportSpotSpacing.lg, vertical = SportSpotSpacing.sm),
                color = colors.onPrimary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun HomeSpotCard(
    spot: Spot,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    isFavorite: Boolean = false,
    favoriteEnabled: Boolean = true,
    showPhotoLicense: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val cardColor = if (colors.background.luminance() < 0.5f) colors.surfaceVariant else colors.surface
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = colors.onSurface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SportSpotSpacing.sm, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
        ) {
            SpotPhotoImage(
                spot = spot,
                illustration = R.drawable.home_court,
                modifier = Modifier.size(width = 104.dp, height = 83.dp).clip(MaterialTheme.shapes.medium),
            ) { photo ->
                if (showPhotoLicense) {
                    Text(photo.license, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = SportSpotSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
            ) {
                Text(
                    spot.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    spot.sportLabel(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.home_ratings_unavailable),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp), enabled = favoriteEnabled) {
                Icon(
                    painterResource(if (isFavorite) R.drawable.ic_favorite_selected else R.drawable.ic_tab_favorites),
                    contentDescription = stringResource(if (isFavorite) R.string.favorites_remove else R.string.favorites_add),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}
