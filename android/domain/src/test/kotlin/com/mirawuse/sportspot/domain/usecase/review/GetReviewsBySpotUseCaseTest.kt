package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetReviewsBySpotUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `возвращает отзывы выбранной площадки`() =
        runTest {
            val reviews =
                listOf(
                    Review(
                        id = "review-1",
                        spotId = spotId,
                        authorId = "user-123",
                        rating = 5,
                        text = "Отличная площадка",
                    ),
                    Review(
                        id = "review-2",
                        spotId = spotId,
                        authorId = "user-456",
                        rating = 4,
                        text = "Хорошее покрытие",
                    ),
                )

            val repository = FakeReviewRepository(reviews)
            val useCase = GetReviewsBySpotUseCase(repository)

            val result = useCase(spotId)

            assertEquals(reviews, result)
            assertEquals(spotId, repository.requestedSpotId)
            assertEquals(1, repository.getReviewsCalls)
        }

    @Test
    fun `возвращает пустой список если отзывов нет`() =
        runTest {
            val repository = FakeReviewRepository(emptyList())
            val useCase = GetReviewsBySpotUseCase(repository)

            val result = useCase(spotId)

            assertTrue(result.isEmpty())
            assertEquals(1, repository.getReviewsCalls)
        }

    private class FakeReviewRepository(
        private val reviews: List<Review>,
    ) : ReviewRepository {
        var requestedSpotId: SpotId? = null
        var getReviewsCalls = 0

        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> {
            requestedSpotId = spotId
            getReviewsCalls++
            return reviews
        }

        override suspend fun createReview(
            spotId: SpotId,
            rating: Int,
            text: String,
        ): Review = error("Метод не используется в тесте")

        override suspend fun updateReview(
            reviewId: String,
            rating: Int,
            text: String,
        ): Review = error("Метод не используется в тесте")

        override suspend fun deleteReview(reviewId: String) {
            error("Метод не используется в тесте")
        }
    }
}
