package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.repository.FavoriteRepository

class GetFavoritesUseCase(
    private val repository: FavoriteRepository,
) {
    suspend operator fun invoke(): List<Favorite> = repository.getFavorites()
}
