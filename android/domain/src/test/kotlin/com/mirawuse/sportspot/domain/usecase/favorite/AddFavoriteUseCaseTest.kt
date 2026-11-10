
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

class AddFavoriteUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `авторизованный пользователь может добавить площадку`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.USER))
            val favoriteRepository = FakeFavoriteRepository()
            val useCase = AddFavoriteUseCase(authRepository, favoriteRepository)

            val result = useCase(spotId)

            assertEquals(spotId, result.spotId)
            assertEquals(1, favoriteRepository.checkCalls)
            assertEquals(1, favoriteRepository.addCalls)
        }

    @Test
    fun `модератор может добавить площадку`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.MODERATOR))
            val favoriteRepository = FakeFavoriteRepository()
            val useCase = AddFavoriteUseCase(authRepository, favoriteRepository)

            val result = useCase(spotId)

            assertEquals(spotId, result.spotId)
            assertEquals(1, favoriteRepository.addCalls)
        }

    @Test
    fun `гость не может добавить площадку`() =
        runTest {
            val authRepository = FakeAuthRepository(AuthState.Guest)
            val favoriteRepository = FakeFavoriteRepository()
            val useCase = AddFavoriteUseCase(authRepository, favoriteRepository)

            val result = runCatching { useCase(spotId) }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, favoriteRepository.checkCalls)
            assertEquals(0, favoriteRepository.addCalls)
        }

    @Test
    fun `повторное добавление площадки запрещено`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.USER))
            val favoriteRepository = FakeFavoriteRepository(alreadyAdded = true)
            val useCase = AddFavoriteUseCase(authRepository, favoriteRepository)

            val result = runCatching { useCase(spotId) }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(1, favoriteRepository.checkCalls)
            assertEquals(0, favoriteRepository.addCalls)
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

    private class FakeFavoriteRepository(
        private val alreadyAdded: Boolean = false,
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
