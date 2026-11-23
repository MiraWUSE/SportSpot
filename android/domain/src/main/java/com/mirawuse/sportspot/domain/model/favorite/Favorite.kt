package com.mirawuse.sportspot.domain.model.favorite

import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

data class Favorite(
    val userId: String,
    val spotId: SpotId,
    val addedAt: String,
    val spot: Spot? = null,
) {
    init {
        require(userId.isNotBlank()) {
            "Идентификатор пользователя не может быть пустым"
        }

        require(addedAt.isNotBlank()) {
            "Дата добавления не может быть пустой"
        }
        require(spot == null || spot.id == spotId)
    }
}
