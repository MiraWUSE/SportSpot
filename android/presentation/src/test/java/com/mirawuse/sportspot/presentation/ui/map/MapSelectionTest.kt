package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.filterSpots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MapSelectionTest {
    private val center = GeoPoint(56.3269, 44.0059)
    private val first = Spot(SpotId("osm", "node:1"), "Корт", SportType.BASKETBALL, center)
    private val second = first.copy(id = SpotId("osm", "way:1"))
    private val otherSource = first.copy(id = SpotId("sportspot", "node:1"))
    private val far = first.copy(id = SpotId("osm", "node:2"), location = GeoPoint(48.8566, 2.3522))
    private val spots = listOf(first, second, otherSource, far)

    @Test
    fun `grouping keeps exact coordinates and all typed IDs without clamping distant spots`() {
        val groups = mapMarkerGroups(spots)
        assertEquals(2, groups.size)
        assertEquals(listOf(first.id, second.id, otherSource.id), groups.getValue(center).map { it.id })
        assertEquals(listOf(far), groups.getValue(far.location))
    }

    @Test
    fun `coincident marker cycles IDs and selection follows ID after reordering`() {
        assertEquals(first.id, nextMapSpot(spots, first.id, far.id))
        assertEquals(second.id, nextMapSpot(spots, first.id, first.id))
        assertEquals(otherSource.id, nextMapSpot(spots, first.id, second.id))
        assertEquals(first.id, nextMapSpot(spots, first.id, otherSource.id))
        assertEquals(second, selectedMapSpot(spots.reversed(), second.id))
        assertNull(nextMapSpot(spots, SpotId("osm", "missing"), first.id))
    }

    @Test
    fun `missing selection and empty results have safe fallbacks`() {
        assertEquals(first, selectedMapSpot(spots, null))
        assertEquals(first, selectedMapSpot(spots, SpotId("osm", "missing")))
        assertNull(selectedMapSpot(emptyList(), first.id))
    }

    @Test
    fun `filter leaves only visible full IDs available to markers and selection`() {
        val tennis = otherSource.copy(sportType = SportType.TENNIS)
        val matches = filterSpots(listOf(first, tennis), SearchFilterState(sport = SportType.TENNIS)) { "" }
        assertEquals(listOf(tennis), matches)
        assertEquals(tennis, selectedMapSpot(matches, first.id))
        assertEquals(listOf(tennis), mapMarkerGroups(matches).getValue(center))
        assertNull(nextMapSpot(matches, first.id, tennis.id))
        val noMatches = filterSpots(matches, SearchFilterState("missing")) { "" }
        assertNull(selectedMapSpot(noMatches, tennis.id))
    }

    @Test
    fun `initial camera uses actual center or exact focused spot`() {
        assertEquals(MapCameraTarget(center, 13.5), mapCameraTarget(center, null, null, null, false))
        assertEquals(MapCameraTarget(far.location, 15.5), mapCameraTarget(center, far, null, null, false))
        assertEquals(MapCameraTarget(center, 12.0), mapCameraTarget(center, null, null, null, false, 12.0))
    }

    @Test
    fun `restoration preserves pan while GPS or new focus can move the camera`() {
        assertNull(mapCameraTarget(center, first, mapCenterKey(center), mapFocusKey(first), true))
        assertEquals(
            MapCameraTarget(far.location, 13.5),
            mapCameraTarget(far.location, first, mapCenterKey(center), mapFocusKey(first), true),
        )
        assertEquals(MapCameraTarget(far.location, 15.5), mapCameraTarget(center, far, mapCenterKey(center), mapFocusKey(first), true))
    }

    @Test
    fun `invalid coordinates and poles cannot become projected edge markers`() {
        for ((latitude, longitude) in listOf(Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY, 91.0 to 0.0, 0.0 to 181.0, 90.0 to 0.0)) {
            assertFalse(isMapCoordinate(latitude, longitude))
        }
        assertEquals(emptyMap<GeoPoint, List<Spot>>(), mapMarkerGroups(listOf(first.copy(location = GeoPoint(90.0, 0.0)))))
    }
}
