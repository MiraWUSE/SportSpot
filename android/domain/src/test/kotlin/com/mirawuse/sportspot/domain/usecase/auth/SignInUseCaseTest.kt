package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {
    @Test
    fun `пользователь успешно входит в аккаунт`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeAuthRepository(profile)
            val useCase = SignInUseCase(repository)

            val result = useCase("ivan@example.com", "password123")

            assertEquals(profile, result)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals(1, repository.signInCalls)
        }

    @Test
    fun `модератор получает профиль со своей ролью`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.MODERATOR))
            val useCase = SignInUseCase(repository)

            val result = useCase("ivan@example.com", "password123")

            assertEquals(UserRole.MODERATOR, result.role)
            assertTrue(result.isModerator())
        }

    @Test
    fun `пробелы вокруг электронной почты удаляются`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            useCase("  ivan@example.com  ", "password123")

            assertEquals("ivan@example.com", repository.lastEmail)
        }

    @Test
    fun `пустая электронная почта запрещена`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            val result = runCatching { useCase("   ", "password123") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.signInCalls)
        }

    @Test
    fun `пустой пароль запрещен`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            val result = runCatching { useCase("ivan@example.com", "   ") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.signInCalls)
        }

    private fun createProfile(role: UserRole): UserProfile =
        UserProfile(
            id = "user-123",
            name = "Иван",
            email = "ivan@example.com",
            role = role,
        )

    private class FakeAuthRepository(
        private val profile: UserProfile,
    ) : AuthRepository {
        var signInCalls = 0
        var lastEmail: String? = null

        override suspend fun getAuthState(): AuthState = error("Метод не используется в тесте")

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile {
            signInCalls++
            lastEmail = email
            return profile
        }

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
