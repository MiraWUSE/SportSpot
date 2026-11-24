package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {
    @Test
    fun computesOneBoundedRequestFromActualCornersOrPausesAtRegionalScale() {
        val tiny = area(56.327, 44.006, 0.0001, 19.0)
        assertEquals(100, tiny.request?.radiusMeters)
        val local = area(56.327, 44.006, 0.009, 14.0)
        assertEquals(1200, local.request?.radiusMeters)
        assertFalse(local.limited)
        val wide = area(56.327, 44.006, 0.04, 12.0)
        assertEquals(3000, wide.request?.radiusMeters)
        assertTrue(wide.limited)
        assertNull(area(56.327, 44.006, 0.2, 10.0).request)
        assertNull(area(0.0, 0.0, 0.1, 1.0).request)
    }

    @Test
    fun wrapsTheDateLineAndKeepsHighLatitudeRequestsInsideMercator() {
        val acrossDateLine = area(84.999, 179.9998, 0.002, 15.5)
        assertEquals(-180.0, acrossDateLine.request?.center?.longitude)
        assertEquals(300, acrossDateLine.request?.radiusMeters)
        val north = area(85.05112, 10.0, 0.0001, 19.0)
        assertTrue(north.request!!.center.latitude < 85.05112878)
        assertNull(mapViewport(GeoPoint(86.0, 10.0), List(4) { GeoPoint(85.0, 10.0) }, 15.0))
    }

    @Test
    fun ignoresJitterButDistinguishesAnotherDistrictAndMeaningfulZoom() {
        val original = area(56.327, 44.006, 0.01, 14.0)
        val jitter = area(56.3271, 44.0061, 0.0101, 14.0)
        assertEquals(original.request, jitter.request)
        assertTrue(original.isSimilarTo(jitter))
        assertFalse(original.isSimilarTo(area(56.35, 44.006, 0.01, 14.0)))
        assertFalse(original.isSimilarTo(area(56.327, 44.006, 0.005, 15.0)))
    }

    private fun area(
        latitude: Double,
        longitude: Double,
        step: Double,
        zoom: Double,
    ): MapViewport {
        val center = GeoPoint(latitude, longitude)
        val corners =
            listOf(-step, step).flatMap { lat ->
                listOf(-step, step).map { lon -> GeoPoint(latitude + lat, wrappedMapLongitude(longitude + lon)) }
            }
        return checkNotNull(mapViewport(center, corners, zoom))
    }
}
