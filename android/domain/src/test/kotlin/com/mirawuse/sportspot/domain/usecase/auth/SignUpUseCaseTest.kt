
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpUseCaseTest {
    @Test
    fun `пользователь успешно регистрируется`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = useCase("Иван", "ivan@example.com", "password123")

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(1, repository.signUpCalls)
            assertEquals("Иван", repository.lastName)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals("password123", repository.lastPassword)
        }

    @Test
    fun `пробелы вокруг имени и почты удаляются`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result =
                useCase(
                    "  Иван  ",
                    "  ivan@example.com  ",
                    "password123",
                )

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals("Иван", repository.lastName)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals(1, repository.signUpCalls)
        }

    @Test
    fun `пробелы в пароле сохраняются`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result =
                useCase(
                    "Иван",
                    "ivan@example.com",
                    " password123 ",
                )

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(" password123 ", repository.lastPassword)
            assertEquals(1, repository.signUpCalls)
        }

    @Test
    fun `пустое имя возвращает Validation`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = useCase("   ", "ivan@example.com", "password123")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Имя пользователя не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.signUpCalls)
        }

    @Test
    fun `пустая электронная почта возвращает Validation`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = useCase("Иван", "   ", "password123")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Электронная почта не может быть пустой",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.signUpCalls)
        }

    @Test
    fun `пустой пароль возвращает Validation`() =
        runTest {
            val repository = FakeAuthRepository()
            val useCase = SignUpUseCase(repository)

            val result = useCase("Иван", "ivan@example.com", "   ")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Пароль не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.signUpCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка регистрации")
            val repository = FakeAuthRepository(failure)
            val useCase = SignUpUseCase(repository)

            val result =
                runCatching {
                    useCase("Иван", "ivan@example.com", "password123")
                }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.signUpCalls)
        }

    private class FakeAuthRepository(
        private val failure: Throwable? = null,
    ) : AuthRepository {
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

            failure?.let { throw it }
        }

        override suspend fun signOut() {
            error("Метод не используется в тесте")
        }
    }
}
