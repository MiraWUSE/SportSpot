
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class CreateProposalUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(
        name: String,
        sportType: SportType,
        location: GeoPoint,
    ): DomainResult<SpotProposal> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.authenticatedError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        if (name.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Название площадки не может быть пустым",
                ),
            )
        }

        val proposal =
            proposalRepository.createProposal(
                name = name.trim(),
                sportType = sportType,
                location = location,
            )

        return DomainResult.Success(proposal)
    }
}
