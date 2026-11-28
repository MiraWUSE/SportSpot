package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.local.CacheClock
import com.mirawuse.sportspot.data.local.CacheSnapshot
import com.mirawuse.sportspot.data.local.RoomSpotCache
import com.mirawuse.sportspot.data.remote.RemoteSpotDataSource
import com.mirawuse.sportspot.data.remote.SpotSearchDiagnostics
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.data.remote.validatedCanonicalId
import com.mirawuse.sportspot.data.remote.validatedSearchRequest
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.NearbySpots
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.repository.ViewportSearchResult
import com.mirawuse.sportspot.domain.result.DomainError
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
        private val diagnostics: SpotSearchDiagnostics = SpotSearchDiagnostics(),
    ) : SpotRepository {
        // The saved list and individual details update the same Room rows.
        private val savedMutex = Mutex()
        private val searchMutex = Mutex()

        override suspend fun getSpots(): List<Spot> =
            savedMutex.withLock {
                readOrRefresh(
                    ttlMillis = SAVED_TTL_MILLIS,
                    readCache = cache::readSaved,
                    fetchRemote = { remote.getSpots() },
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

        override suspend fun getCachedSpotNames(ids: Set<SpotId>): Map<SpotId, String> {
            val byCanonicalId = ids.associateBy { validatedCanonicalId(it) }
            return cache.readSpotNames(byCanonicalId.keys).mapKeys { (id, _) -> byCanonicalId.getValue(id) }
        }

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> = searchNearby(center, radiusMeters, sportType, refresh = false).spots

        override suspend fun refreshNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> = searchNearby(center, radiusMeters, sportType, refresh = true).spots

        override suspend fun nearbyWithStatus(
            center: GeoPoint,
            radiusMeters: Int,
            refresh: Boolean,
        ): NearbySpots = searchNearby(center, radiusMeters, null, refresh)

        override suspend fun searchViewport(
            center: GeoPoint,
            radiusMeters: Int,
            refresh: Boolean,
            permitNetwork: () -> Boolean,
        ): ViewportSearchResult =
            try {
                val result = searchNearby(center, radiusMeters, null, refresh, permitNetwork)
                ViewportSearchResult.Available(result.spots, result.fallback)
            } catch (_: ViewportDeferred) {
                ViewportSearchResult.Deferred
            } catch (failure: ViewportRateLimited) {
                failure.result
            }

        private suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
            refresh: Boolean,
            permitNetwork: (() -> Boolean)? = null,
        ): NearbySpots =
            diagnostics.stage("search") {
                val request = validatedSearchRequest(center, radiusMeters, sportType)
                val key = request.cacheKey
                searchMutex.withLock {
                    var fallback: DomainError? = null
                    val spots =
                        readOrRefresh(
                            ttlMillis = if (refresh) 0L else SEARCH_TTL_MILLIS,
                            readCache = { diagnostics.stage("cache.read") { cache.readSearch(key) } },
                            fetchRemote = { cached ->
                                if (permitNetwork != null && !permitNetwork()) throw ViewportDeferred()
                                try {
                                    remote.searchNearby(request)
                                } catch (failure: NetworkException) {
                                    val http = failure.error as? NetworkError.Http
                                    if (permitNetwork != null && http?.status == 429) {
                                        throw ViewportRateLimited(ViewportSearchResult.RateLimited(http.retryAfterSeconds, cached))
                                    }
                                    throw failure
                                }
                            },
                            writeCache = { spots, time -> diagnostics.stage("cache.write") { cache.replaceSearch(key, spots, time) } },
                            onFallback = { fallback = it },
                        )
                    NearbySpots(spots, fallback)
                }
            }

        private suspend fun <T> readOrRefresh(
            ttlMillis: Long,
            readCache: suspend () -> CacheSnapshot<T>?,
            fetchRemote: suspend (T?) -> T,
            writeCache: suspend (T, Long) -> T,
            onFallback: ((DomainError) -> Unit)? = null,
        ): T {
            val context = currentCoroutineContext()
            context.ensureActive()
            val snapshot = readCache()
            context.ensureActive()
            if (snapshot != null && isFresh(snapshot.fetchedAtEpochMillis, ttlMillis)) {
                if (onFallback != null && !monitor.isOnline()) onFallback(NetworkException(NetworkError.Offline).domainError)
                return snapshot.value
            }

            if (!monitor.isOnline()) {
                context.ensureActive()
                if (snapshot != null) {
                    onFallback?.invoke(NetworkException(NetworkError.Offline).domainError)
                    return snapshot.value
                }
                throw NetworkException(NetworkError.Offline)
            }

            val remoteValue =
                try {
                    fetchRemote(snapshot?.value)
                } catch (failure: NetworkException) {
                    context.ensureActive()
                    if (snapshot != null && failure.error.permitsCacheFallback()) {
                        onFallback?.invoke(failure.domainError)
                        return snapshot.value
                    }
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

        private class ViewportDeferred : RuntimeException()

        private class ViewportRateLimited(
            val result: ViewportSearchResult.RateLimited,
        ) : RuntimeException()

        companion object {
            private const val SAVED_TTL_MILLIS = 15 * 60_000L
            private const val SEARCH_TTL_MILLIS = 60 * 60_000L
        }
    }
