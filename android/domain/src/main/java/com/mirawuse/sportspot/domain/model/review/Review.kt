
package com.mirawuse.sportspot.domain.model.review

import com.mirawuse.sportspot.domain.model.spot.SpotId

data class Review(
    val id: String,
    val spotId: SpotId,
    val authorId: String,
    val rating: Int,
    val text: String,
    val photoUrls: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) {
            "Идентификатор отзыва не может быть пустым"
        }

        require(authorId.isNotBlank()) {
            "Идентификатор автора не может быть пустым"
        }

        require(rating in 1..5) {
            "Оценка должна быть от 1 до 5"
        }

        require(text.isNotBlank()) {
            "Текст отзыва не может быть пустым"
        }
    }
}
