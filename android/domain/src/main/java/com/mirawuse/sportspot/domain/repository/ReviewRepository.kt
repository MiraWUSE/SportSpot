package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId

interface ReviewRepository {
    suspend fun getReviewsBySpot(spotId: SpotId): List<Review>

    suspend fun createReview(
        spotId: SpotId,
        rating: Int,
        text: String,
    ): Review

    suspend fun updateReview(
        reviewId: String,
        rating: Int,
        text: String,
    ): Review

    suspend fun deleteReview(reviewId: String)
}
