package com.mirawuse.sportspot.domain.model.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotTest {
    @Test
    fun `площадка создается с корректными данными`() {
        val spot =
            Spot(
                id = SpotId("osm", "12345"),
                name = "Футбольное поле",
                sportType = SportType.FOOTBALL,
                location = GeoPoint(56.3269, 44.0059),
                surface = "grass",
                lighting = true,
            )

        assertEquals("Футбольное поле", spot.name)
        assertEquals(SportType.FOOTBALL, spot.sportType)
        assertEquals("grass", spot.surface)
        assertTrue(spot.lighting == true)
    }

    @Test
    fun `необязательные характеристики могут отсутствовать`() {
        val spot =
            Spot(
                id = SpotId("osm", "123"),
                name = "Спортивная площадка",
                sportType = SportType.OTHER,
                location = GeoPoint(56.0, 44.0),
            )

        assertNull(spot.surface)
        assertNull(spot.lighting)
    }

    @Test
    fun `пустое название вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            Spot(
                id = SpotId("osm", "123"),
                name = "",
                sportType = SportType.FOOTBALL,
                location = GeoPoint(56.0, 44.0),
            )
        }
    }

    @Test
    fun `площадка поддерживает свой вид спорта`() {
        val spot =
            Spot(
                id = SpotId("osm", "123"),
                name = "Баскетбольная площадка",
                sportType = SportType.BASKETBALL,
                location = GeoPoint(56.0, 44.0),
            )

        assertTrue(spot.supportsSport(SportType.BASKETBALL))
    }

    @Test
    fun `площадка не поддерживает другой вид спорта`() {
        val spot =
            Spot(
                id = SpotId("osm", "123"),
                name = "Баскетбольная площадка",
                sportType = SportType.BASKETBALL,
                location = GeoPoint(56.0, 44.0),
            )

        assertFalse(spot.supportsSport(SportType.FOOTBALL))
    }
}
