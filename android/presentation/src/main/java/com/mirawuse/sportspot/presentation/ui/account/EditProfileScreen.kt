package com.mirawuse.sportspot.presentation.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.forms.ContentErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen

@Composable
internal fun EditProfileScreen(
    profile: UserProfile,
    viewModel: EditProfileViewModel,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var attempted by rememberSaveable(profile.id) { mutableStateOf(false) }
    val nameError = FormValidation.profileName(name)
    BackHandler(enabled = viewModel.saving) {}
    FormScreen(stringResource(R.string.profile_edit), { if (!viewModel.saving) onBack() }) {
        FormField(
            label = stringResource(R.string.auth_name),
            value = name,
            onValueChange = { name = it },
            error = if (attempted) nameError else null,
            enabled = !viewModel.saving,
        )
        Text(stringResource(R.string.auth_email), style = MaterialTheme.typography.labelMedium)
        Text(profile.email, style = MaterialTheme.typography.bodyMedium)
        viewModel.error?.let { ContentErrorText(it) }
        SportSpotButton(
            text = stringResource(R.string.content_save),
            onClick = {
                attempted = true
                if (nameError == null) viewModel.save(profile.id, name, onBack)
            },
            modifier = Modifier.fillMaxWidth(),
            isLoading = viewModel.saving,
        )
        SportSpotButton(
            text = stringResource(R.string.content_cancel),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.saving,
            variant = SportSpotButtonVariant.SECONDARY,
        )
    }
}
