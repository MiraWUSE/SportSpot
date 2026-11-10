
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository

class CreateProposalUseCase(
    private val authRepository: AuthRepository,
    private val proposalRepository: SpotProposalRepository,
) {
    suspend operator fun invoke(
        name: String,
        sportType: SportType,
        location: GeoPoint,
    ): SpotProposal {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для предложения площадки необходимо авторизоваться"
        }

        require(name.isNotBlank()) {
            "Название площадки не может быть пустым"
        }

        return proposalRepository.createProposal(
            name = name.trim(),
            sportType = sportType,
            location = location,
        )
    }
}
