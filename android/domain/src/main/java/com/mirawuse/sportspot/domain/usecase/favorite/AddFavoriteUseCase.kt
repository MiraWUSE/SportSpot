
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class AddFavoriteUseCase(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(
        spot: Spot,
        expectedUserId: String? = null,
    ): DomainResult<Favorite> {
        val userId = authRepository.favoriteOwner(expectedUserId) ?: return DomainResult.Failure(DomainError.Unauthorized)
        return DomainResult.Success(favoriteRepository.addFavorite(userId, spot))
    }
}
