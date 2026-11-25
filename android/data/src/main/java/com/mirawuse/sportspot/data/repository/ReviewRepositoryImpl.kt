package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mirawuse.sportspot.data.mapper.parseCanonicalSpotId
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.data.remote.api.ReviewApi
import com.mirawuse.sportspot.data.remote.api.ReviewRow
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

internal class ReviewRepositoryImpl
    @Inject
    constructor(
        private val api: ReviewApi,
        private val call: ContentCall,
        private val network: NetworkCall,
    ) : ReviewRepository {
        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> =
            call.execute { session ->
                val filter = "eq.${spotId.toCanonicalId()}"
                readPages({ it.spotId == spotId }) { offset -> network.body(api.listBySpot(filter, CONTENT_PAGE_SIZE, offset, session)) }
            }

        override suspend fun getReviewsByUser(userId: String): List<Review> =
            call.execute(userId) { session ->
                val filter = uuidFilter(userId)
                readPages({ it.authorId == userId }) { offset -> network.body(api.listByUser(filter, CONTENT_PAGE_SIZE, offset, session)) }
            }

        override suspend fun createReview(
            userId: String,
            spotId: SpotId,
            rating: Int,
            text: String,
            title: String?,
        ): Review =
            call.execute(userId) { session ->
                val body = reviewBody(rating, text, title, spotId.toCanonicalId())
                val review = network.body(api.create(body, session)).singleOrNull()?.toReview() ?: invalidContent()
                if (review.authorId != userId || review.spotId != spotId) invalidContent()
                review
            }

        override suspend fun updateReview(
            userId: String,
            reviewId: String,
            rating: Int,
            text: String,
            title: String?,
        ): Review =
            call.execute(userId) { session ->
                val rows = network.body(api.update(uuidFilter(reviewId), uuidFilter(userId), reviewBody(rating, text, title), session))
                if (rows.isEmpty()) throw NetworkException(NetworkError.Http(404))
                val review = rows.singleOrNull()?.toReview() ?: invalidContent()
                if (review.authorId != userId || review.id != reviewId) invalidContent()
                review
            }

        override suspend fun deleteReview(
            userId: String,
            reviewId: String,
        ) {
            call.execute(userId) { session ->
                val rows = network.body(api.delete(uuidFilter(reviewId), uuidFilter(userId), session))
                if (rows.isEmpty()) throw NetworkException(NetworkError.Http(404))
                if (rows.singleOrNull()?.id != reviewId) invalidContent()
            }
        }

        private suspend fun readPages(
            matches: (Review) -> Boolean,
            fetch: suspend (Int) -> List<ReviewRow?>,
        ): List<Review> {
            val reviews = linkedMapOf<String, Review>()
            var offset = 0
            do {
                val page = fetch(offset)
                if (page.size > CONTENT_PAGE_SIZE) invalidContent()
                val previousSize = reviews.size
                page.forEach { row ->
                    val review = row?.toReview() ?: invalidContent()
                    if (!matches(review)) invalidContent()
                    reviews[review.id] = review
                }
                if (page.size == CONTENT_PAGE_SIZE && reviews.size == previousSize) invalidContent()
                offset += page.size
            } while (page.size == CONTENT_PAGE_SIZE)
            return reviews.values.toList()
        }

        private fun ReviewRow.toReview(): Review =
            Review(
                id = id?.takeIf(String::isCanonicalUuid) ?: invalidContent(),
                spotId = spotId?.let(::parseCanonicalSpotId) ?: invalidContent(),
                authorId = userId?.takeIf(String::isCanonicalUuid) ?: invalidContent(),
                rating = rating?.takeIf { it in 1..5 } ?: invalidContent(),
                text = text?.takeIf(String::isNotBlank) ?: invalidContent(),
                title = title?.takeIf(String::isNotBlank),
                createdAt = createdAt,
            )

        private fun reviewBody(
            rating: Int,
            text: String,
            title: String?,
            spotId: String? = null,
        ): RequestBody =
            JsonObject()
                .apply {
                    if (spotId != null) addProperty("spot_id", spotId)
                    addProperty("rating", rating)
                    addProperty("text", text)
                    // PATCH must send null explicitly when an existing title is cleared.
                    add("title", title?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                }.toString()
                .toRequestBody("application/json".toMediaType())

        private fun uuidFilter(value: String): String {
            require(value.isCanonicalUuid())
            return "eq.$value"
        }
    }
