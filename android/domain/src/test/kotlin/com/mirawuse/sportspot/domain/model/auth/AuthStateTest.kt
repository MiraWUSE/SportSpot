package com.mirawuse.sportspot.domain.model.auth

import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateTest {
    @Test
    fun `гость не является авторизованным пользователем`() {
        val state: AuthState = AuthState.Guest

        assertFalse(state is AuthState.Authenticated)
    }

    @Test
    fun `авторизованный пользователь содержит свой профиль`() {
        val profile =
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = UserRole.USER,
            )

        val state = AuthState.Authenticated(profile)

        assertEquals(profile, state.profile)
        assertEquals(UserRole.USER, state.profile.role)
    }

    @Test
    fun `авторизованный модератор сохраняет свою роль`() {
        val profile =
            UserProfile(
                id = "moderator-123",
                name = "Модератор",
                email = "moderator@example.com",
                role = UserRole.MODERATOR,
            )

        val state = AuthState.Authenticated(profile)

        assertTrue(state.profile.isModerator())
    }

    @Test
    fun `гость и авторизованный пользователь разные состояния`() {
        val profile =
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = UserRole.USER,
            )

        val guest: AuthState = AuthState.Guest
        val authenticated: AuthState = AuthState.Authenticated(profile)

        assertFalse(guest == authenticated)
    }
}
