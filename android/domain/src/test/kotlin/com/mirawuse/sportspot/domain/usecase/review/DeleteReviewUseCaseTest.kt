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

class DeleteReviewUseCaseTest {
    @Test
    fun `авторизованный пользователь может удалить отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            useCase("review-123")

            assertEquals(listOf("review-123"), repository.deletedIds)
        }

    @Test
    fun `модератор может удалить свой отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            useCase("review-456")

            assertEquals(listOf("review-456"), repository.deletedIds)
        }

    @Test
    fun `гость не может удалить отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = runCatching { useCase("review-123") }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(repository.deletedIds.isEmpty())
        }

    @Test
    fun `пустой идентификатор отзыва запрещен`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(repository.deletedIds.isEmpty())
        }

    @Test
    fun `идентификатор из пробелов запрещен`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("   ") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(repository.deletedIds.isEmpty())
        }

    private fun createUseCase(
        state: AuthState,
        repository: ReviewRepository,
    ): DeleteReviewUseCase =
        DeleteReviewUseCase(
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
        val deletedIds = mutableListOf<String>()

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
        ): Review = error("Метод не используется в тесте")

        override suspend fun deleteReview(reviewId: String) {
            deletedIds.add(reviewId)
        }
    }
}
