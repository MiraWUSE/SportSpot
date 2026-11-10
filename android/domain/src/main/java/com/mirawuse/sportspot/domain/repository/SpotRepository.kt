package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

interface SpotRepository {
    suspend fun getSpots(): List<Spot>

    suspend fun getSpotById(id: SpotId): Spot?

    suspend fun searchNearby(
        center: GeoPoint,
        radiusMeters: Int,
        sportType: SportType? = null,
    ): List<Spot>
}
