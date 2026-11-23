
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class GetFavoritesUseCase(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(expectedUserId: String? = null): DomainResult<FavoriteList> {
        val userId = authRepository.favoriteOwner(expectedUserId) ?: return DomainResult.Failure(DomainError.Unauthorized)
        return DomainResult.Success(favoriteRepository.getFavorites(userId))
    }
}
