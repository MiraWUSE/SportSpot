package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.ReviewRepository

class GetReviewsBySpotUseCase(
    private val repository: ReviewRepository,
) {
    suspend operator fun invoke(spotId: SpotId): List<Review> = repository.getReviewsBySpot(spotId)
}
