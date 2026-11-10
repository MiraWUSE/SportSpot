
package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository

class DeleteReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(reviewId: String) {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для удаления отзыва необходимо авторизоваться"
        }

        require(reviewId.isNotBlank()) {
            "Идентификатор отзыва не может быть пустым"
        }

        reviewRepository.deleteReview(reviewId)
    }
}
