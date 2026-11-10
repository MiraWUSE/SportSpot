
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddFavoriteUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `пользователь успешно добавляет площадку`() =
        runTest {
            val repository = FakeFavoriteRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase(spotId)

            assertTrue(result is DomainResult.Success)
            assertEquals(spotId, (result as DomainResult.Success).value.spotId)
            assertEquals(1, repository.checkCalls)
            assertEquals(1, repository.addCalls)
        }

    @Test
    fun `модератор успешно добавляет площадку`() =
        runTest {
            val repository = FakeFavoriteRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase(spotId)

            assertTrue(result is DomainResult.Success)
            assertEquals(1, repository.addCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeFavoriteRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase(spotId)

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.checkCalls)
            assertEquals(0, repository.addCalls)
        }

    @Test
    fun `повторное добавление возвращает Conflict`() =
        runTest {
            val repository = FakeFavoriteRepository(alreadyAdded = true)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase(spotId)

            assertEquals(
                DomainResult.Failure(DomainError.Conflict),
                result,
            )
            assertEquals(1, repository.checkCalls)
            assertEquals(0, repository.addCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка репозитория")
            val repository = FakeFavoriteRepository(failure = failure)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase(spotId) }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.addCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: FavoriteRepository,
    ): AddFavoriteUseCase =
        AddFavoriteUseCase(
            authRepository = FakeAuthRepository(state),
            favoriteRepository = repository,
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

    private class FakeFavoriteRepository(
        private val alreadyAdded: Boolean = false,
        private val failure: Throwable? = null,
    ) : FavoriteRepository {
        var checkCalls = 0
        var addCalls = 0

        override suspend fun getFavorites(): List<Favorite> = emptyList()

        override suspend fun isFavorite(spotId: SpotId): Boolean {
            checkCalls++
            return alreadyAdded
        }

        override suspend fun addFavorite(spotId: SpotId): Favorite {
            addCalls++
            failure?.let { throw it }

            return Favorite(
                userId = "user-123",
                spotId = spotId,
                addedAt = "2026-11-10T10:00:00Z",
            )
        }

        override suspend fun removeFavorite(spotId: SpotId) {
            error("Метод не используется в тесте")
        }
    }
}
