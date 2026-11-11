package com.mirawuse.sportspot.data.remote

import com.mirawuse.sportspot.data.mapper.SpotMappingException
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.data.mapper.toDomain
import com.mirawuse.sportspot.data.remote.api.SpotApi
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import javax.inject.Inject

class RemoteSpotDataSource
    @Inject
    constructor(
        private val api: SpotApi,
        private val calls: NetworkCall,
    ) {
        suspend fun getSpots(): List<Spot> =
            calls.execute {
                val spots = mutableListOf<Spot>()
                val ids = mutableSetOf<SpotId>()
                do {
                    val rows = calls.body(api.getSpots(limit = PAGE_SIZE, offset = spots.size))
                    if (rows.size > PAGE_SIZE) throw SpotMappingException()
                    val page = rows.map { it?.toDomain() ?: throw SpotMappingException() }
                    if (page.any { !ids.add(it.id) }) throw SpotMappingException()
                    spots += page
                } while (rows.size == PAGE_SIZE)
                spots
            }

        internal suspend fun getSpotById(canonicalId: String): Spot? =
            calls.execute {
                val rows = calls.body(api.getSpots(limit = 2, id = "eq.$canonicalId"))
                if (rows.isEmpty()) {
                    null
                } else {
                    if (rows.size != 1) throw SpotMappingException()
                    val spot = rows.single()?.toDomain() ?: throw SpotMappingException()
                    if (spot.id.toCanonicalId() != canonicalId) throw SpotMappingException()
                    spot
                }
            }

        internal suspend fun searchNearby(request: SpotSearchRequest): List<Spot> =
            calls.execute {
                val response = api.searchSpots(request.latitude, request.longitude, request.radiusMeters, request.sport)
                calls.body(response).toDomain()
            }

        companion object {
            private const val PAGE_SIZE = 100
        }
    }
