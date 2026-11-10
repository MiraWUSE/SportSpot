
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsFavoriteUseCaseTest {
    private val spotId = SpotId("osm", "12345")

    @Test
    fun `возвращает true если площадка в избранном`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.USER))
            val favoriteRepository = FakeFavoriteRepository(true)
            val useCase = IsFavoriteUseCase(authRepository, favoriteRepository)

            val result = useCase(spotId)

            assertTrue(result)
            assertEquals(1, favoriteRepository.checkCalls)
            assertEquals(spotId, favoriteRepository.lastCheckedId)
        }

    @Test
    fun `возвращает false если площадки нет в избранном`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.USER))
            val favoriteRepository = FakeFavoriteRepository(false)
            val useCase = IsFavoriteUseCase(authRepository, favoriteRepository)

            val result = useCase(spotId)

            assertFalse(result)
            assertEquals(1, favoriteRepository.checkCalls)
        }

    @Test
    fun `для гостя возвращает false без обращения к избранному`() =
        runTest {
            val authRepository = FakeAuthRepository(AuthState.Guest)
            val favoriteRepository = FakeFavoriteRepository(true)
            val useCase = IsFavoriteUseCase(authRepository, favoriteRepository)

            val result = useCase(spotId)

            assertFalse(result)
            assertEquals(0, favoriteRepository.checkCalls)
        }

    @Test
    fun `модератор может проверить избранное`() =
        runTest {
            val authRepository = FakeAuthRepository(authenticated(UserRole.MODERATOR))
            val favoriteRepository = FakeFavoriteRepository(true)
            val useCase = IsFavoriteUseCase(authRepository, favoriteRepository)

            val result = useCase(spotId)

            assertTrue(result)
            assertEquals(1, favoriteRepository.checkCalls)
        }

    @Test
    fun `ошибка репозитория не превращается в false`() =
        runTest {
            val failure = IllegalStateException("Ошибка загрузки избранного")
            val authRepository = FakeAuthRepository(authenticated(UserRole.USER))
            val favoriteRepository =
                FakeFavoriteRepository(
                    favoriteResult = false,
                    failure = failure,
                )
            val useCase = IsFavoriteUseCase(authRepository, favoriteRepository)

            val result = runCatching { useCase(spotId) }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, favoriteRepository.checkCalls)
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
        private val favoriteResult: Boolean,
        private val failure: Throwable? = null,
    ) : FavoriteRepository {
        var checkCalls = 0
        var lastCheckedId: SpotId? = null

        override suspend fun getFavorites(): List<Favorite> = emptyList()

        override suspend fun addFavorite(spotId: SpotId): Favorite = error("Метод не используется в тесте")

        override suspend fun removeFavorite(spotId: SpotId) {
            error("Метод не используется в тесте")
        }

        override suspend fun isFavorite(spotId: SpotId): Boolean {
            checkCalls++
            lastCheckedId = spotId

            failure?.let { throw it }

            return favoriteResult
        }
    }
}
