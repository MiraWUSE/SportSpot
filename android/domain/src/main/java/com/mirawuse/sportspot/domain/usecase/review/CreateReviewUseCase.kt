package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.contentResult

class CreateReviewUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(
        spotId: SpotId,
        rating: Int,
        text: String,
        title: String? = null,
        expectedUserId: String? = null,
    ): DomainResult<Review> {
        val authState = authRepository.getAuthState()
        AuthorizationPolicy.authenticatedError(authState)?.let { return DomainResult.Failure(it) }
        val userId = (authState as AuthState.Authenticated).profile.id
        if (expectedUserId != null && expectedUserId != userId) return DomainResult.Failure(DomainError.Unauthorized)
        reviewValidation(rating, text, title)?.let { return DomainResult.Failure(it) }
        return contentResult {
            reviewRepository.createReview(userId, spotId, rating, text.trim(), title?.trim()?.takeIf(String::isNotEmpty))
        }
    }
}
