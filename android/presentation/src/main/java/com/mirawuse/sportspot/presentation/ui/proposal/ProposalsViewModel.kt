package com.mirawuse.sportspot.presentation.ui.proposal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.proposal.CreateProposalUseCase
import com.mirawuse.sportspot.domain.usecase.proposal.GetMyProposalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ProposalsViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        repository: SpotProposalRepository,
    ) : ViewModel() {
        private val create = CreateProposalUseCase(auth, repository)
        private val getMine = GetMyProposalsUseCase(auth, repository)
        private var owner by mutableStateOf<String?>(null)
        private var list by mutableStateOf<DomainResult<List<SpotProposal>>?>(null)
        private var loadJob: Job? = null
        private var submission by mutableStateOf<Pair<String, DomainResult<SpotProposal>>?>(null)
        var submitting by mutableStateOf(false)
            private set

        fun listFor(userId: String?): DomainResult<List<SpotProposal>>? = list.takeIf { owner == userId }

        fun submissionFor(userId: String?): DomainResult<SpotProposal>? = submission?.takeIf { it.first == userId }?.second

        fun clearSubmission() {
            submission = null
        }

        fun load(userId: String?) {
            if (owner == userId && loadJob?.isActive == true) return
            loadJob?.cancel()
            owner = userId
            list = null
            if (userId == null) return
            loadJob = viewModelScope.launch { list = getMine(userId) }
        }

        fun submit(
            userId: String,
            name: String,
            sport: SportType,
            location: GeoPoint,
            address: String,
        ) {
            if (submitting || submissionFor(userId) is DomainResult.Success) return
            submitting = true
            clearSubmission()
            viewModelScope.launch {
                try {
                    val result = create(name, sport, location, address, userId)
                    submission = userId to result
                    if (result is DomainResult.Success && owner == userId) load(userId)
                } finally {
                    submitting = false
                }
            }
        }
    }
