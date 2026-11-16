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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun ProposalScreen(onBack: () -> Unit) {
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

    FormScreen(stringResource(R.string.proposal_title), onBack) {
        Text(stringResource(R.string.proposal_heading), style = MaterialTheme.typography.titleLarge)
        FormField(
            label = stringResource(R.string.proposal_name),
            value = name,
            onValueChange = { name = it },
            error = if (attempted) nameError else null,
            hint = stringResource(R.string.proposal_name_hint),
        )
        Column {
            Text(stringResource(R.string.proposal_sport), style = MaterialTheme.typography.labelMedium)
            Box {
                TextButton(
                    onClick = { menuOpen = true },
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
            onValueChange = { address = it },
            hint = stringResource(R.string.proposal_address_hint),
        )
        FormCard(stringResource(R.string.proposal_coordinates)) {
            Row(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
                FormField(
                    label = stringResource(R.string.proposal_latitude),
                    value = latitude,
                    onValueChange = { latitude = it },
                    modifier = Modifier.weight(1f),
                    error = if (attempted) latitudeError else null,
                )
                FormField(
                    label = stringResource(R.string.proposal_longitude),
                    value = longitude,
                    onValueChange = { longitude = it },
                    modifier = Modifier.weight(1f),
                    error = if (attempted) longitudeError else null,
                )
            }
        }
        SportSpotButton(
            text = stringResource(R.string.proposal_validate),
            onClick = { attempted = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && nameError == null && sportError == null && latitudeError == null && longitudeError == null) {
            Text(stringResource(R.string.proposal_send_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
