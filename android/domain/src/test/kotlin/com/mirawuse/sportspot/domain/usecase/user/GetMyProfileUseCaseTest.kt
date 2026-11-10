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

class GetMyProfileUseCaseTest {
    @Test
    fun `авторизованный пользователь получает свой профиль`() =
        runTest {
            val profile = createProfile(UserRole.USER)
            val userRepository = FakeUserRepository(profile)
            val useCase =
                GetMyProfileUseCase(
                    FakeAuthRepository(AuthState.Authenticated(profile)),
                    userRepository,
                )

            val result = useCase()

            assertEquals(profile, result)
            assertEquals(1, userRepository.getProfileCalls)
        }

    @Test
    fun `модератор получает свой профиль`() =
        runTest {
            val profile = createProfile(UserRole.MODERATOR)
            val userRepository = FakeUserRepository(profile)
            val useCase =
                GetMyProfileUseCase(
                    FakeAuthRepository(AuthState.Authenticated(profile)),
                    userRepository,
                )

            val result = useCase()

            assertEquals(UserRole.MODERATOR, result.role)
            assertEquals(1, userRepository.getProfileCalls)
        }

    @Test
    fun `гость не может получить профиль`() =
        runTest {
            val userRepository = FakeUserRepository(createProfile(UserRole.USER))
            val useCase =
                GetMyProfileUseCase(
                    FakeAuthRepository(AuthState.Guest),
                    userRepository,
                )

            val result = runCatching { useCase() }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, userRepository.getProfileCalls)
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
        var getProfileCalls = 0

        override suspend fun getMyProfile(): UserProfile {
            getProfileCalls++
            return profile
        }

        override suspend fun updateMyProfile(name: String): UserProfile = error("Метод не используется в тесте")
    }
}
