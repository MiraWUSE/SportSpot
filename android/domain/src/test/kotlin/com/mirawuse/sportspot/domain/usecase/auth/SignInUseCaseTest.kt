
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
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

            assertEquals(DomainResult.Success(profile), result)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals("password123", repository.lastPassword)
            assertEquals(1, repository.signInCalls)
        }

    @Test
    fun `модератор успешно входит с сохранением роли`() =
        runTest {
            val profile = createProfile(UserRole.MODERATOR)
            val repository = FakeAuthRepository(profile)
            val useCase = SignInUseCase(repository)

            val result = useCase("ivan@example.com", "password123")

            assertEquals(DomainResult.Success(profile), result)
            assertEquals(UserRole.MODERATOR, profile.role)
            assertTrue(profile.isModerator())
            assertEquals(1, repository.signInCalls)
        }

    @Test
    fun `пробелы вокруг электронной почты удаляются`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            val result = useCase("  ivan@example.com  ", "password123")

            assertTrue(result is DomainResult.Success)
            assertEquals("ivan@example.com", repository.lastEmail)
            assertEquals(1, repository.signInCalls)
        }

    @Test
    fun `пробелы в пароле сохраняются`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            val result = useCase("ivan@example.com", " password123 ")

            assertTrue(result is DomainResult.Success)
            assertEquals(" password123 ", repository.lastPassword)
            assertEquals(1, repository.signInCalls)
        }

    @Test
    fun `пустая электронная почта возвращает Validation`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            val result = useCase("   ", "password123")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Электронная почта не может быть пустой",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.signInCalls)
        }

    @Test
    fun `пустой пароль возвращает Validation`() =
        runTest {
            val repository = FakeAuthRepository(createProfile(UserRole.USER))
            val useCase = SignInUseCase(repository)

            val result = useCase("ivan@example.com", "   ")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Пароль не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.signInCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка авторизации")
            val repository =
                FakeAuthRepository(
                    profile = createProfile(UserRole.USER),
                    failure = failure,
                )
            val useCase = SignInUseCase(repository)

            val result =
                runCatching {
                    useCase("ivan@example.com", "password123")
                }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.signInCalls)
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
        private val failure: Throwable? = null,
    ) : AuthRepository {
        var signInCalls = 0
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun getAuthState(): AuthState = error("Метод не используется в тесте")

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile {
            signInCalls++
            lastEmail = email
            lastPassword = password

            failure?.let { throw it }

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
