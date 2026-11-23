package com.mirawuse.sportspot.domain.model.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint

data class Spot(
    val id: SpotId,
    val name: String,
    val sportType: SportType,
    val location: GeoPoint,
    val surface: String? = null,
    val lighting: Boolean? = null,
    val photo: SpotPhoto? = null,
    val address: String? = null,
    val equipment: String? = null,
    // null: older record without the sport list. An empty list means OSM did not specify a sport.
    val sports: List<SpotSport>? = null,
) {
    init {
        require(name.isNotBlank()) {
            "Название площадки не может быть пустым"
        }
    }

    fun supportsSport(type: SportType): Boolean = sports?.takeIf { it.isNotEmpty() }?.any { it.category == type } ?: (sportType == type)
}
