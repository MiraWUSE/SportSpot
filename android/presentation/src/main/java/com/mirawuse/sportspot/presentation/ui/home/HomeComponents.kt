package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotCardSummary
import com.mirawuse.sportspot.presentation.ui.components.SpotPhotoImage
import com.mirawuse.sportspot.presentation.ui.components.spotCardContainerColor
import com.mirawuse.sportspot.presentation.ui.map.OsmMap
import com.mirawuse.sportspot.presentation.ui.map.previewMapSpots
import com.mirawuse.sportspot.presentation.ui.photos.SpotCoverLoader
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun HomeMapPreview(
    spots: List<Spot>,
    center: GeoPoint,
    resultsPending: Boolean = false,
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
                HomeMapControls(spots.size.takeUnless { resultsPending }, onOpenMap, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun HomeMapControls(
    count: Int?,
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
                text =
                    if (count ==
                        null
                    ) {
                        stringResource(R.string.search_filters_updating)
                    } else {
                        stringResource(R.string.home_results_count, count)
                    },
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
    coverLoader: SpotCoverLoader? = null,
    showUnknownAddress: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = spotCardContainerColor(), contentColor = colors.onSurface),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(SportSpotSpacing.sm)) {
            // Give text the width below the photo when large type would crowd a narrow card.
            val stackPhoto = maxWidth < 340.dp && LocalDensity.current.fontScale >= 1.3f
            Row(Modifier.fillMaxWidth()) {
                val detailsModifier =
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button, onClick = onClick)
                if (stackPhoto) {
                    Column(detailsModifier, verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
                        HomeSpotThumbnail(spot, showPhotoLicense, coverLoader)
                        SpotCardSummary(spot, Modifier.padding(bottom = SportSpotSpacing.xs), showUnknownAddress)
                    }
                } else {
                    Row(
                        modifier = detailsModifier,
                        horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeSpotThumbnail(spot, showPhotoLicense, coverLoader)
                        SpotCardSummary(spot, Modifier.weight(1f).padding(vertical = SportSpotSpacing.xs), showUnknownAddress)
                    }
                }
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(48.dp).semantics { selected = isFavorite },
                    enabled = favoriteEnabled,
                ) {
                    Icon(
                        painterResource(if (isFavorite) R.drawable.ic_favorite_selected else R.drawable.ic_tab_favorites),
                        contentDescription = stringResource(if (isFavorite) R.string.favorites_remove else R.string.favorites_add),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSpotThumbnail(
    spot: Spot,
    showPhotoLicense: Boolean,
    coverLoader: SpotCoverLoader?,
) {
    Column(Modifier.width(104.dp)) {
        SpotPhotoImage(
            spot = spot,
            illustration = R.drawable.home_court,
            modifier = Modifier.fillMaxWidth().height(83.dp).clip(MaterialTheme.shapes.medium),
            coverLoader = coverLoader,
        ) { photo ->
            if (showPhotoLicense) {
                Text(
                    text = photo.license,
                    modifier = Modifier.padding(top = SportSpotSpacing.xs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
