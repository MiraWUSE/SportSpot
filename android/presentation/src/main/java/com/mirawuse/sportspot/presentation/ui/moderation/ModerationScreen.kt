package com.mirawuse.sportspot.presentation.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme

@Composable
internal fun ModerationScreen(
    auth: AuthState,
    pending: List<SpotProposal>?,
    onBack: () -> Unit,
) {
    FormScreen(stringResource(R.string.moderation_title), onBack) {
        if (AuthorizationPolicy.moderatorError(auth) != null) {
            FormCard(stringResource(R.string.moderation_access)) {
                Text(stringResource(R.string.moderation_role_required))
            }
        } else {
            Text(stringResource(R.string.moderation_queue), style = MaterialTheme.typography.headlineSmall)
            when {
                pending == null -> Text(stringResource(R.string.moderation_unavailable))
                pending.isEmpty() -> Text(stringResource(R.string.moderation_queue_empty))
                else ->
                    pending.forEach { proposal ->
                        key(proposal.id) { ModerationProposal(proposal.name) }
                    }
            }
        }
    }
}

@Composable
private fun ModerationProposal(name: String) {
    var rejectionOpen by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var approveAttempted by rememberSaveable { mutableStateOf(false) }
    val reasonError = FormValidation.required(reason)

    FormCard(name) {
        Text(stringResource(R.string.moderation_pending), style = MaterialTheme.typography.bodyMedium)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
        SportSpotButton(
            text = stringResource(R.string.moderation_approve),
            onClick = {
                approveAttempted = true
                rejectionOpen = false
            },
            modifier = Modifier.weight(1f),
        )
        SportSpotButton(
            text = stringResource(R.string.moderation_reject),
            onClick = {
                rejectionOpen = true
                approveAttempted = false
            },
            modifier = Modifier.weight(1f),
            variant = SportSpotButtonVariant.OUTLINED,
        )
    }
    if (rejectionOpen) {
        FormField(
            label = stringResource(R.string.moderation_reason),
            value = reason,
            onValueChange = { reason = it },
            error = if (attempted) reasonError else null,
            singleLine = false,
        )
        SportSpotButton(text = stringResource(R.string.moderation_validate), onClick = { attempted = true })
    }
    if (approveAttempted || (rejectionOpen && attempted && reasonError == null)) {
        Text(stringResource(R.string.moderation_send_unavailable))
    }
}

@Preview(showBackground = true)
@Composable
private fun ModeratorPreview() {
    SportSpotTheme {
        Scaffold { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                ModerationScreen(
                    auth = AuthState.Authenticated(UserProfile("preview", "Модератор", "moderator@example.test", UserRole.MODERATOR)),
                    pending =
                        listOf(
                            SpotProposal(
                                id = "preview",
                                authorId = "preview-author",
                                name = "Воркаут-площадка",
                                sportType = SportType.WORKOUT,
                                location = GeoPoint(56.3269, 44.0059),
                            ),
                        ),
                    onBack = {},
                )
            }
        }
    }
}
