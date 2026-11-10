
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainResult

class GetPendingProposalsUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(): DomainResult<List<SpotProposal>> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.moderatorError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        return DomainResult.Success(
            proposalRepository.getPendingProposals(),
        )
    }
}
