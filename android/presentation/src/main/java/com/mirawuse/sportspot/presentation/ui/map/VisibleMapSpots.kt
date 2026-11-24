package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotCardSummary
import com.mirawuse.sportspot.presentation.ui.components.spotCardContainerColor
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun VisibleMapSpotsHeader(
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val expansion = stringResource(if (expanded) R.string.map_list_expanded else R.string.map_list_collapsed)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.map_list_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (count != null) {
                Text(
                    stringResource(R.string.map_list_count, count),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(
            onClick = onToggle,
            modifier = Modifier.semantics { stateDescription = expansion },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(stringResource(if (expanded) R.string.map_list_collapse else R.string.map_list_expand))
        }
    }
}

@Composable
internal fun VisibleMapSpotRow(
    spot: Spot,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val details = stringResource(R.string.map_list_details, spot.name)
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) colors.surfaceVariant else spotCardContainerColor(),
                contentColor = colors.onSurface,
            ),
        border = if (selected) BorderStroke(1.dp, colors.outline) else null,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(horizontal = SportSpotSpacing.md, vertical = SportSpotSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = colors.onSurface, unselectedColor = colors.onSurfaceVariant),
            )
            SpotCardSummary(spot, Modifier.weight(1f))
        }
        TextButton(
            onClick = onOpen,
            modifier =
                Modifier
                    .align(Alignment.End)
                    .padding(end = SportSpotSpacing.sm)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = details },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
        ) {
            Text(stringResource(R.string.map_details))
        }
    }
}
