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
        private val diagnostics: SpotSearchDiagnostics = SpotSearchDiagnostics(),
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
                if (rows.isEmpty()) return@execute null
                if (rows.size != 1) throw SpotMappingException()

                val spot = rows.single()?.toDomain() ?: throw SpotMappingException()
                if (spot.id.toCanonicalId() != canonicalId) throw SpotMappingException()
                spot
            }

        internal suspend fun searchNearby(request: SpotSearchRequest): List<Spot> =
            calls.execute {
                val payload =
                    diagnostics.stage("function.decode") {
                        calls.body(api.searchSpots(request.latitude, request.longitude, request.radiusMeters, request.sport))
                    }
                val osm = diagnostics.stage("function.map") { payload.toDomain() }
                val community = diagnostics.stage("community") { getCommunitySpots(request) }
                (osm + community).distinctBy(Spot::id)
            }

        private suspend fun getCommunitySpots(request: SpotSearchRequest): List<Spot> {
            val area = CommunitySearchArea(request)
            val filter = request.sport.takeUnless { it == "all" }?.let { "eq.$it" }
            val result = mutableListOf<Spot>()
            val ids = mutableSetOf<SpotId>()
            var offset = 0
            do {
                val rows = calls.body(api.getCommunitySpots(area.bounds, area.wrappedLongitudes, filter, PAGE_SIZE, offset))
                if (rows.size > PAGE_SIZE) throw SpotMappingException()
                val previousSize = ids.size
                rows.forEach { row ->
                    if (row == null) throw SpotMappingException()
                    val spot = row.toDomain()
                    if (spot.id.source != "community" || (filter != null && row.sportType != request.sport)) {
                        throw SpotMappingException()
                    }
                    if (ids.add(spot.id) && area.contains(spot.location)) result += spot
                }
                if (rows.size == PAGE_SIZE && ids.size == previousSize) throw SpotMappingException()
                offset += rows.size
            } while (rows.size == PAGE_SIZE)
            return result
        }

        companion object {
            private const val PAGE_SIZE = 100
        }
    }
