package com.mirawuse.sportspot.domain.usecase.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.repository.NearbySpots
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.repository.ViewportSearchResult

class SearchNearbySpotsUseCase(
    private val repository: SpotRepository,
) {
    suspend operator fun invoke(
        center: GeoPoint,
        radiusMeters: Int = 1000,
        sportType: SportType? = null,
        refresh: Boolean = false,
    ): List<Spot> {
        validateRadius(radiusMeters)

        return if (refresh) {
            repository.refreshNearby(center, radiusMeters, sportType)
        } else {
            repository.searchNearby(center, radiusMeters, sportType)
        }
    }

    suspend fun withStatus(
        center: GeoPoint,
        radiusMeters: Int,
        refresh: Boolean,
    ): NearbySpots {
        validateRadius(radiusMeters)
        return repository.nearbyWithStatus(center, radiusMeters, refresh)
    }

    suspend fun forViewport(
        center: GeoPoint,
        radiusMeters: Int,
        refresh: Boolean,
        permitNetwork: () -> Boolean,
    ): ViewportSearchResult {
        validateRadius(radiusMeters)
        return repository.searchViewport(center, radiusMeters, refresh, permitNetwork)
    }

    private fun validateRadius(radiusMeters: Int) {
        require(radiusMeters in 100..3000) {
            "Радиус поиска должен быть от 100 до 3000 метров"
        }
    }
}
