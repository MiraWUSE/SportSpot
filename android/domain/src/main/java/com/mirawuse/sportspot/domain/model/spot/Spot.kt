package com.mirawuse.sportspot.domain.model.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint

data class Spot(
    val id: SpotId,
    val name: String,
    val sportType: SportType,
    val location: GeoPoint,
    val surface: String? = null,
    val lighting: Boolean? = null,
) {
    init {
        require(name.isNotBlank()) {
            "Название площадки не может быть пустым"
        }
    }

    fun supportsSport(type: SportType): Boolean = sportType == type
}
