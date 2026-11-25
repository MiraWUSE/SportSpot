package com.mirawuse.sportspot.presentation.ui.proposal

import androidx.compose.runtime.saveable.listSaver
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.validation.FormValidation
import java.math.RoundingMode

internal data class ProposalCoordinates(
    val latitude: String = "",
    val longitude: String = "",
) {
    fun pointOrNull(): GeoPoint? {
        if (FormValidation.latitude(latitude) != null || FormValidation.longitude(longitude) != null) return null
        return GeoPoint(latitude.trim().replace(',', '.').toDouble(), longitude.trim().replace(',', '.').toDouble())
    }

    companion object {
        val Saver =
            listSaver<ProposalCoordinates, String>(
                save = { listOf(it.latitude, it.longitude) },
                restore = { ProposalCoordinates(it[0], it[1]) },
            )

        fun fromPoint(point: GeoPoint): ProposalCoordinates = ProposalCoordinates(point.latitude.asText(), point.longitude.asText())

        // Truncating toward zero also keeps points at the projection edge inside the visible map.
        private fun Double.asText(): String = toBigDecimal().setScale(7, RoundingMode.DOWN).toPlainString()
    }
}
