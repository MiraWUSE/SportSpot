package com.mirawuse.sportspot.presentation.ui.details

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotDetailsTest {
    private val node = Spot(SpotId("osm", "node:123"), "Площадка", SportType.TENNIS, GeoPoint(56.3269, 44.0059))
    private val way = node.copy(id = SpotId("osm", "way:123"), name = "Футбольное поле", sportType = SportType.FOOTBALL)
    private val content = HomeUiState.Content(listOf(node, way))

    @Test
    fun `details find the exact OSM object even when numeric identifiers match`() {
        assertEquals(way, content.findSpot(SpotId("osm", "way:123")))
        assertEquals(node, content.findSpot(SpotId("osm", "node:123")))
    }

    @Test
    fun `details do not substitute another place for a missing ID`() {
        assertNull(content.findSpot(SpotId("osm", "relation:123")))
    }

    @Test
    fun `lighting preserves all three values`() {
        assertEquals(R.string.details_lighting_yes, lightingLabelRes(true))
        assertEquals(R.string.details_lighting_no, lightingLabelRes(false))
        assertEquals(R.string.details_unknown, lightingLabelRes(null))
    }
}
