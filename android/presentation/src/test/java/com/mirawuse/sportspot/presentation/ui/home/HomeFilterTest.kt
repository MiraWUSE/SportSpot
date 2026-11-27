package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.spot.SpotSport
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import com.mirawuse.sportspot.presentation.ui.search.LightingFilter
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.SpotSort
import com.mirawuse.sportspot.presentation.ui.search.SurfaceFilter
import com.mirawuse.sportspot.presentation.ui.search.filterSpots
import com.mirawuse.sportspot.presentation.ui.search.sortSpots
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
            R.string.sport_hockey to "Хоккей",
            R.string.sport_table_tennis to "Настольный теннис",
            R.string.sport_unspecified to "Вид спорта не указан",
        )

    private fun sportLabel(spot: Spot): String = spot.sportLabel(labels::getValue)

    @Test
    fun `default preserves source order and Russian names sort without case`() {
        val lower = football.copy(name = "арена")
        val upper = football.copy(id = SpotId("community", football.id.value), name = "АРЕНА")
        val beta = basketball.copy(name = "База")
        val last = other.copy(name = "Яхт-клуб")
        val candidates = listOf(last, lower, beta, upper)
        assertEquals(candidates, sortSpots(candidates, SpotSort.DEFAULT, football.location))
        assertEquals(listOf(upper, lower, beta, last), sortSpots(candidates, SpotSort.NAME_ASC, football.location))
    }

    @Test
    fun `nearest uses both coordinates finds the closest center and breaks ties by id`() {
        val center = GeoPoint(56.0, 44.0)
        val east = football.copy(location = GeoPoint(56.0, 44.003))
        val samePoint = basketball.copy(id = SpotId("community", east.id.value), location = east.location)
        val north = other.copy(location = GeoPoint(56.002, 44.0))
        val atCenter = football.copy(id = SpotId("osm", "way:0"), location = center)
        val candidates = listOf(north, east, atCenter, samePoint)
        assertEquals(listOf(atCenter, samePoint, east, north), sortSpots(candidates, SpotSort.NEAREST, center))
        assertEquals(north, sortSpots(candidates, SpotSort.NEAREST, north.location).first())
    }

    @Test
    fun `nearest finds a point across the antimeridian first`() {
        val center = GeoPoint(0.0, 179.999)
        val west = football.copy(location = GeoPoint(0.0, -179.999))
        val east = basketball.copy(location = GeoPoint(0.0, 179.98))
        assertEquals(listOf(west, east), sortSpots(listOf(east, west), SpotSort.NEAREST, center))
    }

    @Test
    fun `lighting distinguishes known positive negative and missing values`() {
        val lit = football.copy(lighting = true)
        val unlit = basketball.copy(lighting = false)
        val candidates = listOf(lit, unlit, other)
        val expected =
            mapOf(
                LightingFilter.ALL to candidates,
                LightingFilter.LIT to listOf(lit),
                LightingFilter.UNLIT to listOf(unlit),
                LightingFilter.UNKNOWN to listOf(other),
            )
        for ((filter, matching) in expected) {
            assertEquals(matching, filterSpots(candidates, SearchFilterState(lighting = filter), ::sportLabel))
        }
    }

    @Test
    fun `surfaces recognize real tokens and keep unclassified values separate`() {
        val cases =
            mapOf(
                " Rubber " to setOf(SurfaceFilter.RUBBER),
                "asphalt" to setOf(SurfaceFilter.HARD),
                "paved; Concrete:Plates" to setOf(SurfaceFilter.HARD),
                "sand ; ARTIFICIAL_TURF ; " to setOf(SurfaceFilter.SAND, SurfaceFilter.GRASS),
                "grass; asphalt" to setOf(SurfaceFilter.GRASS, SurfaceFilter.HARD),
                "clay; unknown" to setOf(SurfaceFilter.OTHER),
                "unknown;asphalt" to setOf(SurfaceFilter.HARD),
                " " to setOf(SurfaceFilter.UNKNOWN),
                null to setOf(SurfaceFilter.UNKNOWN),
            )
        for ((surface, expected) in cases) {
            val candidates = listOf(football.copy(surface = surface))
            val groups =
                SurfaceFilter.entries
                    .filter { group ->
                        filterSpots(candidates, SearchFilterState(surface = group), ::sportLabel).isNotEmpty()
                    }.toSet()
            assertEquals(surface, expected + SurfaceFilter.ALL, groups)
        }
    }

    @Test
    fun `all filters combine before sorting and reset restores the source list`() {
        val z = basketball.copy(name = "Яркая площадка", lighting = true, surface = "rubber")
        val a = z.copy(id = SpotId("community", z.id.value), name = "Арена", address = "Улица Площадка", surface = "grass;rubber")
        val candidates =
            listOf(
                z,
                z.copy(id = other.id, lighting = null),
                z.copy(id = SpotId("osm", "way:5"), surface = "concrete"),
                z.copy(id = SpotId("osm", "way:6"), name = "Стадион"),
                a,
                football.copy(name = "Площадка", lighting = true, surface = "rubber"),
            )
        val filter = SearchFilterState("площадка", SportType.BASKETBALL, SpotSort.NAME_ASC, LightingFilter.LIT, SurfaceFilter.RUBBER)
        val matching = filterSpots(candidates, filter, ::sportLabel)
        assertEquals(listOf(a, z), sortSpots(matching, filter.sort, football.location))
        assertEquals(candidates, filterSpots(candidates, SearchFilterState(), ::sportLabel))
    }

    @Test
    fun `text matches the name or localized sport regardless of case`() {
        assertEquals(listOf(football), filterSpots(spots, SearchFilterState(" СТАД ", null), ::sportLabel))
        assertEquals(listOf(basketball), filterSpots(spots, SearchFilterState("БАСКЕТ", null), ::sportLabel))
    }

    @Test
    fun `category and query are both applied`() {
        assertEquals(listOf(basketball), filterSpots(spots, SearchFilterState("площадка", SportType.BASKETBALL), ::sportLabel))
        assertTrue(filterSpots(spots, SearchFilterState("площадка", SportType.FOOTBALL), ::sportLabel).isEmpty())
    }

    @Test
    fun `compound sports are searchable by each localized sport and compatible category`() {
        val mixed = other.copy(sports = listOf(SpotSport.FOOTBALL, SpotSport.ICE_HOCKEY))
        val table = other.copy(id = SpotId("osm", "node:4"), sports = listOf(SpotSport.TABLE_TENNIS))
        val hockey = other.copy(id = SpotId("osm", "node:5"), sports = listOf(SpotSport.HOCKEY))
        val candidates = listOf(mixed, table, hockey)
        assertEquals(listOf(mixed, hockey), filterSpots(candidates, SearchFilterState("хоккей"), ::sportLabel))

        assertEquals(listOf(mixed), filterSpots(candidates, SearchFilterState("ХоКкЕй", SportType.FOOTBALL), ::sportLabel))
        assertEquals(listOf(table), filterSpots(candidates, SearchFilterState("настольный теннис", null), ::sportLabel))
        assertTrue(filterSpots(candidates, SearchFilterState("", SportType.TENNIS), ::sportLabel).isEmpty())
    }

    @Test
    fun `missing sport is distinct from unsupported sport`() {
        val missing = football.copy(sportType = SportType.OTHER, sports = emptyList())
        val unsupported = other.copy(sports = listOf(SpotSport.OTHER))
        val candidates = listOf(missing, unsupported)

        assertEquals(listOf(missing), filterSpots(candidates, SearchFilterState("не указан", null), ::sportLabel))
        assertEquals(listOf(unsupported), filterSpots(candidates, SearchFilterState("другой", null), ::sportLabel))
    }

    @Test
    fun `clearing filters includes every sport from the original snapshot`() {
        assertEquals(spots, filterSpots(spots, SearchFilterState(" ", null), ::sportLabel))
    }

    @Test
    fun `matches an available address without inventing a missing address`() {
        val addressed = basketball.copy(address = "Красная улица, 14")
        val candidates = listOf(football, addressed, other.copy(address = ""))
        assertEquals(listOf(addressed), filterSpots(candidates, SearchFilterState(" УЛИЦА, 14 "), ::sportLabel))
        assertTrue(filterSpots(candidates, SearchFilterState("адрес не указан"), ::sportLabel).isEmpty())
    }

    @Test
    fun `keeps identical names and values from different sources in input order`() {
        val community = football.copy(id = SpotId("community", football.id.value))
        val candidates = listOf(community, basketball, football)
        assertEquals(listOf(community, football), filterSpots(candidates, SearchFilterState("стадион"), ::sportLabel))
    }
}
