package com.mirawuse.sportspot.presentation.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.filterSpots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapSpotVisibilityTest {
    private val size = IntSize(100, 50)
    private val court = Spot(SpotId("osm", "node:1"), "Корт", SportType.TENNIS, GeoPoint(20.0, 10.0))

    @Test
    fun screenIncludesItsEdgesAndRejectsOffscreenOrInvalidCoordinates() {
        val edge = court.copy(id = SpotId("osm", "node:2"), location = GeoPoint(50.0, 100.0))
        val outside = court.copy(id = SpotId("osm", "node:3"), location = GeoPoint(20.0, 100.01))
        val pole = court.copy(id = SpotId("osm", "node:4"), location = GeoPoint(89.0, 10.0))
        val ids =
            projectedSpotIds(listOf(court, edge, outside, pole), size) { point ->
                check(point != pole.location)
                Offset(point.longitude.toFloat(), point.latitude.toFloat())
            }
        assertEquals(setOf(court.id, edge.id), ids)
        assertFalse(Offset(Float.NaN, 0f).isInMap(size))
        assertFalse(Offset.Zero.isInMap(IntSize.Zero))
    }

    @Test
    fun visibilityIsRecalculatedAfterFilteringAndDistinguishesCoincidentIds() {
        val field = court.copy(id = SpotId("community", court.id.value), sportType = SportType.FOOTBALL)
        val duplicate = court.copy(name = "Корт с другим названием")
        val spots = listOf(court, field, duplicate)
        val filter = SearchFilterState("Корт", SportType.TENNIS)
        val filtered = filterSpots(spots, filter) { it.sportType.name }
        assertEquals(setOf(court.id, field.id), projectedSpotIds(spots, size) { Offset.Zero })
        assertEquals(setOf(court.id), projectedSpotIds(filtered, size) { Offset.Zero })
        assertEquals(setOf(field.id), projectedSpotIds(listOf(field), size) { Offset.Zero })
        assertEquals(emptySet<SpotId>(), projectedSpotIds(filtered, size) { Offset(-1f, 0f) })
    }
}
