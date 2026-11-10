
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SignOutUseCaseTest {
    @Test
    fun `авторизованный пользователь может выйти из аккаунта`() =
        runTest {
            val repository = FakeAuthRepository(authenticated(UserRole.USER))
            val useCase = SignOutUseCase(repository)

            useCase()

            assertEquals(1, repository.getStateCalls)
            assertEquals(1, repository.signOutCalls)
        }

    @Test
    fun `модератор может выйти из аккаунта`() =
        runTest {
            val repository = FakeAuthRepository(authenticated(UserRole.MODERATOR))
            val useCase = SignOutUseCase(repository)

            useCase()

            assertEquals(1, repository.signOutCalls)
        }

    @Test
    fun `гость не вызывает выход из аккаунта`() =
        runTest {
            val repository = FakeAuthRepository(AuthState.Guest)
            val useCase = SignOutUseCase(repository)

            useCase()

            assertEquals(1, repository.getStateCalls)
            assertEquals(0, repository.signOutCalls)
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
        var getStateCalls = 0
        var signOutCalls = 0

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
            signOutCalls++
        }
    }
}
