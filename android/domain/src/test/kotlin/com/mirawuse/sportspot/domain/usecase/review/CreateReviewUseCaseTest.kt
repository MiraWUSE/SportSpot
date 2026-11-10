package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateReviewUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `авторизованный пользователь может создать отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase =
                CreateReviewUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = useCase(spotId, 5, " Отличная площадка ")

            assertEquals(5, result.rating)
            assertEquals("Отличная площадка", result.text)
            assertEquals(spotId, repository.lastSpotId)
            assertEquals(1, repository.createCalls)
        }

    @Test
    fun `модератор может создать отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase =
                CreateReviewUseCase(
                    FakeAuthRepository(authenticated(UserRole.MODERATOR)),
                    repository,
                )

            val result = useCase(spotId, 1, "Плохое покрытие")

            assertEquals(1, result.rating)
            assertEquals(1, repository.createCalls)
        }

    @Test
    fun `гость не может создать отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase =
                CreateReviewUseCase(
                    FakeAuthRepository(AuthState.Guest),
                    repository,
                )

            val result = runCatching { useCase(spotId, 5, "Хорошо") }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.createCalls)
        }

    @Test
    fun `оценка меньше 1 запрещена`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase =
                CreateReviewUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = runCatching { useCase(spotId, 0, "Текст") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.createCalls)
        }

    @Test
    fun `оценка больше 5 запрещена`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase =
                CreateReviewUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = runCatching { useCase(spotId, 6, "Текст") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.createCalls)
        }

    @Test
    fun `пустой текст отзыва запрещен`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase =
                CreateReviewUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = runCatching { useCase(spotId, 5, "   ") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.createCalls)
        }

    private fun authenticated(role: UserRole): AuthState =
        AuthState.Authenticated(
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = role,
            ),
        )

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override suspend fun getAuthState(): AuthState = state

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile = error("Метод не используется в тесте")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) {
            error("Метод не используется в тесте")
        }

        override suspend fun signOut() {
            error("Метод не используется в тесте")
        }
    }

    private class FakeReviewRepository : ReviewRepository {
        var createCalls = 0
        var lastSpotId: SpotId? = null

        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> = error("Метод не используется в тесте")

        override suspend fun createReview(
            spotId: SpotId,
            rating: Int,
            text: String,
        ): Review {
            createCalls++
            lastSpotId = spotId

            return Review(
                id = "review-123",
                spotId = spotId,
                authorId = "user-123",
                rating = rating,
                text = text,
            )
        }

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
