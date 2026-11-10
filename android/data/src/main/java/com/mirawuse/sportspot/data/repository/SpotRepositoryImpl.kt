package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.mapper.SpotMappingException
import com.mirawuse.sportspot.data.mapper.toApiSport
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.data.mapper.toDomain
import com.mirawuse.sportspot.data.remote.api.SpotApi
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.math.BigDecimal
import javax.inject.Inject

class SpotRepositoryImpl
    @Inject
    constructor(
        private val api: SpotApi,
        private val calls: NetworkCall,
    ) : SpotRepository {
        override suspend fun getSpots(): List<Spot> =
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

        override suspend fun getSpotById(id: SpotId): Spot? {
            val canonical = validate { id.toCanonicalId() }
            return calls.execute {
                val rows = calls.body(api.getSpots(limit = 2, id = "eq.$canonical"))
                if (rows.isEmpty()) {
                    null
                } else {
                    if (rows.size != 1) throw SpotMappingException()
                    val spot = rows.single()?.toDomain() ?: throw SpotMappingException()
                    if (spot.id != id) throw SpotMappingException()
                    spot
                }
            }
        }

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            val (lat, lon, sport) =
                validate {
                    require(radiusMeters in 100..3000) { "Search radius must be between 100 and 3000 meters" }
                    Triple(coordinate(center.latitude), coordinate(center.longitude), sportType.toApiSport())
                }
            return calls.execute {
                calls.body(api.searchSpots(lat, lon, radiusMeters, sport)).toDomain()
            }
        }

        private fun coordinate(value: Double): String {
            val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
            require(decimal.scale() <= 6) { "Search coordinates support at most six decimal places" }
            return decimal.toPlainString()
        }

        private suspend fun <T> validate(block: () -> T): T {
            currentCoroutineContext().ensureActive()
            return try {
                block()
            } catch (failure: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                throw NetworkException(NetworkError.InvalidRequest, failure)
            }
        }

        companion object {
            private const val PAGE_SIZE = 100
        }
    }
