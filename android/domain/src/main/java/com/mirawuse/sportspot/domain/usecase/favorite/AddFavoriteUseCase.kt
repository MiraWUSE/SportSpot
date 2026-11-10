
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository

class AddFavoriteUseCase(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(spotId: SpotId): Favorite {
        val authState = authRepository.getAuthState()

        check(authState is AuthState.Authenticated) {
            "Для добавления в избранное необходимо авторизоваться"
        }

        check(!favoriteRepository.isFavorite(spotId)) {
            "Площадка уже находится в избранном"
        }

        return favoriteRepository.addFavorite(spotId)
    }
}
