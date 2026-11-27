package com.mirawuse.sportspot.domain.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoPointTest {
    @Test
    fun `корректные координаты сохраняются`() {
        val point =
            GeoPoint(
                latitude = 56.3269,
                longitude = 44.0059,
            )

        assertEquals(56.3269, point.latitude, 0.0)
        assertEquals(44.0059, point.longitude, 0.0)
    }

    @Test
    fun `граничные значения координат допустимы`() {
        val point = GeoPoint(90.0, 180.0)

        assertEquals(90.0, point.latitude, 0.0)
        assertEquals(180.0, point.longitude, 0.0)
    }

    @Test
    fun distanceIsZeroAtTheSamePointAndFiniteAtThePoles() {
        val first = GeoPoint(56.3269, 44.0059)
        assertEquals(0.0, first.distanceMetersTo(first), 0.0)
        assertEquals(0.0, GeoPoint(90.0, 0.0).distanceMetersTo(GeoPoint(90.0, 180.0)), 0.000001)
        assertEquals(Math.PI * 6_371_000, GeoPoint(90.0, 0.0).distanceMetersTo(GeoPoint(-90.0, 180.0)), 0.001)
    }

    @Test
    fun distanceFollowsTheShortArcAcrossTheDateLine() {
        val east = GeoPoint(0.0, 179.999)
        val west = GeoPoint(0.0, -179.999)
        assertEquals(222.39, east.distanceMetersTo(west), 0.01)
        assertEquals(east.distanceMetersTo(west), west.distanceMetersTo(east), 0.000001)
        assertEquals(0.0, GeoPoint(0.0, 180.0).distanceMetersTo(GeoPoint(0.0, -180.0)), 0.0)
    }

    @Test
    fun `неправильная широта вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(91.0, 44.0)
        }
    }

    @Test
    fun `неправильная долгота вызывает ошибку`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(56.0, 181.0)
        }
    }

    @Test
    fun `одинаковые координаты создают равные объекты`() {
        val first = GeoPoint(56.0, 44.0)
        val second = GeoPoint(56.0, 44.0)

        assertEquals(first, second)
    }
}
