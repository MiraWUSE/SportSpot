
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository

class ApproveProposalUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(proposalId: String): SpotProposal {
        val authState = authRepository.getAuthState()

        check(
            authState is AuthState.Authenticated &&
                authState.profile.isModerator(),
        ) {
            "Одобрять предложения может только модератор"
        }

        require(proposalId.isNotBlank()) {
            "Идентификатор предложения не может быть пустым"
        }

        return proposalRepository.approveProposal(proposalId.trim())
    }
}
