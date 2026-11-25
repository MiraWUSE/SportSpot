package com.mirawuse.sportspot.presentation.ui.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.home.SearchLocationState
import com.mirawuse.sportspot.presentation.ui.map.OsmMap
import com.mirawuse.sportspot.presentation.ui.map.isMapCoordinate
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun ProposalMapPicker(
    initialPoint: GeoPoint?,
    mapCenter: GeoPoint,
    enabled: Boolean,
    onConfirm: (ProposalCoordinates) -> Unit,
    onCancel: () -> Unit,
) {
    val initial = initialPoint?.takeIf { isMapCoordinate(it.latitude, it.longitude) }
    val center =
        rememberSaveable(saver = ProposalCoordinates.Saver) {
            val start = initial ?: mapCenter.takeIf { isMapCoordinate(it.latitude, it.longitude) } ?: SearchLocationState().center
            ProposalCoordinates.fromPoint(start)
        }
    var selected by rememberSaveable(stateSaver = ProposalCoordinates.Saver) {
        mutableStateOf(initial?.let { ProposalCoordinates.fromPoint(it) } ?: ProposalCoordinates())
    }
    val point = selected.pointOrNull()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val mapHeight = (maxHeight * 0.52f).coerceIn(180.dp, 540.dp)
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SportSpotSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.proposal_map_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(stringResource(R.string.proposal_map_hint), style = MaterialTheme.typography.bodyMedium)
                    if (initialPoint != null && initial == null && point == null) {
                        Text(stringResource(R.string.proposal_map_point_unavailable), style = MaterialTheme.typography.bodyMedium)
                    }
                    OsmMap(
                        center = checkNotNull(center.pointOrNull()),
                        spots = emptyList(),
                        selectedId = null,
                        isInteractive = true,
                        modifier = Modifier.fillMaxWidth().height(mapHeight).clip(MaterialTheme.shapes.large),
                        pickedPoint = point,
                        onMapTap = { if (enabled) selected = ProposalCoordinates.fromPoint(it) },
                    )
                    if (point != null) {
                        Text(stringResource(R.string.proposal_map_selected, selected.latitude, selected.longitude))
                    }
                    SportSpotButton(
                        text = stringResource(R.string.proposal_map_confirm),
                        onClick = { if (enabled && point != null) onConfirm(selected) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled && point != null,
                    )
                    SportSpotButton(
                        text = stringResource(R.string.content_cancel),
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        variant = SportSpotButtonVariant.SECONDARY,
                    )
                }
            }
        }
    }
}
