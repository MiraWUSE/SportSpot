
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

class RemoveFavoriteUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `пользователь успешно удаляет площадку из избранного`() =
        runTest {
            val repository = FakeFavoriteRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase(spotId)

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(listOf(spotId), repository.removedIds)
            assertEquals(1, repository.removeCalls)
        }

    @Test
    fun `модератор успешно удаляет площадку из избранного`() =
        runTest {
            val repository = FakeFavoriteRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase(spotId)

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(listOf(spotId), repository.removedIds)
            assertEquals(1, repository.removeCalls)
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
            assertEquals(0, repository.removeCalls)
            assertTrue(repository.removedIds.isEmpty())
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка удаления")
            val repository = FakeFavoriteRepository(failure)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase(spotId) }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.removeCalls)
            assertTrue(repository.removedIds.isEmpty())
        }

    private fun createUseCase(
        state: AuthState,
        repository: FavoriteRepository,
    ): RemoveFavoriteUseCase =
        RemoveFavoriteUseCase(
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
        private val failure: Throwable? = null,
    ) : FavoriteRepository {
        val removedIds = mutableListOf<SpotId>()
        var removeCalls = 0

        override suspend fun getFavorites(): List<Favorite> = emptyList()

        override suspend fun addFavorite(spotId: SpotId): Favorite = error("Метод не используется в тесте")

        override suspend fun removeFavorite(spotId: SpotId) {
            removeCalls++
            failure?.let { throw it }
            removedIds.add(spotId)
        }

        override suspend fun isFavorite(spotId: SpotId): Boolean = false
    }
}
