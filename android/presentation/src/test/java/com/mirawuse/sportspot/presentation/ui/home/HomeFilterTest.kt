package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.spot.SpotSport
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFilterTest {
    private val football = Spot(SpotId("osm", "way:1"), "Стадион", SportType.FOOTBALL, GeoPoint(56.3269, 44.0059))
    private val basketball = football.copy(id = SpotId("osm", "way:2"), name = "Площадка", sportType = SportType.BASKETBALL)
    private val other = football.copy(id = SpotId("osm", "node:3"), name = "Корт", sportType = SportType.OTHER)
    private val spots = listOf(football, basketball, other)
    private val labels =
        mapOf(
            R.string.sport_football to "Футбол",
            R.string.sport_basketball to "Баскетбол",
            R.string.sport_other to "Другой вид спорта",
            R.string.sport_ice_hockey to "Хоккей с шайбой",
            R.string.sport_table_tennis to "Настольный теннис",
            R.string.sport_unspecified to "Вид спорта не указан",
        )

    private fun sportLabel(spot: Spot): String = spot.sportLabel(labels::getValue)

    @Test
    fun `text matches the name or localized sport regardless of case`() {
        assertEquals(listOf(football), filterHomeSpots(spots, " СТАД ", null, ::sportLabel))
        assertEquals(listOf(basketball), filterHomeSpots(spots, "БАСКЕТ", null, ::sportLabel))
    }

    @Test
    fun `category and query are both applied`() {
        assertEquals(listOf(basketball), filterHomeSpots(spots, "площадка", SportType.BASKETBALL, ::sportLabel))
        assertTrue(filterHomeSpots(spots, "площадка", SportType.FOOTBALL, ::sportLabel).isEmpty())
    }

    @Test
    fun `compound sports are searchable by each localized sport and compatible category`() {
        val mixed = other.copy(sports = listOf(SpotSport.FOOTBALL, SpotSport.ICE_HOCKEY))
        val table = other.copy(id = SpotId("osm", "node:4"), sports = listOf(SpotSport.TABLE_TENNIS))
        val candidates = listOf(mixed, table)

        assertEquals(listOf(mixed), filterHomeSpots(candidates, "ХоКкЕй", SportType.FOOTBALL, ::sportLabel))
        assertEquals(listOf(table), filterHomeSpots(candidates, "настольный теннис", null, ::sportLabel))
        assertTrue(filterHomeSpots(candidates, "", SportType.TENNIS, ::sportLabel).isEmpty())
    }

    @Test
    fun `missing sport is distinct from unsupported sport`() {
        val missing = football.copy(sportType = SportType.OTHER, sports = emptyList())
        val unsupported = other.copy(sports = listOf(SpotSport.OTHER))
        val candidates = listOf(missing, unsupported)

        assertEquals(listOf(missing), filterHomeSpots(candidates, "не указан", null, ::sportLabel))
        assertEquals(listOf(unsupported), filterHomeSpots(candidates, "другой", null, ::sportLabel))
    }

    @Test
    fun `clearing filters includes every sport from the original snapshot`() {
        assertEquals(spots, filterHomeSpots(spots, " ", null, ::sportLabel))
    }
}
