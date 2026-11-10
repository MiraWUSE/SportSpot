
package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class DeleteReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(reviewId: String): DomainResult<Unit> {
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

        reviewRepository.deleteReview(reviewId.trim())

        return DomainResult.Success(Unit)
    }
}
