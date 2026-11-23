
package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.spot.Spot
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

class GetFavoritesUseCaseTest {
    private val favorite =
        Favorite(
            userId = "user-123",
            spotId = SpotId("osm", "12345"),
            addedAt = "2026-11-10T10:00:00Z",
        )

    @Test
    fun `пользователь получает список избранных площадок`() =
        runTest {
            val repository = FakeFavoriteRepository(listOf(favorite))
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase()

            assertEquals(
                DomainResult.Success(FavoriteList(listOf(favorite))),
                result,
            )
            assertEquals(1, repository.getFavoritesCalls)
        }

    @Test
    fun `модератор получает список избранных площадок`() =
        runTest {
            val repository = FakeFavoriteRepository(listOf(favorite))
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase()

            assertEquals(
                DomainResult.Success(FavoriteList(listOf(favorite))),
                result,
            )
            assertEquals(1, repository.getFavoritesCalls)
        }

    @Test
    fun `пустой список избранного считается успешным результатом`() =
        runTest {
            val repository = FakeFavoriteRepository(emptyList())
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase()

            assertEquals(
                DomainResult.Success(FavoriteList(emptyList())),
                result,
            )
            assertEquals(1, repository.getFavoritesCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeFavoriteRepository(listOf(favorite))
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase()

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.getFavoritesCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка загрузки избранного")
            val repository = FakeFavoriteRepository(emptyList(), failure)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.getFavoritesCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: FavoriteRepository,
    ): GetFavoritesUseCase =
        GetFavoritesUseCase(
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
        private val favorites: List<Favorite>,
        private val failure: Throwable? = null,
    ) : FavoriteRepository {
        var getFavoritesCalls = 0

        override suspend fun getFavorites(userId: String): FavoriteList {
            getFavoritesCalls++
            failure?.let { throw it }
            return FavoriteList(favorites)
        }

        override suspend fun addFavorite(
            userId: String,
            spot: Spot,
        ): Favorite = error("Метод не используется в тесте")

        override suspend fun removeFavorite(
            userId: String,
            spotId: SpotId,
        ) {
            error("Метод не используется в тесте")
        }

        override suspend fun isFavorite(
            userId: String,
            spotId: SpotId,
        ): Boolean = error("Метод не используется в тесте")
    }
}
