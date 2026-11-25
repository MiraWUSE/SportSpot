package com.mirawuse.sportspot.presentation.ui.proposal

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ProposalCoordinatesTest {
    @Test
    fun readsBothManualFieldsWithEitherDecimalSeparator() {
        assertEquals(GeoPoint(56.3269, -44.0059), ProposalCoordinates(" 56,3269 ", "-44.0059").pointOrNull())
        assertEquals(GeoPoint(90.0, 180.0), ProposalCoordinates("90", "180").pointOrNull())
    }

    @Test
    fun rejectsIncompleteNonFiniteAndOutOfRangePairs() {
        val invalid =
            listOf(
                ProposalCoordinates("", "44"),
                ProposalCoordinates("56", ""),
                ProposalCoordinates("NaN", "44"),
                ProposalCoordinates("56", "Infinity"),
                ProposalCoordinates("90.0001", "44"),
                ProposalCoordinates("56", "-181"),
                ProposalCoordinates("56;3", "44"),
            )

        invalid.forEach { assertNull(it.pointOrNull()) }
    }

    @Test
    fun selectedCoordinatesRoundTripInRussianLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            val fields = ProposalCoordinates.fromPoint(GeoPoint(56.32691234, -44.00567894))

            assertEquals("56.3269123", fields.latitude)
            assertEquals("-44.0056789", fields.longitude)
            assertEquals(GeoPoint(56.3269123, -44.0056789), fields.pointOrNull())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
