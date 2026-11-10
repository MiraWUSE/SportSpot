package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.spot.SpotId

interface FavoriteRepository {
    suspend fun getFavorites(): List<Favorite>

    suspend fun addFavorite(spotId: SpotId): Favorite

    suspend fun removeFavorite(spotId: SpotId)

    suspend fun isFavorite(spotId: SpotId): Boolean
}
