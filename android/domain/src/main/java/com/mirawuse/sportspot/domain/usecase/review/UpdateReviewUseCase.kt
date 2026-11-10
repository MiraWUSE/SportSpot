
package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class UpdateReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(
        reviewId: String,
        rating: Int,
        text: String,
    ): DomainResult<Review> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.authenticatedError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        if (reviewId.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Идентификатор отзыва не может быть пустым",
                ),
            )
        }

        if (rating !in 1..5) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Оценка должна быть от 1 до 5",
                ),
            )
        }

        if (text.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Текст отзыва не может быть пустым",
                ),
            )
        }

        val review =
            reviewRepository.updateReview(
                reviewId = reviewId.trim(),
                rating = rating,
                text = text.trim(),
            )

        return DomainResult.Success(review)
    }
}
