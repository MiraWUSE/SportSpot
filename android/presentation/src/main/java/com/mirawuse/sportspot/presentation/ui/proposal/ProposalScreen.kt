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
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.forms.ContentErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
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
) {
    FormScreen(stringResource(R.string.proposal_title), onBack) {
        when {
            restoring -> SpotSearchLoading()
            owner == null -> {
                Text(stringResource(R.string.proposals_sign_in))
                SportSpotButton(stringResource(R.string.auth_open), onOpenAuth, Modifier.fillMaxWidth())
            }
            submission is DomainResult.Success -> {
                FormCard(stringResource(R.string.proposal_sent)) {
                    ProposalStatusText(submission.value)
                    Text(stringResource(R.string.proposal_number, submission.value.id))
                }
                SportSpotButton(stringResource(R.string.profile_my_proposals), onViewProposals, Modifier.fillMaxWidth())
            }
            else -> key(owner) { ProposalFields(busy, (submission as? DomainResult.Failure)?.error, onSubmit) }
        }
    }
}

@Composable
private fun ProposalFields(
    busy: Boolean,
    error: DomainError?,
    onSubmit: (String, SportType, GeoPoint, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<SportType?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val nameError = FormValidation.required(name)
    val sportError = FormValidation.sport(sport)
    val latitudeError = FormValidation.latitude(latitude)
    val longitudeError = FormValidation.longitude(longitude)

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
            Row(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
                FormField(
                    label = stringResource(R.string.proposal_latitude),
                    value = latitude,
                    onValueChange = { if (!busy) latitude = it },
                    modifier = Modifier.weight(1f),
                    error = if (attempted) latitudeError else null,
                    hint = stringResource(R.string.form_coordinate_format),
                )
                FormField(
                    label = stringResource(R.string.proposal_longitude),
                    value = longitude,
                    onValueChange = { if (!busy) longitude = it },
                    modifier = Modifier.weight(1f),
                    error = if (attempted) longitudeError else null,
                    hint = stringResource(R.string.form_coordinate_format),
                )
            }
        }
        error?.let { ContentErrorText(it) }
        SportSpotButton(
            text = stringResource(R.string.proposal_submit),
            onClick = {
                attempted = true
                val selectedSport = sport
                if (nameError == null && selectedSport != null && latitudeError == null && longitudeError == null) {
                    val point = GeoPoint(latitude.trim().replace(',', '.').toDouble(), longitude.trim().replace(',', '.').toDouble())
                    onSubmit(name, selectedSport, point, address)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = busy,
        )
    }
}
