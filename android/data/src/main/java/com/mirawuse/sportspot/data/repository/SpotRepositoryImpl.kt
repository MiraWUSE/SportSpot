package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.local.CacheClock
import com.mirawuse.sportspot.data.local.CacheSnapshot
import com.mirawuse.sportspot.data.local.RoomSpotCache
import com.mirawuse.sportspot.data.remote.RemoteSpotDataSource
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.data.remote.validatedCanonicalId
import com.mirawuse.sportspot.data.remote.validatedSearchRequest
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class SpotRepositoryImpl
    @Inject
    constructor(
        private val remote: RemoteSpotDataSource,
        private val cache: RoomSpotCache,
        private val monitor: NetworkMonitor,
        private val clock: CacheClock,
    ) : SpotRepository {
        // The saved list and individual details update the same Room rows.
        private val savedMutex = Mutex()
        private val searchMutex = Mutex()

        override suspend fun getSpots(): List<Spot> =
            savedMutex.withLock {
                readOrRefresh(
                    ttlMillis = SAVED_TTL_MILLIS,
                    readCache = cache::readSaved,
                    fetchRemote = remote::getSpots,
                    writeCache = cache::replaceSaved,
                )
            }

        override suspend fun getSpotById(id: SpotId): Spot? {
            val canonicalId = validatedCanonicalId(id)
            return savedMutex.withLock {
                readOrRefresh(
                    ttlMillis = SAVED_TTL_MILLIS,
                    readCache = { cache.readSavedSpot(canonicalId) },
                    fetchRemote = { remote.getSpotById(canonicalId) },
                    writeCache = { spot, time -> cache.replaceSavedSpot(canonicalId, spot, time) },
                )
            }
        }

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            val request = validatedSearchRequest(center, radiusMeters, sportType)
            val key = request.cacheKey
            return searchMutex.withLock {
                readOrRefresh(
                    ttlMillis = SEARCH_TTL_MILLIS,
                    readCache = { cache.readSearch(key) },
                    fetchRemote = { remote.searchNearby(request) },
                    writeCache = { spots, time -> cache.replaceSearch(key, spots, time) },
                )
            }
        }

        private suspend fun <T> readOrRefresh(
            ttlMillis: Long,
            readCache: suspend () -> CacheSnapshot<T>?,
            fetchRemote: suspend () -> T,
            writeCache: suspend (T, Long) -> T,
        ): T {
            val context = currentCoroutineContext()
            context.ensureActive()
            val snapshot = readCache()
            context.ensureActive()
            if (snapshot != null && isFresh(snapshot.fetchedAtEpochMillis, ttlMillis)) return snapshot.value

            if (!monitor.isOnline()) {
                context.ensureActive()
                if (snapshot != null) return snapshot.value
                throw NetworkException(NetworkError.Offline)
            }

            val remoteValue =
                try {
                    fetchRemote()
                } catch (failure: NetworkException) {
                    context.ensureActive()
                    if (snapshot != null && failure.error.permitsCacheFallback()) return snapshot.value
                    throw failure
                }

            context.ensureActive()
            val localValue = writeCache(remoteValue, clock.nowMillis())
            context.ensureActive()
            return localValue
        }

        private fun isFresh(
            fetchedAt: Long,
            ttlMillis: Long,
        ): Boolean {
            val now = clock.nowMillis()
            // Migrated rows start at zero. Timestamps ahead of the clock also need a refresh.
            return fetchedAt in 1L..now && now - fetchedAt < ttlMillis
        }

        private fun NetworkError.permitsCacheFallback(): Boolean =
            when (this) {
                NetworkError.Offline, NetworkError.Timeout, NetworkError.Dns, NetworkError.Connection -> true
                is NetworkError.Http -> status == 429 || status in 500..599
                NetworkError.Parsing, NetworkError.InvalidRequest, NetworkError.Unexpected -> false
            }

        companion object {
            private const val SAVED_TTL_MILLIS = 15 * 60_000L
            private const val SEARCH_TTL_MILLIS = 60 * 60_000L
        }
    }
