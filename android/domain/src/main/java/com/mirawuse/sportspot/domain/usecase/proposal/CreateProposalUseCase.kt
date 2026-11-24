
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.contentResult

class CreateProposalUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(
        name: String,
        sportType: SportType,
        location: GeoPoint,
        address: String? = null,
        expectedUserId: String? = null,
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

        val userId = (authState as AuthState.Authenticated).profile.id
        if (expectedUserId != null && expectedUserId != userId) return DomainResult.Failure(DomainError.Unauthorized)
        val cleanName = name.trim()
        val cleanAddress = address?.trim()?.takeIf(String::isNotEmpty)
        if (cleanName.length > 120 || (cleanAddress?.length ?: 0) > 250) {
            return DomainResult.Failure(DomainError.Validation("Название — до 120 символов, адрес — до 250"))
        }
        return contentResult {
            proposalRepository.createProposal(
                userId = userId,
                name = cleanName,
                sportType = sportType,
                location = location,
                address = cleanAddress,
            )
        }
    }
}
