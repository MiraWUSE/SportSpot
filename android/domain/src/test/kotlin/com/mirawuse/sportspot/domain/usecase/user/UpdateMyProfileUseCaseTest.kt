
package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMyProfileUseCaseTest {
    @Test
    fun `пользователь успешно изменяет имя`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("Александр")

            assertEquals(
                DomainResult.Success(profile.copy(name = "Александр")),
                result,
            )
            assertEquals("Александр", repository.lastName)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `модератор изменяет имя без изменения роли`() =
        runTest {
            val profile = createProfile(UserRole.MODERATOR)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("Александр")

            assertEquals(
                DomainResult.Success(profile.copy(name = "Александр")),
                result,
            )
            assertEquals(UserRole.MODERATOR, repository.lastUpdatedProfile?.role)
            assertEquals(profile.id, repository.lastUpdatedProfile?.id)
            assertEquals(profile.email, repository.lastUpdatedProfile?.email)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `пробелы вокруг имени удаляются`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("   Александр   ")

            assertEquals(
                DomainResult.Success(profile.copy(name = "Александр")),
                result,
            )
            assertEquals("Александр", repository.lastName)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `пустое имя возвращает Validation`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("   ")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Имя пользователя не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeUserRepository(createProfile(UserRole.USER))
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase("Александр")

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val failure = IllegalStateException("Ошибка обновления профиля")
            val repository = FakeUserRepository(profile, failure)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = runCatching { useCase("Александр") }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.updateCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: UserRepository,
    ): UpdateMyProfileUseCase =
        UpdateMyProfileUseCase(
            authRepository = FakeAuthRepository(state),
            userRepository = repository,
        )

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

    private class FakeUserRepository(
        private val profile: UserProfile,
        private val failure: Throwable? = null,
    ) : UserRepository {
        var updateCalls = 0
        var lastName: String? = null
        var lastUpdatedProfile: UserProfile? = null

        override suspend fun getMyProfile(): UserProfile = error("Метод не используется в тесте")

        override suspend fun updateMyProfile(name: String): UserProfile {
            updateCalls++
            lastName = name

            failure?.let { throw it }

            val updatedProfile = profile.copy(name = name)
            lastUpdatedProfile = updatedProfile

            return updatedProfile
        }
    }
}
