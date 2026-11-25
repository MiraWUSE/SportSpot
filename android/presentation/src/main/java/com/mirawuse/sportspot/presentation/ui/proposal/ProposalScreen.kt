package com.mirawuse.sportspot.presentation.ui.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.forms.ContentErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.home.SearchLocationState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun ProposalScreen(
    owner: String?,
    restoring: Boolean,
    busy: Boolean,
    submission: DomainResult<SpotProposal>?,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onViewProposals: () -> Unit,
    onSubmit: (String, SportType, GeoPoint, String) -> Unit,
    mapCenter: GeoPoint = SearchLocationState().center,
    photoAttachments: @Composable (SpotProposal?) -> Unit = {},
) {
    FormScreen(stringResource(R.string.proposal_title), onBack) {
        when {
            restoring -> SpotSearchLoading()
            owner == null -> {
                Text(stringResource(R.string.proposals_sign_in))
                SportSpotButton(stringResource(R.string.content_open_auth), onOpenAuth, Modifier.fillMaxWidth())
            }
            submission is DomainResult.Success -> {
                FormCard(stringResource(R.string.proposal_sent)) {
                    ProposalStatusBadge(submission.value.status)
                    Text(stringResource(R.string.proposal_number, submission.value.id))
                }
                photoAttachments(submission.value)
                SportSpotButton(stringResource(R.string.profile_my_proposals), onViewProposals, Modifier.fillMaxWidth(), enabled = !busy)
            }
            else ->
                key(owner) {
                    ProposalFields(busy, (submission as? DomainResult.Failure)?.error, mapCenter, onSubmit) { photoAttachments(null) }
                }
        }
    }
}

@Composable
private fun ProposalFields(
    busy: Boolean,
    error: DomainError?,
    mapCenter: GeoPoint,
    onSubmit: (String, SportType, GeoPoint, String) -> Unit,
    photoAttachments: @Composable () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<SportType?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var coordinates by rememberSaveable(stateSaver = ProposalCoordinates.Saver) { mutableStateOf(ProposalCoordinates()) }
    var choosingPoint by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val nameError = FormValidation.required(name)
    val sportError = FormValidation.sport(sport)
    val latitudeError = FormValidation.latitude(coordinates.latitude)
    val longitudeError = FormValidation.longitude(coordinates.longitude)

    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md)) {
        Text(stringResource(R.string.proposal_heading), style = MaterialTheme.typography.titleLarge)
        FormField(
            label = stringResource(R.string.proposal_name),
            value = name,
            onValueChange = { if (!busy) name = it },
            error = if (attempted) nameError else null,
            hint = stringResource(R.string.proposal_name_hint),
        )
        Column {
            Text(stringResource(R.string.proposal_sport), style = MaterialTheme.typography.labelMedium)
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Text(stringResource(sport?.labelRes() ?: R.string.proposal_sport_hint))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SportType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.labelRes())) },
                            onClick = {
                                sport = type
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            if (attempted && sportError != null) FormErrorText(sportError)
        }
        FormField(
            label = stringResource(R.string.proposal_address),
            value = address,
            onValueChange = { if (!busy) address = it },
            hint = stringResource(R.string.proposal_address_hint),
        )
        FormCard(stringResource(R.string.proposal_coordinates)) {
            Text(stringResource(R.string.proposal_coordinates_hint), style = MaterialTheme.typography.bodyMedium)
            SportSpotButton(
                text = stringResource(R.string.proposal_map_open),
                onClick = {
                    focusManager.clearFocus()
                    choosingPoint = true
                },
                modifier = Modifier.fillMaxWidth(),
                variant = SportSpotButtonVariant.SECONDARY,
                enabled = !busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
                FormField(
                    label = stringResource(R.string.proposal_latitude),
                    value = coordinates.latitude,
                    onValueChange = { if (!busy) coordinates = coordinates.copy(latitude = it) },
                    modifier = Modifier.weight(1f),
                    error = if (attempted) latitudeError else null,
                    hint = stringResource(R.string.form_coordinate_format),
                )
                FormField(
                    label = stringResource(R.string.proposal_longitude),
                    value = coordinates.longitude,
                    onValueChange = { if (!busy) coordinates = coordinates.copy(longitude = it) },
                    modifier = Modifier.weight(1f),
                    error = if (attempted) longitudeError else null,
                    hint = stringResource(R.string.form_coordinate_format),
                )
            }
        }
        error?.let { ContentErrorText(it) }
        photoAttachments()
        SportSpotButton(
            text = stringResource(R.string.proposal_submit),
            onClick = {
                attempted = true
                val selectedSport = sport
                val point = coordinates.pointOrNull()
                if (nameError == null && selectedSport != null && point != null) {
                    onSubmit(name, selectedSport, point, address)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = busy,
        )
    }
    if (choosingPoint) {
        ProposalMapPicker(
            initialPoint = coordinates.pointOrNull(),
            mapCenter = mapCenter,
            enabled = !busy,
            onConfirm = { selected ->
                if (!busy) {
                    coordinates = selected
                    choosingPoint = false
                }
            },
            onCancel = { choosingPoint = false },
        )
    }
}
