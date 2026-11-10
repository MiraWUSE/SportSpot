package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository

class UpdateReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(
        reviewId: String,
        rating: Int,
        text: String,
    ): Review {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для редактирования отзыва необходимо авторизоваться"
        }

        require(reviewId.isNotBlank()) {
            "Идентификатор отзыва не может быть пустым"
        }

        require(rating in 1..5) {
            "Оценка должна быть от 1 до 5"
        }

        require(text.isNotBlank()) {
            "Текст отзыва не может быть пустым"
        }

        return reviewRepository.updateReview(
            reviewId = reviewId,
            rating = rating,
            text = text.trim(),
        )
    }
}
