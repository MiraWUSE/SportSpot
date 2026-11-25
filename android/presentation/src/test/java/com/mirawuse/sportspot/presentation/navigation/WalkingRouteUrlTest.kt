package com.mirawuse.sportspot.presentation.navigation

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.presentation.ui.navigation.walkingRouteUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.util.Locale

class WalkingRouteUrlTest {
    @Test
    fun sendsExactlyTheDestinationAndWalkingOptions() {
        val url = URI(walkingRouteUrl(GeoPoint(56.3269, 44.0059)))

        assertEquals("https", url.scheme)
        assertEquals("www.google.com", url.host)
        assertEquals("/maps/dir/", url.path)
        assertEquals("api=1&destination=56.3269%2C44.0059&travelmode=walking&dir_action=navigate", url.rawQuery)
    }

    @Test
    fun russianLocaleKeepsDecimalPointsEvenForSmallCoordinates() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val url = URI(walkingRouteUrl(GeoPoint(-0.00000017, 44.0059)))

            assertEquals("api=1&destination=-0.00000017%2C44.0059&travelmode=walking&dir_action=navigate", url.rawQuery)
        } finally {
            Locale.setDefault(original)
        }
    }
}
