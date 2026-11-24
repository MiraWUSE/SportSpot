package com.mirawuse.sportspot.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun SpotCardSummary(
    spot: Spot,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(
            text = spot.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SpotSportTag(spot, maxLines = 2)
        spot.address?.takeIf(String::isNotBlank)?.let { address ->
            Text(
                text = address,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SpotSportTag(
    spot: Spot,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = SportSpotPillShape,
        color = colors.primary.copy(alpha = 0.14f),
        contentColor = colors.onSurface,
    ) {
        Text(
            text = spot.sportLabel(),
            modifier = Modifier.padding(horizontal = SportSpotSpacing.sm, vertical = SportSpotSpacing.xs),
            style = MaterialTheme.typography.labelSmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
