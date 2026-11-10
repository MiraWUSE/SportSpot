package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMyProfileUseCaseTest {
    @Test
    fun `авторизованный пользователь может изменить имя`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("Александр")

            assertEquals("Александр", result.name)
            assertEquals(profile.id, result.id)
            assertEquals(profile.email, result.email)
            assertEquals(profile.role, result.role)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `модератор может изменить свое имя`() =
        runTest {
            val profile = createProfile(UserRole.MODERATOR)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("Александр")

            assertEquals("Александр", result.name)
            assertEquals(UserRole.MODERATOR, result.role)
            assertEquals(1, repository.updateCalls)
        }

    @Test
    fun `пробелы вокруг имени удаляются`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = useCase("   Александр   ")

            assertEquals("Александр", result.name)
            assertEquals("Александр", repository.lastName)
        }

    @Test
    fun `пустое имя запрещено`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val repository = FakeUserRepository(profile)
            val useCase = createUseCase(AuthState.Authenticated(profile), repository)

            val result = runCatching { useCase("   ") }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.updateCalls)
        }

    @Test
    fun `гость не может изменить профиль`() =
        runTest {
            val repository = FakeUserRepository(createProfile(UserRole.USER))
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = runCatching { useCase("Александр") }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.updateCalls)
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
    ) : UserRepository {
        var updateCalls = 0
        var lastName: String? = null

        override suspend fun getMyProfile(): UserProfile = error("Метод не используется в тесте")

        override suspend fun updateMyProfile(name: String): UserProfile {
            updateCalls++
            lastName = name

            return profile.copy(name = name)
        }
    }
}
