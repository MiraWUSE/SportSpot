
package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteReviewUseCaseTest {
    @Test
    fun `пользователь успешно удаляет отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("review-123")

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(listOf("review-123"), repository.deletedIds)
            assertEquals(1, repository.deleteCalls)
        }

    @Test
    fun `модератор успешно удаляет свой отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("review-456")

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(listOf("review-456"), repository.deletedIds)
            assertEquals(1, repository.deleteCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase("review-123")

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.deleteCalls)
            assertTrue(repository.deletedIds.isEmpty())
        }

    @Test
    fun `пустой идентификатор возвращает Validation`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Идентификатор отзыва не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `идентификатор из пробелов возвращает Validation`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("   ")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Идентификатор отзыва не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.deleteCalls)
        }

    @Test
    fun `пробелы вокруг идентификатора удаляются`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("  review-123  ")

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(listOf("review-123"), repository.deletedIds)
            assertEquals(1, repository.deleteCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка удаления отзыва")
            val repository = FakeReviewRepository(failure)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase("review-123") }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.deleteCalls)
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

    private class FakeReviewRepository(
        private val failure: Throwable? = null,
    ) : ReviewRepository {
        val deletedIds = mutableListOf<String>()
        var deleteCalls = 0

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
            deleteCalls++
            failure?.let { throw it }
            deletedIds.add(reviewId)
        }
    }
}
