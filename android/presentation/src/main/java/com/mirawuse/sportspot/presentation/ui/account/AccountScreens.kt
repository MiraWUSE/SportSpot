package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun AuthScreen(onBack: () -> Unit) {
    var isRegistration by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Keep the password in memory for this composition, outside saved state.
    var password by remember { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }

    val nameError = if (isRegistration) FormValidation.required(name) else null
    val emailError = FormValidation.email(email)
    val passwordError = FormValidation.required(password)
    val colors = MaterialTheme.colorScheme

    FormScreen(stringResource(R.string.auth_title), onBack) {
        Text(stringResource(R.string.auth_welcome), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.auth_permissions), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm), modifier = Modifier.fillMaxWidth()) {
            listOf(false, true).forEach { registration ->
                val selected = registration == isRegistration
                Text(
                    text = stringResource(if (registration) R.string.auth_registration_tab else R.string.auth_login_tab),
                    color = if (selected) colors.onPrimary else colors.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selected) colors.primary else colors.surfaceVariant)
                            .selectable(selected = selected, role = Role.Tab, onClick = {
                                isRegistration = registration
                                attempted = false
                                password = ""
                            })
                            .padding(vertical = SportSpotSpacing.md),
                )
            }
        }
        if (isRegistration) {
            FormField(
                label = stringResource(R.string.auth_name),
                value = name,
                onValueChange = { name = it },
                error = if (attempted) nameError else null,
            )
        }
        FormField(
            label = stringResource(R.string.auth_email),
            value = email,
            onValueChange = { email = it },
            error = if (attempted) emailError else null,
            hint = stringResource(R.string.auth_email_hint),
            keyboardType = KeyboardType.Email,
        )
        FormField(
            label = stringResource(R.string.auth_password),
            value = password,
            onValueChange = { password = it },
            error = if (attempted) passwordError else null,
            hint = stringResource(R.string.auth_password_hint),
            keyboardType = KeyboardType.Password,
            obscure = true,
        )
        SportSpotButton(
            text = stringResource(R.string.auth_validate),
            onClick = { attempted = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && nameError == null && emailError == null && passwordError == null) {
            Text(
                stringResource(if (isRegistration) R.string.auth_sign_up_unavailable else R.string.auth_sign_in_unavailable),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SportSpotButton(
            text = stringResource(R.string.auth_continue_guest),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            variant = SportSpotButtonVariant.TEXT,
        )
    }
}

@Composable
internal fun ProfileScreen(
    onOpenAuth: () -> Unit,
    onPropose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FormScreen(stringResource(R.string.tab_profile)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.md)) {
            Box(
                Modifier.size(64.dp).background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_tab_profile), contentDescription = null, tint = colors.onPrimary)
            }
            Text(stringResource(R.string.profile_guest), style = MaterialTheme.typography.headlineMedium)
        }
        SportSpotButton(
            text = stringResource(R.string.auth_open),
            onClick = onOpenAuth,
            modifier = Modifier.fillMaxWidth(),
        )
        FormCard(stringResource(R.string.profile_my_reviews)) {
            Text(stringResource(R.string.profile_reviews_guest), style = MaterialTheme.typography.bodyMedium)
        }
        FormCard(stringResource(R.string.profile_my_proposals)) {
            Text(stringResource(R.string.profile_proposals_guest), style = MaterialTheme.typography.bodyMedium)
            SportSpotButton(
                text = stringResource(R.string.proposal_title),
                onClick = onPropose,
                variant = SportSpotButtonVariant.OUTLINED,
            )
        }
        FormCard(stringResource(R.string.profile_settings)) {
            Text(stringResource(R.string.profile_theme_setting), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
