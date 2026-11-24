package com.mirawuse.sportspot.presentation.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.mirawuse.sportspot.presentation.ui.proposal.ProposalStatusBadge
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
                    SportSpotButton(
                        text = stringResource(if (showPhotos) R.string.moderation_switch_proposals else R.string.photos_queue),
                        onClick = { showPhotos = !showPhotos },
                        variant = SportSpotButtonVariant.SECONDARY,
                        icon = if (showPhotos) null else R.drawable.ic_photo_image,
                    )
                    if (showPhotos) {
                        content()
                        return@FormScreen
                    }
                }
                ModerationHeading(title = stringResource(R.string.moderation_queue)) {
                    SportSpotButton(
                        text = stringResource(R.string.content_refresh),
                        onClick = onRetry,
                        variant = SportSpotButtonVariant.SECONDARY,
                        icon = R.drawable.ic_action_refresh,
                        enabled = !busy && pending != null,
                    )
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                actionError?.let { ContentErrorText(it) }
                when (pending) {
                    null -> SpotSearchLoading()
                    is DomainResult.Failure -> {
                        ContentErrorText(pending.error)
                    }
                    is DomainResult.Success -> {
                        if (pending.value.isEmpty()) Text(stringResource(R.string.moderation_queue_empty))
                        pending.value.forEach { proposal ->
                            key(account, proposal.id) {
                                ModerationProposal(proposal, busy, onApprove, onReject, proposalPhotos)
                            }
                        }
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
        ProposalStatusBadge(proposal.status)
        Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
            ModerationField(stringResource(R.string.moderation_sport_label), stringResource(proposal.sportType.labelRes()))
            ModerationField(
                stringResource(R.string.moderation_coordinates_label),
                stringResource(R.string.moderation_coordinate_values, proposal.location.latitude, proposal.location.longitude),
            )
            proposal.address?.let { ModerationField(stringResource(R.string.moderation_address_label), it) }
            proposal.description?.let { ModerationField(stringResource(R.string.moderation_description_label), it) }
            proposal.surface?.let { ModerationField(stringResource(R.string.moderation_surface_label), it) }
            proposal.equipment?.let { ModerationField(stringResource(R.string.moderation_equipment_label), it) }
            proposal.lighting?.let {
                ModerationField(stringResource(R.string.moderation_lighting_label), stringResource(lightingLabelRes(it)))
            }
        }
        photoAttachments(proposal)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ModerationDecisionButtons(
            enabled = !busy,
            onApprove = { onApprove(proposal.id) },
            onReject = { rejectionOpen = !rejectionOpen },
        )
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
                modifier = Modifier.fillMaxWidth(),
                variant = SportSpotButtonVariant.DANGER,
                isLoading = busy,
            )
        }
    }
}
