package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveFavoriteUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `авторизованный пользователь может удалить площадку`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.USER))
            val favoriteRepository = FakeFavoriteRepository()
            val useCase = RemoveFavoriteUseCase(authRepository, favoriteRepository)

            useCase(spotId)

            assertEquals(listOf(spotId), favoriteRepository.removedIds)
        }

    @Test
    fun `модератор может удалить площадку`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.MODERATOR))
            val favoriteRepository = FakeFavoriteRepository()
            val useCase = RemoveFavoriteUseCase(authRepository, favoriteRepository)

            useCase(spotId)

            assertEquals(listOf(spotId), favoriteRepository.removedIds)
        }

    @Test
    fun `гость не может удалить площадку`() =
        runTest {
            val authRepository = FakeAuthRepository(AuthState.Guest)
            val favoriteRepository = FakeFavoriteRepository()
            val useCase = RemoveFavoriteUseCase(authRepository, favoriteRepository)

            val result = runCatching { useCase(spotId) }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertTrue(favoriteRepository.removedIds.isEmpty())
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

    private class FakeFavoriteRepository : FavoriteRepository {
        val removedIds = mutableListOf<SpotId>()

        override suspend fun getFavorites(): List<Favorite> = emptyList()

        override suspend fun addFavorite(spotId: SpotId): Favorite = error("Метод не используется в тесте")

        override suspend fun removeFavorite(spotId: SpotId) {
            removedIds.add(spotId)
        }

        override suspend fun isFavorite(spotId: SpotId): Boolean = false
    }
}
