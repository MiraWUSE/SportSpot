package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

interface SpotRepository {
    suspend fun getSpots(): List<Spot>

    suspend fun getSpotById(id: SpotId): Spot?

    suspend fun getCachedSpotNames(ids: Set<SpotId>): Map<SpotId, String> = emptyMap()

    suspend fun searchNearby(
        center: GeoPoint,
        radiusMeters: Int,
        sportType: SportType? = null,
    ): List<Spot>

    suspend fun refreshNearby(
        center: GeoPoint,
        radiusMeters: Int,
        sportType: SportType?,
    ): List<Spot> = searchNearby(center, radiusMeters, sportType)

    /** The callback is consulted before a network attempt, after checking local data. */
    suspend fun searchViewport(
        center: GeoPoint,
        radiusMeters: Int,
        refresh: Boolean,
        permitNetwork: () -> Boolean,
    ): ViewportSearchResult {
        if (!permitNetwork()) return ViewportSearchResult.Deferred
        val spots = if (refresh) refreshNearby(center, radiusMeters, null) else searchNearby(center, radiusMeters)
        return ViewportSearchResult.Available(spots)
    }
}
