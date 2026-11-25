package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId

interface ReviewRepository {
    suspend fun getReviewsBySpot(spotId: SpotId): List<Review>

    suspend fun getReviewsByUser(userId: String): List<Review>

    suspend fun createReview(
        userId: String,
        spotId: SpotId,
        rating: Int,
        text: String,
        title: String?,
    ): Review

    suspend fun updateReview(
        userId: String,
        reviewId: String,
        rating: Int,
        text: String,
        title: String?,
    ): Review

    suspend fun deleteReview(
        userId: String,
        reviewId: String,
    )
}
