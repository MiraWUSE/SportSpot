package com.mirawuse.sportspot.presentation.ui.moderation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.proposal.ApproveProposalUseCase
import com.mirawuse.sportspot.domain.usecase.proposal.GetPendingProposalsUseCase
import com.mirawuse.sportspot.domain.usecase.proposal.RejectProposalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ModerationViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        repository: SpotProposalRepository,
    ) : ViewModel() {
        private val getPending = GetPendingProposalsUseCase(auth, repository)
        private val approve = ApproveProposalUseCase(auth, repository)
        private val reject = RejectProposalUseCase(auth, repository)
        private var owner by mutableStateOf<String?>(null)
        private var pending by mutableStateOf<DomainResult<List<SpotProposal>>?>(null)
        private var actionError by mutableStateOf<DomainError?>(null)
        private var loadJob: Job? = null
        var busy by mutableStateOf(false)
            private set

        fun pendingFor(userId: String?): DomainResult<List<SpotProposal>>? = pending.takeIf { userId != null && owner == userId }

        fun errorFor(userId: String?): DomainError? = actionError.takeIf { userId != null && owner == userId }

        fun load(
            userId: String?,
            clearError: Boolean = true,
        ) {
            if (owner == userId && loadJob?.isActive == true) return
            loadJob?.cancel()
            owner = userId
            pending = null
            if (clearError) actionError = null
            if (userId == null) return
            loadJob = viewModelScope.launch { pending = getPending(userId) }
        }

        fun decide(
            userId: String,
            proposalId: String,
            rejectionReason: String? = null,
            onPublished: () -> Unit = {},
        ) {
            val current = (pendingFor(userId) as? DomainResult.Success)?.value ?: return
            if (busy || current.none { it.id == proposalId }) return
            busy = true
            actionError = null
            viewModelScope.launch {
                try {
                    val result =
                        if (rejectionReason == null) approve(proposalId, userId) else reject(proposalId, rejectionReason, userId)
                    if (owner != userId) return@launch
                    when (result) {
                        is DomainResult.Success -> {
                            pending = DomainResult.Success(current.filterNot { it.id == proposalId })
                            if (result.value.status == ProposalStatus.APPROVED) onPublished()
                        }
                        is DomainResult.Failure -> {
                            actionError = result.error
                            if (result.error == DomainError.Conflict || result.error == DomainError.NotFound) {
                                load(userId, clearError = false)
                            }
                        }
                    }
                } finally {
                    busy = false
                }
            }
        }
    }
