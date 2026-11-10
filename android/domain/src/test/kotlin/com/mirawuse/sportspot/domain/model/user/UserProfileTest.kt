package com.mirawuse.sportspot.domain.model.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {
    @Test
    fun `профиль создается с корректными данными`() {
        val profile =
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = UserRole.USER,
            )

        assertEquals("user-123", profile.id)
        assertEquals("Иван", profile.name)
        assertEquals("ivan@example.com", profile.email)
        assertEquals(UserRole.USER, profile.role)
    }

    @Test
    fun `модератор определяется правильно`() {
        val profile =
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = UserRole.MODERATOR,
            )

        assertTrue(profile.isModerator())
    }

    @Test
    fun `обычный пользователь не является модератором`() {
        val profile =
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = UserRole.USER,
            )

        assertFalse(profile.isModerator())
    }

    @Test
    fun `пустой идентификатор вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserProfile(
                id = "",
                name = "Иван",
                email = "ivan@example.com",
                role = UserRole.USER,
            )
        }
    }

    @Test
    fun `пустое имя вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserProfile(
                id = "user-123",
                name = "",
                email = "ivan@example.com",
                role = UserRole.USER,
            )
        }
    }

    @Test
    fun `пустая почта вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "",
                role = UserRole.USER,
            )
        }
    }
}
