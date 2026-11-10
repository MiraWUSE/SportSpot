
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class ApproveProposalUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(proposalId: String): DomainResult<SpotProposal> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.moderatorError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        if (proposalId.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Идентификатор предложения не может быть пустым",
                ),
            )
        }

        val proposal = proposalRepository.approveProposal(proposalId.trim())

        return DomainResult.Success(proposal)
    }
}
