package com.mirawuse.sportspot.data.repository

import androidx.room.Room
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mirawuse.sportspot.data.local.CacheClock
import com.mirawuse.sportspot.data.local.RoomSpotCache
import com.mirawuse.sportspot.data.local.db.SportSpotDatabase
import com.mirawuse.sportspot.data.local.withDatabaseDirectory
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.NetworkConfig
import com.mirawuse.sportspot.data.remote.RemoteSpotDataSource
import com.mirawuse.sportspot.data.remote.api.SpotApi
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineSpotRepositoryTest {
    @get:Rule
    val server = MockWebServer()

    @get:Rule
    val folder = TemporaryFolder()

    @Volatile
    private var online = true

    @Volatile
    private var dnsUnavailable = false

    private var timeMillis = 10_000_000L
    private val monitor = NetworkMonitor { online }
    private val clock = CacheClock { timeMillis }
    private val gson = NetworkClient.createGson()
    private lateinit var client: OkHttpClient
    private lateinit var remote: RemoteSpotDataSource
    private lateinit var database: SportSpotDatabase
    private lateinit var repository: SpotRepositoryImpl

    private val center = GeoPoint(56.3269, 44.0059)
    private val osmId = SpotId("osm", "node:12345")
    private val unknownId = SpotId("osm", "way:999")
    private val osmSpot = Spot(osmId, "Спортивная площадка", SportType.BASKETBALL, center, "asphalt", false)
    private val savedSpots =
        listOf(
            osmSpot,
            Spot(
                SpotId("community", "33333333-3333-4333-8333-333333333333"),
                "Спортивная площадка",
                SportType.WORKOUT,
                center,
            ),
        )
    private val searchSpots = listOf(osmSpot.copy(lighting = null))

    @Before
    fun setUp() {
        val serverUrl = server.url("/")
        val serverAddress = InetAddress.getByName(serverUrl.host)
        val config =
            NetworkConfig(
                baseUrl = serverUrl.newBuilder().host("localhost").build(),
                readTimeoutMillis = 2000,
                callTimeoutMillis = 6000,
            )
        client =
            NetworkClient
                .createOkHttp(config, AccessTokenProvider { null })
                .newBuilder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            if (dnsUnavailable) throw UnknownHostException(hostname)
                            return listOf(serverAddress)
                        }
                    },
                ).build()
        val api = NetworkClient.createRetrofit(config, client, gson).create(SpotApi::class.java)
        remote = RemoteSpotDataSource(api, NetworkCall(monitor, gson))
        openDatabase()
    }

    @After
    fun tearDown() {
        if (::client.isInitialized) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            client.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
        if (::database.isInitialized) database.close()
    }

    @Test
    fun `saved HTTP rows become Domain spots and survive offline database reopening`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))
            assertEquals(savedSpots, repository.getSpots())
            assertEquals("/rest/v1/spots", takeRequest().requestUrl?.encodedPath)

            timeMillis += SAVED_TTL
            online = false
            reopenDatabase()
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(osmSpot, repository.getSpotById(osmId))
            assertNull(repository.getSpotById(unknownId))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `saved empty HTTP success survives disk reopening while missing snapshot fails offline`() =
        runBlocking {
            online = false
            assertError(NetworkError.Offline) { repository.getSpots() }
            assertEquals(0, server.requestCount)

            online = true
            server.enqueue(json("[]"))
            assertTrue(repository.getSpots().isEmpty())

            online = false
            reopenDatabase()
            assertTrue(repository.getSpots().isEmpty())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `search stores independent nonempty and empty snapshots on disk`() =
        runBlocking {
            server.enqueue(json(fixture("search-spots.json")))
            server.enqueue(json(emptySearch()))
            assertEquals(searchSpots, repository.searchNearby(center, 1000, SportType.BASKETBALL))
            assertTrue(repository.searchNearby(center, 2000, SportType.BASKETBALL).isEmpty())

            val first = requireNotNull(takeRequest().requestUrl)
            val second = requireNotNull(takeRequest().requestUrl)
            assertEquals("/functions/v1/search-sports-spots-v1", first.encodedPath)
            assertEquals("56.3269", first.queryParameter("lat"))
            assertEquals("44.0059", first.queryParameter("lon"))
            assertEquals("1000", first.queryParameter("radius"))
            assertEquals("basketball", first.queryParameter("sport"))
            assertEquals("2000", second.queryParameter("radius"))

            timeMillis += SEARCH_TTL
            online = false
            reopenDatabase()
            assertEquals(searchSpots, repository.searchNearby(center, 1000, SportType.BASKETBALL))
            assertTrue(repository.searchNearby(center, 2000, SportType.BASKETBALL).isEmpty())
            assertError(NetworkError.Offline) { repository.getSpots() }
            assertError(NetworkError.Offline) { repository.getSpotById(osmId) }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `all search key dimensions isolate cached results and invalid queries fail offline`() =
        runBlocking {
            server.enqueue(json(fixture("search-spots.json")))
            repository.searchNearby(center, 1000, SportType.BASKETBALL)
            online = false

            assertError(NetworkError.Offline) { repository.searchNearby(center, 1001, SportType.BASKETBALL) }
            assertError(NetworkError.Offline) { repository.searchNearby(center, 1000, SportType.TENNIS) }
            assertError(NetworkError.Offline) {
                repository.searchNearby(center.copy(latitude = 56.326901), 1000, SportType.BASKETBALL)
            }
            assertError(NetworkError.Offline) {
                repository.searchNearby(center.copy(longitude = 44.005901), 1000, SportType.BASKETBALL)
            }
            assertError(NetworkError.InvalidRequest) { repository.searchNearby(center, 99, SportType.BASKETBALL) }
            assertError(NetworkError.InvalidRequest) { repository.searchNearby(center, 1000, SportType.OTHER) }
            assertError(NetworkError.InvalidRequest) {
                repository.searchNearby(center.copy(latitude = 56.3269001), 1000, SportType.BASKETBALL)
            }
            assertError(NetworkError.InvalidRequest) { repository.getSpotById(SpotId("osm", "12345")) }
            assertEquals(searchSpots, repository.searchNearby(center, 1000, SportType.BASKETBALL))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `saved and search hits avoid HTTP until their respective TTL then refresh`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))
            server.enqueue(json(fixture("search-spots.json")))
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(searchSpots, repository.searchNearby(center, 1000, SportType.BASKETBALL))

            timeMillis += SAVED_TTL - 1
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(searchSpots, repository.searchNearby(center, 1000, SportType.BASKETBALL))
            assertEquals(2, server.requestCount)

            timeMillis += 1
            server.enqueue(json("[]"))
            assertTrue(repository.getSpots().isEmpty())
            assertEquals(searchSpots, repository.searchNearby(center, 1000, SportType.BASKETBALL))
            assertEquals(3, server.requestCount)

            timeMillis += SEARCH_TTL - SAVED_TTL
            server.enqueue(json(emptySearch()))
            assertTrue(repository.searchNearby(center, 1000, SportType.BASKETBALL).isEmpty())
            assertEquals(4, server.requestCount)
        }

    @Test
    fun `detail cache survives reopening without certifying missing records or a full list`() =
        runBlocking {
            server.enqueue(json(JsonArray().apply { add(savedRows()[0]) }.toString()))
            assertEquals(osmSpot, repository.getSpotById(osmId))
            assertEquals("eq.osm:node:12345", takeRequest().requestUrl?.queryParameter("id"))

            online = false
            reopenDatabase()
            assertEquals(osmSpot, repository.getSpotById(osmId))
            assertError(NetworkError.Offline) { repository.getSpotById(unknownId) }
            assertError(NetworkError.Offline) { repository.getSpots() }
            assertEquals(1, server.requestCount)

            online = true
            timeMillis += SAVED_TTL - 1
            assertEquals(osmSpot, repository.getSpotById(osmId))
            assertEquals(1, server.requestCount)

            timeMillis += 1
            server.enqueue(json("[]"))
            assertNull(repository.getSpotById(osmId))
            online = false
            assertError(NetworkError.Offline) { repository.getSpotById(osmId) }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `stale snapshot survives retryable HTTP and transport failures without extending freshness`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))
            assertEquals(savedSpots, repository.getSpots())
            timeMillis += SAVED_TTL

            listOf(429, 503).forEach { status ->
                server.enqueue(json("{}").setResponseCode(status))
                assertEquals(savedSpots, repository.getSpots())
            }
            assertEquals(3, server.requestCount)

            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(savedSpots, repository.getSpots())
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(5, server.requestCount)

            client.connectionPool.evictAll()
            dnsUnavailable = true
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(5, server.requestCount)

            online = false
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(5, server.requestCount)

            dnsUnavailable = false
            online = true
            server.enqueue(json("[]"))
            assertTrue(repository.getSpots().isEmpty())
            assertEquals(6, server.requestCount)
        }

    @Test
    fun `stale snapshot does not hide HTTP client errors or invalid JSON`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))
            assertEquals(savedSpots, repository.getSpots())
            timeMillis += SAVED_TTL

            listOf(400, 401, 403, 404).forEach { status ->
                server.enqueue(json("""{"code":"ERROR"}""").setResponseCode(status))
                assertError(NetworkError.Http(status, "ERROR")) { repository.getSpots() }
            }
            server.enqueue(json("{"))
            assertError(NetworkError.Parsing) { repository.getSpots() }

            online = false
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(6, server.requestCount)
        }

    @Test
    fun `retryable remote failure without a snapshot preserves its original error`() =
        runBlocking {
            server.enqueue(json("{}").setResponseCode(503).addHeader("Retry-After", "60"))
            assertError(NetworkError.Http(503, retryAfterSeconds = 60)) { repository.getSpots() }
            online = false
            assertError(NetworkError.Offline) { repository.getSpots() }
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `failed second page leaves the previous complete saved snapshot on disk`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))
            assertEquals(savedSpots, repository.getSpots())
            takeRequest()
            timeMillis += SAVED_TTL

            server.enqueue(json(fullPage()))
            server.enqueue(json("{}").setResponseCode(500))
            assertEquals(savedSpots, repository.getSpots())
            assertEquals("0", takeRequest().requestUrl?.queryParameter("offset"))
            assertEquals("100", takeRequest().requestUrl?.queryParameter("offset"))

            online = false
            reopenDatabase()
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `cancelling in flight HTTP preserves cancellation and the old disk snapshot`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))
            assertEquals(savedSpots, repository.getSpots())
            takeRequest()
            timeMillis += SAVED_TTL
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val observed = CompletableDeferred<Throwable?>()
            val pending =
                launch(Dispatchers.Default) {
                    observed.complete(runCatching { repository.getSpots() }.exceptionOrNull())
                }

            try {
                takeRequest()
                pending.cancelAndJoin()
                assertTrue(withTimeout(3000) { observed.await() } is CancellationException)
                withTimeout(3000) {
                    while (client.dispatcher.runningCallsCount() != 0) delay(10)
                }
            } finally {
                pending.cancelAndJoin()
            }

            online = false
            reopenDatabase()
            assertEquals(savedSpots, repository.getSpots())
            assertEquals(2, server.requestCount)
        }

    private fun openDatabase() {
        val context = RuntimeEnvironment.getApplication().withDatabaseDirectory(folder.root)
        database = Room.databaseBuilder(context, SportSpotDatabase::class.java, "cache.db").build()
        repository = SpotRepositoryImpl(remote, RoomSpotCache(database), monitor, clock)
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }

    private suspend fun assertError(
        expected: NetworkError,
        block: suspend () -> Any?,
    ) {
        val failure = runCatching { block() }.exceptionOrNull()
        if (failure !is NetworkException) throw failure ?: AssertionError("Expected ${expected.javaClass.simpleName}")
        assertEquals(expected, failure.error)
    }

    private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun takeRequest(): RecordedRequest = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/fixtures/$name")).readText()

    private fun savedRows(): JsonArray = gson.fromJson(fixture("saved-spots.json"), JsonArray::class.java)

    private fun emptySearch(): String =
        gson
            .fromJson(fixture("search-spots.json"), JsonObject::class.java)
            .apply {
                add("spots", JsonArray())
                addProperty("count", 0)
            }.toString()

    private fun fullPage(): String {
        val template = savedRows()[0].asJsonObject
        return JsonArray()
            .apply {
                repeat(100) { index ->
                    val id = index + 1
                    add(
                        template.deepCopy().apply {
                            addProperty("id", "osm:node:$id")
                            addProperty("osm_id", id)
                        },
                    )
                }
            }.toString()
    }

    companion object {
        private const val SAVED_TTL = 15 * 60_000L
        private const val SEARCH_TTL = 60 * 60_000L
    }
}
