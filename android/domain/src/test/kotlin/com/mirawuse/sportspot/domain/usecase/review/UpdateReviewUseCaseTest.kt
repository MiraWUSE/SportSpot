
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

class UpdateReviewUseCaseTest {
    @Test
    fun `пользователь успешно изменяет отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("review-123", 5, " Отличная площадка ")

            assertTrue(result is DomainResult.Success)
            assertEquals(5, repository.lastRating)
            assertEquals("Отличная площадка", repository.lastText)
            assertEquals("review-123", repository.lastReviewId)
            assertEquals(1, repository.updateCalls)
            assertEquals(
                DomainResult.Success(repository.lastUpdatedReview),
                result,
            )
        }

    @Test
    fun `модератор успешно изменяет свой отзыв`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("review-123", 1, "Плохое покрытие")

            assertTrue(result is DomainResult.Success)
            assertEquals(1, repository.lastRating)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase("review-123", 5, "Хорошая площадка")

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `оценка меньше 1 возвращает Validation`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("review-123", 0, "Текст отзыва")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Оценка должна быть от 1 до 5",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `оценка больше 5 возвращает Validation`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("review-123", 6, "Текст отзыва")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Оценка должна быть от 1 до 5",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `пустой текст возвращает Validation`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("review-123", 5, "   ")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Текст отзыва не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `пустой идентификатор возвращает Validation`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("", 5, "Хорошая площадка")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Идентификатор отзыва не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `пробелы вокруг идентификатора и текста удаляются`() =
        runTest {
            val repository = FakeReviewRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result =
                useCase(
                    "  review-123  ",
                    4,
                    "  Хорошее покрытие  ",
                )

            assertTrue(result is DomainResult.Success)
            assertEquals("review-123", repository.lastReviewId)
            assertEquals("Хорошее покрытие", repository.lastText)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка обновления отзыва")
            val repository = FakeReviewRepository(failure)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result =
                runCatching {
                    useCase("review-123", 5, "Отличная площадка")
                }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.updateCalls)
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

    private class FakeReviewRepository(
        private val failure: Throwable? = null,
    ) : ReviewRepository {
        var updateCalls = 0
        var lastReviewId: String? = null
        var lastRating: Int? = null
        var lastText: String? = null
        var lastUpdatedReview: Review? = null

        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> = error("Метод не используется в тесте")

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
            lastRating = rating
            lastText = text

            failure?.let { throw it }

            val review =
                Review(
                    id = reviewId,
                    spotId = SpotId("osm", "12345"),
                    authorId = "user-123",
                    rating = rating,
                    text = text,
                )

            lastUpdatedReview = review
            return review
        }

        override suspend fun deleteReview(reviewId: String) {
            error("Метод не используется в тесте")
        }
    }
}
