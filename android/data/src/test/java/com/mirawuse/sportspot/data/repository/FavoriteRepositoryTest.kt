package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mirawuse.sportspot.data.local.account.AuthSession
import com.mirawuse.sportspot.data.local.account.AuthSessionStore
import com.mirawuse.sportspot.data.local.account.FavoriteCache
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.data.mapper.toFavoriteSnapshot
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.NetworkConfig
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.remote.api.FavoriteApi
import com.mirawuse.sportspot.data.remote.api.FavoriteRow
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.domain.model.favorite.FavoriteException
import com.mirawuse.sportspot.domain.model.favorite.FavoriteFailure
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.spot.SpotPhoto
import com.mirawuse.sportspot.domain.model.spot.SpotSport
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class FavoriteRepositoryTest {
    @get:Rule val server = MockWebServer()
    private val gson = NetworkClient.createGson()

    @Volatile private var online = true
    private val cache = MemoryFavoriteCache()
    private val network = NetworkCall(NetworkMonitor { online }, gson)
    private val retrofit by lazy {
        val config = NetworkConfig(server.url("/"), "fixture-publishable-key")
        NetworkClient.createRetrofit(config, NetworkClient.createOkHttp(config, AccessTokenProvider { "unrelated-token" }), gson)
    }
    private val stored = MemorySessionStore(activeSession(ALICE))
    private val auth by lazy { AuthRepositoryImpl(retrofit.create(AuthApi::class.java), network, stored) }
    private val repo by lazy { newRepository(auth) }
    private val photo =
        SpotPhoto(
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Fixture.jpg/500px-Fixture.jpg",
            "https://commons.wikimedia.org/wiki/File:Fixture.jpg",
            "Fixture author",
            "CC BY-SA 4.0",
            "https://creativecommons.org/licenses/by-sa/4.0/",
        )
    private val spot =
        Spot(
            SpotId("osm", "way:42"),
            "Именная площадка",
            SportType.OTHER,
            GeoPoint(56.3, 44.0),
            "asphalt",
            false,
            photo,
            "Лесная, 12",
            "horizontal_bar",
            listOf(SpotSport.FOOTBALL, SpotSport.ICE_HOCKEY),
        )

    @Test
    fun postsCanonicalOsmAndCommunitySnapshotsWithPinnedBearer() =
        runBlocking {
            val community = spot.copy(id = SpotId("community", COMMUNITY), sportType = SportType.TENNIS, photo = null, sports = null)
            val originals = listOf(spot, spot.copy(id = SpotId("osm", "node:43")), spot.copy(id = SpotId("osm", "relation:44")), community)
            for (original in originals) {
                enqueue(rows(original))
                val favorite = repo.addFavorite(ALICE, original)
                assertEquals(original, favorite.spot)
                val request = next()
                assertEquals("POST", request.method)
                assertEquals("/rest/v1/favorites", request.requestUrl?.encodedPath)
                assertEquals("user_id,spot_id", request.requestUrl?.queryParameter("on_conflict"))
                assertEquals("Bearer access-$ALICE", request.getHeader("Authorization"))
                assertEquals("resolution=ignore-duplicates,return=representation,missing=default", request.getHeader("Prefer"))
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                assertEquals(setOf("spot_id", "spot_snapshot"), body.keySet())
                assertEquals(original.id.toCanonicalId(), body["spot_id"].asString)
                assertEquals(original.toFavoriteSnapshot(), body["spot_snapshot"])
            }
        }

    @Test
    fun duplicateInsertReadsExistingRowAndDeletingTwiceIsSafe() =
        runBlocking {
            enqueue("[]", 201)
            enqueue(rows(spot))
            assertEquals(spot, repo.addFavorite(ALICE, spot).spot)
            assertEquals("POST", next().method)
            assertEquals("eq.osm:way:42", next().requestUrl?.queryParameter("spot_id"))

            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(204))
                repo.removeFavorite(ALICE, spot.id)
                val request = next()
                assertEquals("DELETE", request.method)
                assertEquals("eq.osm:way:42", request.requestUrl?.queryParameter("spot_id"))
            }
        }

    @Test
    fun freshClientRestoresServerSnapshotAndOnlyTheOwnerCanUseTheOfflineCache() =
        runBlocking {
            enqueue(rows(spot))
            val freshCache = newRepository(auth)
            assertEquals(
                spot,
                freshCache
                    .getFavorites(ALICE)
                    .items
                    .single()
                    .spot,
            )
            assertNull(next().requestUrl?.queryParameter("user_id"))
            online = false
            val afterRestart = newRepository(AuthRepositoryImpl(retrofit.create(AuthApi::class.java), network, stored))
            val offline = afterRestart.getFavorites(ALICE)
            assertTrue(offline.fromCache)
            assertEquals(spot, offline.items.single().spot)
            assertEquals(FavoriteFailure.OFFLINE, failure { afterRestart.addFavorite(ALICE, spot) })
            assertEquals(FavoriteFailure.OFFLINE, failure { afterRestart.removeFavorite(ALICE, spot.id) })
            auth.signOut()

            val other =
                newRepository(
                    AuthRepositoryImpl(retrofit.create(AuthApi::class.java), network, MemorySessionStore(activeSession(BOB))),
                )
            assertEquals(FavoriteFailure.OFFLINE, failure { other.getFavorites(BOB) })
            assertEquals(FavoriteFailure.SESSION_EXPIRED, failure { other.getFavorites(ALICE) })
            assertEquals(1, server.requestCount)
        }

    @Test
    fun refreshOn401RetriesOnceWithRotatedToken() =
        runBlocking {
            enqueue("{}", 401)
            val refresh =
                """{"access_token":"rotated","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"$ALICE","email":"a@example.test"}}"""
            enqueue(refresh)
            enqueue(rows(spot))
            assertFalse(repo.getFavorites(ALICE).fromCache)
            assertEquals("Bearer access-$ALICE", next().getHeader("Authorization"))
            assertEquals("refresh_token", next().requestUrl?.queryParameter("grant_type"))
            assertEquals("Bearer rotated", next().getHeader("Authorization"))
            assertEquals(3, server.requestCount)
        }

    @Test
    fun wrongOwnerCannotPopulateCacheAndInvalidSpotIsRejectedBeforeNetwork() =
        runBlocking {
            enqueue(rows(spot, BOB))
            assertEquals(FavoriteFailure.UNAVAILABLE, failure { repo.getFavorites(ALICE) })
            assertNull(cache.read(ALICE))
            val invalid = spot.copy(id = SpotId("osm", "way:0042"))
            assertEquals(FavoriteFailure.UNAVAILABLE, failure { repo.addFavorite(ALICE, invalid) })
            assertEquals(1, server.requestCount)
        }

    @Test
    fun lateResponseAfterLogoutIsDiscarded() =
        runBlocking {
            server.enqueue(MockResponse().setBody(rows(spot)).setBodyDelay(2, TimeUnit.SECONDS))
            val response = async { failure { repo.getFavorites(ALICE) } }
            // Let the coroutine enqueue the request before blocking on the test server.
            val first = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { next() }
            assertEquals("GET", first.method)
            server.enqueue(MockResponse().setResponseCode(204))
            auth.signOut()
            assertEquals(FavoriteFailure.SESSION_EXPIRED, response.await())
            assertNull(cache.read(ALICE))
        }

    private fun newRepository(auth: AuthRepositoryImpl) =
        FavoriteRepositoryImpl(retrofit.create(FavoriteApi::class.java), auth, network, cache, NoPublicCatalog, gson)

    private fun enqueue(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body))
    }

    private fun next(): RecordedRequest = checkNotNull(server.takeRequest(3, TimeUnit.SECONDS))

    private fun rows(
        spot: Spot,
        owner: String = ALICE,
    ): String {
        val row =
            JsonObject().apply {
                addProperty("user_id", owner)
                addProperty("spot_id", spot.id.toCanonicalId())
                addProperty("created_at", "2026-11-24T12:00:00Z")
                add("spot_snapshot", spot.toFavoriteSnapshot())
            }
        return gson.toJson(listOf(row))
    }

    private suspend fun failure(block: suspend () -> Any?): FavoriteFailure {
        try {
            block()
        } catch (e: FavoriteException) {
            return e.failure
        }
        error("Expected a controlled favorites error")
    }

    private class MemorySessionStore(
        private var value: AuthSession?,
    ) : AuthSessionStore {
        override fun read() = value

        override fun write(session: AuthSession?) {
            value = session
        }

        override fun nowMillis() = 1000L
    }

    private class MemoryFavoriteCache : FavoriteCache {
        private var record: Pair<String, List<FavoriteRow>>? = null

        override fun read(userId: String) = record?.takeIf { it.first == userId }?.second

        override fun write(
            userId: String,
            rows: List<FavoriteRow>,
        ) {
            record = userId to rows
        }
    }

    private object NoPublicCatalog : SpotRepository {
        override suspend fun getSpots(): List<Spot> = error("Unexpected catalog request")

        override suspend fun getSpotById(id: SpotId): Spot? = error("Valid snapshot should avoid the public catalog")

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> = error("Unexpected nearby search")
    }

    private companion object {
        const val ALICE = "11111111-1111-4111-8111-111111111111"
        const val BOB = "22222222-2222-4222-8222-222222222222"
        const val COMMUNITY = "33333333-3333-4333-8333-333333333333"

        fun activeSession(id: String) =
            AuthSession(
                "access-$id",
                "refresh-$id",
                1000L + 3600_000L,
                UserProfile(id, "Test Account", "a@example.test", UserRole.USER),
            )
    }
}
