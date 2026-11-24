package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.contentResult

class DeleteReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(
        reviewId: String,
        expectedUserId: String? = null,
    ): DomainResult<Unit> {
        val authState = authRepository.getAuthState()
        AuthorizationPolicy.authenticatedError(authState)?.let { return DomainResult.Failure(it) }
        val userId = (authState as AuthState.Authenticated).profile.id
        if (expectedUserId != null && expectedUserId != userId) return DomainResult.Failure(DomainError.Unauthorized)
        if (reviewId.isBlank()) return DomainResult.Failure(DomainError.Validation("Идентификатор отзыва не может быть пустым"))
        return contentResult {
            reviewRepository.deleteReview(userId, reviewId.trim())
        }
    }
}
