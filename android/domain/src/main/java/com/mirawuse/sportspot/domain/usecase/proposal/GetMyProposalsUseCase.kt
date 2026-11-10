package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository

class GetMyProposalsUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(): List<SpotProposal> {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для просмотра своих предложений необходимо авторизоваться"
        }

        return proposalRepository.getMyProposals()
    }
}
