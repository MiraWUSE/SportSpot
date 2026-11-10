
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository

class GetPendingProposalsUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(): List<SpotProposal> {
        val authState = authRepository.getAuthState()

        check(
            authState is AuthState.Authenticated &&
                authState.profile.isModerator(),
        ) {
            "Просматривать предложения на модерации может только модератор"
        }

        return proposalRepository.getPendingProposals()
    }
}
