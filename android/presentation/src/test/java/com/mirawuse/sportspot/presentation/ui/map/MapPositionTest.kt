package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.presentation.ui.home.spotSearchCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPositionTest {
    @Test
    fun `search point is at the center of the scheme`() {
        assertEquals(Offset(0.5f, 0.5f), mapPosition(spotSearchCenter))
    }

    @Test
    fun `latitude moves the marker north and longitude moves it east`() {
        val north = mapPosition(spotSearchCenter.copy(latitude = spotSearchCenter.latitude + 0.001))
        val east = mapPosition(spotSearchCenter.copy(longitude = spotSearchCenter.longitude + 0.001))

        assertEquals(0.5f, north.x, 0f)
        assertTrue(north.y < 0.5f)
        assertTrue(east.x > 0.5f)
        assertEquals(0.5f, east.y, 0f)
    }

    @Test
    fun `a location outside the scheme stays within its bounds`() {
        assertEquals(Offset(1f, 0f), mapPosition(GeoPoint(57.0, 45.0)))
    }

    @Test
    fun `overlapping markers use their nearest center and blank space selects nothing`() {
        val mid = Offset(0.5f, 0.5f)
        val east = Offset(0.55f, 0.5f)
        val markers = setOf(mid, east)
        val viewport = IntSize(200, 200)

        assertEquals(east, nearestMarker(markers, Offset(108f, 100f), viewport, radius = 20f))
        assertEquals(mid, nearestMarker(markers, Offset(100f, 100f), viewport, radius = 20f))
        assertNull(nearestMarker(markers, Offset.Zero, viewport, radius = 20f))
    }
}
