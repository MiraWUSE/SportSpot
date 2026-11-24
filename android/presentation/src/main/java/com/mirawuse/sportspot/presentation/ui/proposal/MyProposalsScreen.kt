package com.mirawuse.sportspot.presentation.ui.proposal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.forms.ContentErrorText
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
                ContentErrorText(state.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
            }
            state is DomainResult.Success -> {
                if (state.value.isEmpty()) Text(stringResource(R.string.proposals_empty))
                state.value.forEach { proposal ->
                    FormCard(proposal.name) {
                        ProposalStatusText(proposal)
                        proposal.createdAt?.take(10)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        proposal.rejectReason?.takeIf { proposal.status == ProposalStatus.REJECTED }?.let {
                            Text(stringResource(R.string.proposals_reason, it))
                        }
                        photoHistory(proposal)
                    }
                }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.content_refresh)) }
            }
        }
    }
}

@Composable
internal fun ProposalStatusText(proposal: SpotProposal) {
    val label =
        when (proposal.status) {
            ProposalStatus.PENDING -> R.string.proposals_pending
            ProposalStatus.APPROVED -> R.string.proposals_approved
            ProposalStatus.REJECTED -> R.string.proposals_rejected
        }
    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
}
