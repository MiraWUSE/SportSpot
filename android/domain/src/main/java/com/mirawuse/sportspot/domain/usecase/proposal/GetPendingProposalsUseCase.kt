
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.contentResult

class GetPendingProposalsUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(expectedUserId: String? = null): DomainResult<List<SpotProposal>> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.moderatorError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        val userId = (authState as AuthState.Authenticated).profile.id
        if (expectedUserId != null && expectedUserId != userId) return DomainResult.Failure(DomainError.Unauthorized)
        return contentResult { proposalRepository.getPendingProposals(userId) }
    }
}
