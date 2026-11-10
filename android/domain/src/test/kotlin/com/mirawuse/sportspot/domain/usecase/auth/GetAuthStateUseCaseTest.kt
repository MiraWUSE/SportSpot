package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetAuthStateUseCaseTest {
    @Test
    fun `возвращает состояние гостя`() =
        runTest {
            val repository = FakeAuthRepository(AuthState.Guest)
            val useCase = GetAuthStateUseCase(repository)

            val result = useCase()

            assertEquals(AuthState.Guest, result)
            assertEquals(1, repository.getStateCalls)
        }

    @Test
    fun `возвращает авторизованного пользователя`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val expected = AuthState.Authenticated(profile)
            val repository = FakeAuthRepository(expected)
            val useCase = GetAuthStateUseCase(repository)

            val result = useCase()

            assertEquals(expected, result)
            assertEquals(1, repository.getStateCalls)
        }

    @Test
    fun `возвращает авторизованного модератора`() =
        runTest {
            val profile = createProfile(UserRole.MODERATOR)
            val expected = AuthState.Authenticated(profile)
            val repository = FakeAuthRepository(expected)
            val useCase = GetAuthStateUseCase(repository)

            val result = useCase()

            assertEquals(expected, result)
            assertEquals(1, repository.getStateCalls)
        }

    private fun createProfile(role: UserRole): UserProfile =
        UserProfile(
            id = "user-123",
            name = "Иван",
            email = "ivan@example.com",
            role = role,
        )

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        var getStateCalls = 0

        override suspend fun getAuthState(): AuthState {
            getStateCalls++
            return state
        }

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
}
