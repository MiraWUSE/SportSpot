package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFilterTest {
    private val football = Spot(SpotId("osm", "way:1"), "Стадион", SportType.FOOTBALL, GeoPoint(56.3269, 44.0059))
    private val basketball = football.copy(id = SpotId("osm", "way:2"), name = "Площадка", sportType = SportType.BASKETBALL)
    private val other = football.copy(id = SpotId("osm", "node:3"), name = "Корт", sportType = SportType.OTHER)
    private val spots = listOf(football, basketball, other)
    private val labels = mapOf(SportType.FOOTBALL to "Футбол", SportType.BASKETBALL to "Баскетбол", SportType.OTHER to "Другой вид спорта")

    @Test
    fun `text matches the name or localized sport regardless of case`() {
        assertEquals(listOf(football), filterHomeSpots(spots, " СТАД ", null, labels::getValue))
        assertEquals(listOf(basketball), filterHomeSpots(spots, "БАСКЕТ", null, labels::getValue))
    }

    @Test
    fun `category and query are both applied`() {
        assertEquals(listOf(basketball), filterHomeSpots(spots, "площадка", SportType.BASKETBALL, labels::getValue))
        assertTrue(filterHomeSpots(spots, "площадка", SportType.FOOTBALL, labels::getValue).isEmpty())
    }

    @Test
    fun `clearing filters includes every sport from the original snapshot`() {
        assertEquals(spots, filterHomeSpots(spots, " ", null, labels::getValue))
    }
}
