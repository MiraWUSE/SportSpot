
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class RemoveFavoriteUseCase(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(
        spotId: SpotId,
        expectedUserId: String? = null,
    ): DomainResult<Unit> {
        val userId = authRepository.favoriteOwner(expectedUserId) ?: return DomainResult.Failure(DomainError.Unauthorized)
        favoriteRepository.removeFavorite(userId, spotId)
        return DomainResult.Success(Unit)
    }
}
