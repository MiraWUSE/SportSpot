package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository

class CreateReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(
        spotId: SpotId,
        rating: Int,
        text: String,
    ): Review {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для создания отзыва необходимо авторизоваться"
        }

        require(rating in 1..5) {
            "Оценка должна быть от 1 до 5"
        }

        require(text.isNotBlank()) {
            "Текст отзыва не может быть пустым"
        }

        return reviewRepository.createReview(
            spotId = spotId,
            rating = rating,
            text = text.trim(),
        )
    }
}
