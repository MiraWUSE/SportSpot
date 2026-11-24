package com.mirawuse.sportspot.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mirawuse.sportspot.data.local.account.FavoriteCache
import com.mirawuse.sportspot.data.mapper.parseCanonicalSpotId
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.data.mapper.toFavoriteSnapshot
import com.mirawuse.sportspot.data.mapper.toFavoriteSpot
import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.api.FavoriteApi
import com.mirawuse.sportspot.data.remote.api.FavoriteRow
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthFailure
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.favorite.FavoriteException
import com.mirawuse.sportspot.domain.model.favorite.FavoriteFailure
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FavoriteRepositoryImpl
    @Inject
    constructor(
        private val api: FavoriteApi,
        private val auth: AuthRepositoryImpl,
        private val network: NetworkCall,
        private val cache: FavoriteCache,
        private val spots: SpotRepository,
        private val gson: Gson,
    ) : FavoriteRepository {
        private val mutex = Mutex()

        override suspend fun getFavorites(userId: String): FavoriteList =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val rows =
                        try {
                            request(userId) { session -> loadAll(session) }
                        } catch (error: FavoriteException) {
                            if (!auth.ownsSession(userId) || error.failure !in CACHE_FALLBACK) throw error
                            val cached = cache.read(userId) ?: throw error
                            return@withLock FavoriteList(cached.map { it.toFavorite(userId) }, fromCache = true)
                        }
                    val entries = rows.map { row -> row.toFavorite(userId) to row }.distinctBy { it.first.spotId }
                    val favorites =
                        entries.map { (favorite, _) ->
                            if (favorite.spot != null) favorite else favorite.copy(spot = findLegacySpot(favorite.spotId))
                        }
                    if (!auth.ownsSession(userId)) throw FavoriteException(FavoriteFailure.SESSION_EXPIRED)
                    writeCache(
                        userId,
                        favorites.zip(entries) { favorite, (_, row) ->
                            val spot = favorite.spot
                            if (row.snapshot?.isJsonNull == false || spot == null) {
                                row
                            } else {
                                val snapshot = runCatching { spot.toFavoriteSnapshot() }.getOrNull()
                                FavoriteRow(userId, row.spotId, row.createdAt, snapshot)
                            }
                        },
                    )
                    FavoriteList(favorites)
                }
            }

        override suspend fun addFavorite(
            userId: String,
            spot: Spot,
        ): Favorite =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val snapshot =
                        try {
                            spot.toFavoriteSnapshot()
                        } catch (_: IllegalArgumentException) {
                            unavailable()
                        }
                    val id = snapshot["id"].asString
                    val body =
                        JsonObject().apply {
                            addProperty("spot_id", id)
                            add("spot_snapshot", snapshot)
                        }
                    val row =
                        request(userId) { session ->
                            network.body(api.add(body, session)).let { rows ->
                                if (rows.isEmpty()) {
                                    network.body(api.list(1, 0, "eq.$id", session)).singleOrNull()
                                } else {
                                    rows.singleOrNull()
                                }
                            } ?: unavailable()
                        }
                    val favorite = row.toFavorite(userId)
                    if (favorite.spotId != spot.id) unavailable()
                    cache.read(userId)?.let { previous ->
                        writeCache(userId, listOf(row) + previous.filterNot { it.spotId == id })
                    }
                    favorite
                }
            }

        override suspend fun removeFavorite(
            userId: String,
            spotId: SpotId,
        ) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val id =
                        try {
                            spotId.toCanonicalId()
                        } catch (_: IllegalArgumentException) {
                            unavailable()
                        }
                    request(userId) { session ->
                        val response = api.remove("eq.$id", session)
                        if (!response.isSuccessful) network.body(response)
                    }
                    cache.read(userId)?.let { previous -> writeCache(userId, previous.filterNot { it.spotId == id }) }
                }
            }
        }

        override suspend fun isFavorite(
            userId: String,
            spotId: SpotId,
        ): Boolean = getFavorites(userId).items.any { it.spotId == spotId }

        private suspend fun loadAll(session: RequestSession): List<FavoriteRow> {
            val result = mutableListOf<FavoriteRow>()
            do {
                val page = network.body(api.list(PAGE_SIZE, result.size, null, session))
                if (page.size > PAGE_SIZE) unavailable()
                result.addAll(page.map { it ?: unavailable() })
            } while (page.size == PAGE_SIZE)
            return result
        }

        private fun FavoriteRow.toFavorite(expectedUserId: String): Favorite {
            val id = spotId?.let(::parseCanonicalSpotId) ?: unavailable()
            val owner = userId?.takeIf { it == expectedUserId } ?: unavailable()
            val date = createdAt?.takeIf { it.isNotBlank() } ?: unavailable()
            return Favorite(owner, id, date, snapshot.toFavoriteSpot(id, gson))
        }

        private suspend fun findLegacySpot(id: SpotId): Spot? =
            try {
                spots.getSpotById(id)
            } catch (_: NetworkException) {
                null
            }

        private fun writeCache(
            userId: String,
            rows: List<FavoriteRow>,
        ) {
            // A confirmed server operation is still successful if local disk storage is unavailable.
            runCatching { cache.write(userId, rows) }
        }

        private suspend fun <T> request(
            userId: String,
            block: suspend (RequestSession) -> T,
        ): T =
            try {
                auth.withAccount(userId) { session -> network.execute { block(session) } }
            } catch (failure: NetworkException) {
                throw FavoriteException(failure.error.favoriteFailure())
            } catch (failure: AuthException) {
                throw FavoriteException(
                    when (failure.failure) {
                        AuthFailure.OFFLINE -> FavoriteFailure.OFFLINE
                        AuthFailure.SESSION_EXPIRED -> FavoriteFailure.SESSION_EXPIRED
                        AuthFailure.RATE_LIMITED -> FavoriteFailure.RATE_LIMITED
                        else -> FavoriteFailure.UNAVAILABLE
                    },
                )
            }

        private fun unavailable(): Nothing = throw FavoriteException(FavoriteFailure.UNAVAILABLE)

        private companion object {
            const val PAGE_SIZE = 100
            val CACHE_FALLBACK = setOf(FavoriteFailure.OFFLINE, FavoriteFailure.RATE_LIMITED, FavoriteFailure.UNAVAILABLE)
        }
    }

private fun NetworkError.favoriteFailure(): FavoriteFailure =
    when (this) {
        NetworkError.Offline, NetworkError.Timeout, NetworkError.Dns, NetworkError.Connection -> FavoriteFailure.OFFLINE
        is NetworkError.Http ->
            when (status) {
                401, 403 -> FavoriteFailure.SESSION_EXPIRED
                429 -> FavoriteFailure.RATE_LIMITED
                else -> FavoriteFailure.UNAVAILABLE
            }
        else -> FavoriteFailure.UNAVAILABLE
    }
