package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository

class RemoveFavoriteUseCase(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(spotId: SpotId) {
        val authState = authRepository.getAuthState()

        check(authState is AuthState.Authenticated) {
            "Для удаления из избранного необходимо авторизоваться"
        }

        favoriteRepository.removeFavorite(spotId)
    }
}
