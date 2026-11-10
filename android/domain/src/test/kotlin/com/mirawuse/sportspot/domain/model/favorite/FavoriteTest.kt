package com.mirawuse.sportspot.domain.model.favorite

import com.mirawuse.sportspot.domain.model.spot.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FavoriteTest {
    @Test
    fun `избранное создается с корректными данными`() {
        val favorite =
            Favorite(
                userId = "user-123",
                spotId = SpotId("osm", "12345"),
                addedAt = "2026-11-10T10:00:00Z",
            )

        assertEquals("user-123", favorite.userId)
        assertEquals("12345", favorite.spotId.value)
        assertEquals("2026-11-10T10:00:00Z", favorite.addedAt)
    }

    @Test
    fun `пустой идентификатор пользователя вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            Favorite(
                userId = "",
                spotId = SpotId("osm", "12345"),
                addedAt = "2026-11-10T10:00:00Z",
            )
        }
    }

    @Test
    fun `пустая дата добавления вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            Favorite(
                userId = "user-123",
                spotId = SpotId("osm", "12345"),
                addedAt = "",
            )
        }
    }

    @Test
    fun `одинаковые записи избранного равны`() {
        val first = createFavorite()
        val second = createFavorite()

        assertEquals(first, second)
    }

    @Test
    fun `избранное разных пользователей отличается`() {
        val first = createFavorite()
        val second = createFavorite(userId = "user-456")

        assertNotEquals(first, second)
    }

    private fun createFavorite(userId: String = "user-123"): Favorite =
        Favorite(
            userId = userId,
            spotId = SpotId("osm", "12345"),
            addedAt = "2026-11-10T10:00:00Z",
        )
}
