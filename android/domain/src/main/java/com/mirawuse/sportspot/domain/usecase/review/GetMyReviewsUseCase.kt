package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.contentResult

class GetMyReviewsUseCase(
    private val authRepository: AuthRepository,
    private val reviewRepository: ReviewRepository,
) {
    suspend operator fun invoke(expectedUserId: String): DomainResult<List<Review>> {
        val profile = (authRepository.getAuthState() as? AuthState.Authenticated)?.profile
        if (profile?.id != expectedUserId) return DomainResult.Failure(DomainError.Unauthorized)
        return contentResult { reviewRepository.getReviewsByUser(profile.id) }
    }
}
