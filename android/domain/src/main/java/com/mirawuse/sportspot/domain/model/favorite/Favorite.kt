package com.mirawuse.sportspot.domain.model.favorite

import com.mirawuse.sportspot.domain.model.spot.SpotId

data class Favorite(
    val userId: String,
    val spotId: SpotId,
    val addedAt: String,
) {
    init {
        require(userId.isNotBlank()) {
            "Идентификатор пользователя не может быть пустым"
        }

        require(addedAt.isNotBlank()) {
            "Дата добавления не может быть пустой"
        }
    }
}
