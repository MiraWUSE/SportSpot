
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository

class IsFavoriteUseCase(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(spotId: SpotId): Boolean =
        when (authRepository.getAuthState()) {
            AuthState.Guest -> false
            is AuthState.Authenticated -> favoriteRepository.isFavorite(spotId)
        }
}
