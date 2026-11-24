package com.mirawuse.sportspot.domain.usecase.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.repository.SpotRepository

class SearchNearbySpotsUseCase(
    private val repository: SpotRepository,
) {
    suspend operator fun invoke(
        center: GeoPoint,
        radiusMeters: Int = 1000,
        sportType: SportType? = null,
        refresh: Boolean = false,
    ): List<Spot> {
        require(radiusMeters in 100..3000) {
            "Радиус поиска должен быть от 100 до 3000 метров"
        }

        return if (refresh) {
            repository.refreshNearby(center, radiusMeters, sportType)
        } else {
            repository.searchNearby(center, radiusMeters, sportType)
        }
    }
}
