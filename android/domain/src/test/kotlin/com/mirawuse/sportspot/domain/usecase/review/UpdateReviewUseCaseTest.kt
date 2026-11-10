
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

class UpdateReviewUseCaseTest {
    @Test
    fun `авторизованный пользователь может изменить отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("review-123", 5, " Отличная площадка ")

            assertEquals("review-123", result.id)
            assertEquals(5, result.rating)
            assertEquals("Отличная площадка", result.text)
            assertEquals("review-123", repository.lastReviewId)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `модератор может изменить свой отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("review-123", 1, "Плохое покрытие")

            assertEquals(1, result.rating)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `гость не может изменить отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = runCatching { useCase("review-123", 5, "Текст") }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `оценка меньше 1 запрещена`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("review-123", 0, "Текст") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `оценка больше 5 запрещена`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("review-123", 6, "Текст") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `пустой текст запрещен`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("review-123", 5, "   ") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `пустой идентификатор отзыва запрещен`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("", 5, "Хорошая площадка") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.updateCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: ReviewRepository,
    ): UpdateReviewUseCase =
        UpdateReviewUseCase(
            authRepository = FakeAuthRepository(state),
            reviewRepository = repository,
        )

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
        var updateCalls = 0
        var lastReviewId: String? = null

        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> = emptyList()

        override suspend fun createReview(
            spotId: SpotId,
            rating: Int,
            text: String,
        ): Review = error("Метод не используется в тесте")

        override suspend fun updateReview(
            reviewId: String,
            rating: Int,
            text: String,
        ): Review {
            updateCalls++
            lastReviewId = reviewId

            return Review(
                id = reviewId,
                spotId = SpotId("osm", "12345"),
                authorId = "user-123",
                rating = rating,
                text = text,
            )
        }

        override suspend fun deleteReview(reviewId: String) {
            error("Метод не используется в тесте")
        }
    }
}
