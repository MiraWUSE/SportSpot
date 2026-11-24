package com.mirawuse.sportspot.presentation.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.details.lightingLabelRes
import com.mirawuse.sportspot.presentation.ui.forms.ContentErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun ModerationScreen(
    auth: AuthState,
    restoring: Boolean,
    pending: DomainResult<List<SpotProposal>>?,
    busy: Boolean,
    actionError: DomainError?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    photoQueue: (@Composable () -> Unit)? = null,
    proposalPhotos: @Composable (SpotProposal) -> Unit = {},
) {
    val account = (auth as? AuthState.Authenticated)?.profile?.id
    var showPhotos by rememberSaveable(account) { mutableStateOf(false) }
    FormScreen(stringResource(R.string.moderation_title), onBack) {
        when {
            restoring -> SpotSearchLoading()
            AuthorizationPolicy.moderatorError(auth) != null -> {
                FormCard(stringResource(R.string.moderation_access)) { Text(stringResource(R.string.moderation_role_required)) }
            }
            else -> {
                photoQueue?.let { content ->
                    TextButton(onClick = { showPhotos = !showPhotos }) {
                        Text(stringResource(if (showPhotos) R.string.moderation_queue else R.string.photos_queue))
                    }
                    if (showPhotos) {
                        content()
                        return@FormScreen
                    }
                }
                Text(stringResource(R.string.moderation_queue), style = MaterialTheme.typography.headlineSmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                actionError?.let { ContentErrorText(it) }
                when (pending) {
                    null -> SpotSearchLoading()
                    is DomainResult.Failure -> {
                        ContentErrorText(pending.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                    }
                    is DomainResult.Success -> {
                        if (pending.value.isEmpty()) Text(stringResource(R.string.moderation_queue_empty))
                        pending.value.forEach { proposal ->
                            key(account, proposal.id) {
                                ModerationProposal(proposal, busy, onApprove, onReject, proposalPhotos)
                            }
                        }
                        TextButton(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.content_refresh)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModerationProposal(
    proposal: SpotProposal,
    busy: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    photoAttachments: @Composable (SpotProposal) -> Unit,
) {
    var rejectionOpen by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val reasonError = FormValidation.required(reason)
    val reasonTooLong = reason.trim().length > 1000

    FormCard(proposal.name) {
        Text(stringResource(proposal.sportType.labelRes()))
        Text(stringResource(R.string.moderation_coordinates, proposal.location.latitude, proposal.location.longitude))
        proposal.address?.let { Text(it) }
        proposal.description?.let { Text(it) }
        proposal.surface?.let { Text(stringResource(R.string.details_surface, it)) }
        proposal.equipment?.let { Text(stringResource(R.string.details_equipment, it)) }
        proposal.lighting?.let { Text(stringResource(R.string.details_lighting, stringResource(lightingLabelRes(it)))) }
        photoAttachments(proposal)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
        SportSpotButton(
            text = stringResource(R.string.moderation_approve),
            onClick = { onApprove(proposal.id) },
            modifier = Modifier.weight(1f),
            enabled = !busy,
        )
        SportSpotButton(
            text = stringResource(R.string.moderation_reject),
            onClick = { rejectionOpen = !rejectionOpen },
            modifier = Modifier.weight(1f),
            variant = SportSpotButtonVariant.OUTLINED,
            enabled = !busy,
        )
    }
    if (rejectionOpen) {
        FormField(
            label = stringResource(R.string.moderation_reason),
            value = reason,
            onValueChange = { if (!busy) reason = it },
            error = if (attempted) reasonError else null,
            singleLine = false,
        )
        if (attempted && reasonTooLong) {
            Text(stringResource(R.string.moderation_reason_length), color = MaterialTheme.colorScheme.error)
        }
        SportSpotButton(
            text = stringResource(R.string.moderation_confirm_rejection),
            onClick = {
                attempted = true
                if (reasonError == null && !reasonTooLong) onReject(proposal.id, reason)
            },
            isLoading = busy,
        )
    }
}
