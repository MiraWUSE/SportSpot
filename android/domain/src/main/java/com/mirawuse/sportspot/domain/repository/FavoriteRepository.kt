package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

interface FavoriteRepository {
    suspend fun getFavorites(userId: String): FavoriteList

    suspend fun addFavorite(
        userId: String,
        spot: Spot,
    ): Favorite

    suspend fun removeFavorite(
        userId: String,
        spotId: SpotId,
    )

    suspend fun isFavorite(
        userId: String,
        spotId: SpotId,
    ): Boolean
}
