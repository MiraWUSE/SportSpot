
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

class GetMyProfileUseCaseTest {
    @Test
    fun `пользователь успешно получает свой профиль`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase()

            assertEquals(DomainResult.Success(profile), result)
            assertEquals(1, repository.getProfileCalls)
        }

    @Test
    fun `модератор успешно получает свой профиль`() =
        runTest {
            val profile = createProfile(UserRole.MODERATOR)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase()

            assertEquals(DomainResult.Success(profile), result)
            assertEquals(UserRole.MODERATOR, profile.role)
            assertEquals(1, repository.getProfileCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase()

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.getProfileCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка загрузки профиля")
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile, failure)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = runCatching { useCase() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.getProfileCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: UserRepository,
    ): GetMyProfileUseCase =
        GetMyProfileUseCase(
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
        var getProfileCalls = 0

        override suspend fun getMyProfile(): UserProfile {
            getProfileCalls++
            failure?.let { throw it }
            return profile
        }

        override suspend fun updateMyProfile(name: String): UserProfile = error("Метод не используется в тесте")
    }
}
