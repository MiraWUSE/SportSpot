package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpUseCaseTest {
    @Test
    fun `регистрация передает данные в репозиторий`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            useCase("Иван", "ivan@example.com", "password123")

            assertEquals(1, repository.signUpCalls)
            assertEquals("Иван", repository.lastName)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals("password123", repository.lastPassword)
        }

    @Test
    fun `пробелы в имени и почте удаляются а пароль сохраняется`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            useCase("  Иван  ", "  ivan@example.com  ", " password123 ")

            assertEquals("Иван", repository.lastName)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals(" password123 ", repository.lastPassword)
        }

    @Test
    fun `пустое имя запрещено`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = runCatching { useCase("   ", "ivan@example.com", "password123") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.signUpCalls)
        }

    @Test
    fun `пустая почта запрещена`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = runCatching { useCase("Иван", "   ", "password123") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.signUpCalls)
        }

    @Test
    fun `пустой пароль запрещен`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = runCatching { useCase("Иван", "ivan@example.com", "   ") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.signUpCalls)
        }

    private class FakeAuthRepository : AuthRepository {
        var signUpCalls = 0
        var lastName: String? = null
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun getAuthState(): AuthState = error("Метод не используется в тесте")

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile = error("Метод не используется в тесте")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) {
            signUpCalls++
            lastName = name
            lastEmail = email
            lastPassword = password
        }

        override suspend fun signOut() {
            error("Метод не используется в тесте")
        }
    }
}
