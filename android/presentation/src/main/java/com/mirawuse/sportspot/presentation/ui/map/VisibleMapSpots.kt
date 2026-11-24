package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
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
        TextButton(onClick = onToggle, modifier = Modifier.semantics { stateDescription = expansion }) {
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
    Card(
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .padding(SportSpotSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
                SpotSummary(spot)
                spot.address?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onOpen, modifier = Modifier.semantics { contentDescription = details }) {
                    Text(stringResource(R.string.map_details))
                }
            }
        }
    }
}
