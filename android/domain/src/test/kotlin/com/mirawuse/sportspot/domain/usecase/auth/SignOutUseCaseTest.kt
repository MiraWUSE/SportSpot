
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutUseCaseTest {
    @Test
    fun `пользователь успешно выходит из аккаунта`() =
        runTest {
            val repository = FakeAuthRepository(authenticated(UserRole.USER))
            val useCase = SignOutUseCase(repository)

            val result = useCase()

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(1, repository.getStateCalls)
            assertEquals(1, repository.signOutCalls)
        }

    @Test
    fun `модератор успешно выходит из аккаунта`() =
        runTest {
            val repository = FakeAuthRepository(authenticated(UserRole.MODERATOR))
            val useCase = SignOutUseCase(repository)

            val result = useCase()

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(1, repository.signOutCalls)
        }

    @Test
    fun `гость получает Success без повторного выхода`() =
        runTest {
            val repository = FakeAuthRepository(AuthState.Guest)
            val useCase = SignOutUseCase(repository)

            val result = useCase()

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(1, repository.getStateCalls)
            assertEquals(0, repository.signOutCalls)
        }

    @Test
    fun `непредвиденная ошибка выхода не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка выхода")
            val repository =
                FakeAuthRepository(
                    state = authenticated(UserRole.USER),
                    failure = failure,
                )
            val useCase = SignOutUseCase(repository)

            val result = runCatching { useCase() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.signOutCalls)
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
        private val failure: Throwable? = null,
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
            failure?.let { throw it }
        }
    }
}
