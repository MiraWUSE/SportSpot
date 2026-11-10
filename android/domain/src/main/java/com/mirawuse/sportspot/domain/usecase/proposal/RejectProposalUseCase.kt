
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository

class RejectProposalUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(
        proposalId: String,
        reason: String,
    ): SpotProposal {
        val authState = authRepository.getAuthState()

        check(
            authState is AuthState.Authenticated &&
                authState.profile.isModerator(),
        ) {
            "Отклонять предложения может только модератор"
        }

        require(proposalId.isNotBlank()) {
            "Идентификатор предложения не может быть пустым"
        }

        require(reason.isNotBlank()) {
            "Причина отклонения не может быть пустой"
        }

        return proposalRepository.rejectProposal(
            proposalId = proposalId.trim(),
            reason = reason.trim(),
        )
    }
}
