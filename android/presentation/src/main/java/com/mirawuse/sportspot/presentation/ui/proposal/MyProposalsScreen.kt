package com.mirawuse.sportspot.presentation.ui.proposal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.components.SportSpotStatusBadge
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.StatusTone
import com.mirawuse.sportspot.presentation.ui.forms.ContentReadError
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen

@Composable
internal fun MyProposalsScreen(
    signedIn: Boolean,
    restoring: Boolean,
    state: DomainResult<List<SpotProposal>>?,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onRetry: () -> Unit,
    photoHistory: @Composable (SpotProposal) -> Unit = {},
) {
    FormScreen(stringResource(R.string.profile_my_proposals), onBack) {
        when {
            restoring -> SpotSearchLoading()
            !signedIn -> {
                Text(stringResource(R.string.proposals_sign_in))
                SportSpotButton(stringResource(R.string.content_open_auth), onOpenAuth, Modifier.fillMaxWidth())
            }
            state == null -> SpotSearchLoading()
            state is DomainResult.Failure -> {
                ContentReadError(state.error, onRetry)
            }
            state is DomainResult.Success -> {
                if (state.value.isEmpty()) Text(stringResource(R.string.proposals_empty))
                state.value.forEach { proposal ->
                    FormCard(proposal.name) {
                        ProposalStatusBadge(proposal.status)
                        proposal.createdAt?.take(10)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        proposal.rejectReason?.takeIf { proposal.status == ProposalStatus.REJECTED }?.let {
                            Text(stringResource(R.string.proposals_reason, it))
                        }
                        photoHistory(proposal)
                    }
                }
                SportSpotButton(
                    text = stringResource(R.string.content_refresh),
                    onClick = onRetry,
                    variant = SportSpotButtonVariant.SECONDARY,
                    icon = R.drawable.ic_action_refresh,
                )
            }
        }
    }
}

@Composable
internal fun ProposalStatusBadge(status: ProposalStatus) {
    val (label, tone) =
        when (status) {
            ProposalStatus.PENDING -> R.string.proposals_pending to StatusTone.PENDING
            ProposalStatus.APPROVED -> R.string.proposals_approved to StatusTone.APPROVED
            ProposalStatus.REJECTED -> R.string.proposals_rejected to StatusTone.REJECTED
        }
    SportSpotStatusBadge(stringResource(label), tone)
}
