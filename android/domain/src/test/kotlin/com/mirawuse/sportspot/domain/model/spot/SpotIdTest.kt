package com.mirawuse.sportspot.domain.model.spot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpotIdTest {
    @Test
    fun `корректный идентификатор создается`() {
        val spotId = SpotId("osm", "12345")

        assertEquals("osm", spotId.source)
        assertEquals("12345", spotId.value)
    }

    @Test
    fun `пустой источник вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotId("", "12345")
        }
    }

    @Test
    fun `пустое значение вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotId("osm", "")
        }
    }

    @Test
    fun `источник из пробелов вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotId("   ", "12345")
        }
    }

    @Test
    fun `одинаковые идентификаторы равны`() {
        val first = SpotId("osm", "12345")
        val second = SpotId("osm", "12345")

        assertEquals(first, second)
    }
}
