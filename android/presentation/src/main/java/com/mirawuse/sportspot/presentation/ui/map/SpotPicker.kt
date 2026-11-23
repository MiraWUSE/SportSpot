package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.labelRes

@Composable
internal fun SpotPickerButton(
    spots: List<Spot>,
    selectedId: SpotId?,
    onSelect: (SpotId) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = spots.firstOrNull { it.id == selectedId }
    val selectionLabel =
        selected?.let {
            stringResource(R.string.map_selected_spot, stringResource(R.string.map_spot_option, spots.indexOf(it) + 1, it.name))
        }
    OutlinedButton(
        onClick = { expanded = true },
        enabled = spots.isNotEmpty(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.semantics { if (selectionLabel != null) stateDescription = selectionLabel },
    ) {
        Text(stringResource(R.string.map_choose_spot))
    }
    if (expanded && spots.isNotEmpty()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = spots.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.map_choose_spot)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).selectableGroup(), state = listState) {
                    itemsIndexed(spots, key = { _, spot -> "${spot.id.source.length}:${spot.id.source}${spot.id.value}" }) { index, spot ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .selectable(
                                    selected = spot.id == selectedId,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(spot.id)
                                        expanded = false
                                    },
                                ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = spot.id == selectedId,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onSurface),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Ordinals distinguish equal names without inventing an address.
                                Text(
                                    stringResource(R.string.map_spot_option, index + 1, spot.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(stringResource(spot.sportType.labelRes()), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { expanded = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text(stringResource(R.string.home_close)) }
            },
        )
    }
}
