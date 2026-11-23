package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMarkersTest {
    private val center = GeoPoint(56.3269, 44.0059)

    private fun spot(
        index: Int,
        point: GeoPoint,
    ) = Spot(SpotId("osm", "node:$index"), "Площадка", SportType.BASKETBALL, point)

    private fun results(count: Int) =
        List(count) { index ->
            spot(
                index,
                center.copy(latitude = center.latitude + (index / 8 - 3) * 0.001, longitude = center.longitude + (index % 8 - 3) * 0.001),
            )
        }

    @Test
    fun `zero and one result keep their meaning`() {
        assertEquals(emptyList<Spot>(), previewMapSpots(emptyList(), center))
        val single = listOf(spot(1, center))
        assertEquals(single, previewMapSpots(single, center))
    }

    @Test
    fun `21 and 56 results produce at most eight real unique points independent of input order`() {
        for (count in listOf(21, 56)) {
            val input = results(count)
            val chosen = previewMapSpots(input, center)
            assertEquals(8, chosen.size)
            assertEquals(chosen.size, chosen.distinctBy { it.location }.size)
            assertTrue(input.containsAll(chosen))
            assertEquals(chosen, previewMapSpots(input, center))
            assertEquals(chosen, previewMapSpots(input.reversed(), center))
            assertEquals(count, input.size)
        }
    }

    @Test
    fun `coincident points choose a stable real ID and polar points are excluded`() {
        val first = spot(1, center)
        val second = first.copy(id = SpotId("sportspot", "node:1"))
        val polar = spot(2, GeoPoint(90.0, 0.0))
        assertEquals(listOf(first), previewMapSpots(listOf(second, polar, first), center))
        assertEquals(listOf(first), previewMapSpots(List(56) { first }, center))
    }

    @Test
    fun `preview covers distinct areas instead of taking the first API records`() {
        val dense = List(50) { index -> spot(index, center.copy(latitude = center.latitude + index * 0.000001)) }
        val corners =
            listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1).mapIndexed { index, (north, east) ->
                spot(100 + index, center.copy(latitude = center.latitude + north * 0.005, longitude = center.longitude + east * 0.005))
            }
        assertTrue(previewMapSpots(dense + corners, center).containsAll(corners))
    }
}
