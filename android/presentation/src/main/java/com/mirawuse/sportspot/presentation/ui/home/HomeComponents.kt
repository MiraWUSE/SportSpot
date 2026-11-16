package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.map.mapPosition
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun HomeMapPreview(
    spots: List<Spot>,
    onOpenMap: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val ground = if (dark) Color(0xFF1D3028) else Color(0xFFEAF1E9)
    val blocks = if (dark) Color(0xFF2F5341) else Color(0xFFCFE5C5)
    val roads = if (dark) Color(0xFF3A4943) else colors.surface
    val secondaryMarker = Color(0xFF20312A)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(158.dp)
                .clip(MaterialTheme.shapes.large)
                .background(ground),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawRoundRect(blocks, Offset(w * 0.05f, h * 0.09f), Size(w * 0.30f, h * 0.52f), CornerRadius(12.dp.toPx()))
            drawRoundRect(blocks, Offset(w * 0.58f, h * 0.04f), Size(w * 0.35f, h * 0.44f), CornerRadius(12.dp.toPx()))
            drawRoundRect(blocks, Offset(w * 0.06f, h * 0.62f), Size(w * 0.23f, h * 0.35f), CornerRadius(12.dp.toPx()))
            drawRoundRect(blocks, Offset(w * 0.61f, h * 0.66f), Size(w * 0.29f, h * 0.28f), CornerRadius(12.dp.toPx()))
            listOf(0.33f, 0.73f).forEach { x -> drawRect(roads, Offset(w * x, 0f), Size(w * 0.05f, h)) }
            listOf(0.32f, 0.72f).forEach { y -> drawRect(roads, Offset(0f, h * y), Size(w, h * 0.11f)) }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = SportSpotSpacing.xxxl)) {
            val mainMarkerSize = 36.dp
            val outerPadding = mainMarkerSize / 2
            val markerCenters = mutableListOf<Offset>()
            for (spot in spots) {
                val point = mapPosition(spot.location)
                val center =
                    Offset(
                        (outerPadding + (maxWidth - mainMarkerSize) * point.x).value,
                        (outerPadding + (maxHeight - mainMarkerSize) * point.y).value,
                    )
                // Skip overlapping pins and keep each visible pin at its real projected position.
                if (markerCenters.any { (it - center).getDistance() < 32f }) continue

                val primary = markerCenters.isEmpty()
                val markerSize = if (primary) mainMarkerSize else 25.dp
                markerCenters.add(center)
                Box(
                    modifier =
                        Modifier
                            .absoluteOffset(center.x.dp - markerSize / 2, center.y.dp - markerSize / 2)
                            .size(markerSize)
                            .background(if (primary) colors.primary else secondaryMarker, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_map_pin),
                        contentDescription = null,
                        tint = if (primary) colors.onPrimary else colors.primary,
                    )
                }
                if (markerCenters.size == 3) break
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(SportSpotSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_results_count, spots.size),
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
}

@Composable
internal fun HomeSpotCard(
    spot: Spot,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
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
            Image(
                painter = painterResource(R.drawable.home_court),
                contentDescription = null,
                modifier = Modifier.size(width = 104.dp, height = 83.dp).clip(MaterialTheme.shapes.medium),
            )
            Column(
                modifier = Modifier.weight(1f).padding(vertical = SportSpotSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
            ) {
                Text(spot.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    stringResource(spot.sportType.labelRes()),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.home_ratings_unavailable),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    painterResource(R.drawable.ic_tab_favorites),
                    contentDescription = stringResource(R.string.tab_favorites),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}
