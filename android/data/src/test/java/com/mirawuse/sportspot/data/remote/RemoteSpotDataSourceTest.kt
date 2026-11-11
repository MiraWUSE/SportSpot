package com.mirawuse.sportspot.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mirawuse.sportspot.data.remote.api.SpotApi
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.concurrent.TimeUnit

class RemoteSpotDataSourceTest {
    @get:Rule
    val server = MockWebServer()

    private val gson = NetworkClient.createGson()
    private val clients = mutableListOf<OkHttpClient>()
    private val center = GeoPoint(56.3269, 44.0059)
    private val osmId = SpotId("osm", "node:12345")
    private val communityId = SpotId("community", "33333333-3333-4333-8333-333333333333")

    @After
    fun closeClients() {
        clients.forEach {
            it.dispatcher.cancelAll()
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
            it.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `search reads OpenAPI HTTP fixture through Retrofit DTO and mapper`() =
        runBlocking {
            server.enqueue(json(fixture("search-spots.json")))

            val result = repository().searchNearby(center, 1000, SportType.BASKETBALL)

            assertEquals(
                listOf(
                    Spot(
                        id = osmId,
                        name = "Спортивная площадка",
                        sportType = SportType.BASKETBALL,
                        location = center,
                        surface = "asphalt",
                        lighting = null,
                    ),
                ),
                result,
            )
            val request = takeRequest()
            val url = requireNotNull(request.requestUrl)
            assertEquals("GET", request.method)
            assertEquals("/functions/v1/search-sports-spots-v1", url.encodedPath)
            assertEquals(setOf("lat", "lon", "radius", "sport"), url.queryParameterNames)
            assertEquals("56.3269", url.queryParameter("lat"))
            assertEquals("44.0059", url.queryParameter("lon"))
            assertEquals("1000", url.queryParameter("radius"))
            assertEquals("basketball", url.queryParameter("sport"))
            assertEquals("test-publishable-key", request.getHeader("apikey"))
            assertNull(request.getHeader("Authorization"))
        }

    @Test
    fun `REST list maps full schema derived OSM and community rows`() =
        runBlocking {
            server.enqueue(json(fixture("saved-spots.json")))

            val result = repository().getSpots()

            assertEquals(
                listOf(
                    Spot(osmId, "Спортивная площадка", SportType.BASKETBALL, center, "asphalt", false),
                    Spot(communityId, "Спортивная площадка", SportType.WORKOUT, center),
                ),
                result,
            )
            val url = requireNotNull(takeRequest().requestUrl)
            assertEquals("/rest/v1/spots", url.encodedPath)
            assertEquals("*", url.queryParameter("select"))
            assertEquals("100", url.queryParameter("limit"))
            assertEquals("0", url.queryParameter("offset"))
            assertEquals("id.asc", url.queryParameter("order"))
            assertNull(url.queryParameter("id"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `lookup sends encoded canonical IDs for OSM and community spots`() =
        runBlocking {
            val repository = repository()
            val cases =
                listOf(
                    Triple(osmId, 0, "eq.osm:node:12345"),
                    Triple(communityId, 1, "eq.community:33333333-3333-4333-8333-333333333333"),
                )
            for ((id, rowIndex, expectedQuery) in cases) {
                server.enqueue(json(singleSavedRow(rowIndex)))

                val spot = repository.getSpotById(id)

                assertEquals(id, requireNotNull(spot).id)
                val url = requireNotNull(takeRequest().requestUrl)
                assertEquals(expectedQuery, url.queryParameter("id"))
                assertEquals("2", url.queryParameter("limit"))
                assertEquals("*", url.queryParameter("select"))
                assertTrue(requireNotNull(url.encodedQuery).contains("id=" + expectedQuery.replace(":", "%3A")))
            }
        }

    @Test
    fun `all three methods preserve successful empty results`() =
        runBlocking {
            val repository = repository()
            server.enqueue(json("[]"))
            server.enqueue(json("[]"))
            val emptySearch =
                searchObject().apply {
                    add("spots", JsonArray())
                    addProperty("count", 0)
                }
            server.enqueue(json(emptySearch.toString()))

            assertTrue(repository.getSpots().isEmpty())
            assertNull(repository.getSpotById(osmId))
            assertTrue(repository.searchNearby(center, 1000).isEmpty())
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `search serializes small exact coordinates independently of locale`() =
        runBlocking {
            val previous = Locale.getDefault()
            try {
                Locale.setDefault(Locale.GERMANY)
                server.enqueue(json(fixture("search-spots.json")))

                repository().searchNearby(GeoPoint(0.000001, -0.000001), 100, SportType.FOOTBALL)

                val url = requireNotNull(takeRequest().requestUrl)
                assertEquals("0.000001", url.queryParameter("lat"))
                assertEquals("-0.000001", url.queryParameter("lon"))
                assertEquals("100", url.queryParameter("radius"))
                assertEquals("soccer", url.queryParameter("sport"))
            } finally {
                Locale.setDefault(previous)
            }
        }

    @Test
    fun `null and workout filters produce all and fitness query values`() =
        runBlocking {
            val repository = repository()
            server.enqueue(json(fixture("search-spots.json")))
            server.enqueue(json(fixture("search-spots.json")))

            repository.searchNearby(center, 3000)
            repository.searchNearby(center, 1000, SportType.WORKOUT)

            assertEquals("all", requireNotNull(takeRequest().requestUrl).queryParameter("sport"))
            assertEquals("fitness", requireNotNull(takeRequest().requestUrl).queryParameter("sport"))
        }

    @Test
    fun `invalid search arguments and IDs fail before HTTP`() =
        runBlocking {
            val repository = repository(online = false)
            val operations: List<suspend () -> Any?> =
                listOf(
                    { repository.searchNearby(center, 99) },
                    { repository.searchNearby(center, 3001) },
                    { repository.searchNearby(center, 1000, SportType.OTHER) },
                    { repository.searchNearby(GeoPoint(56.3269001, 44.0059), 1000) },
                    { repository.searchNearby(GeoPoint(56.3269, 44.0059001), 1000) },
                    { repository.getSpotById(SpotId("osm", "12345")) },
                    { repository.getSpotById(SpotId("community", "invalid-uuid")) },
                )

            operations.forEach { assertError(NetworkError.InvalidRequest, it) }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `offline monitor prevents all three methods from sending HTTP`() =
        runBlocking {
            val repository = repository(online = false)

            assertError(NetworkError.Offline) { repository.getSpots() }
            assertError(NetworkError.Offline) { repository.getSpotById(osmId) }
            assertError(NetworkError.Offline) { repository.searchNearby(center, 1000) }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `all three methods preserve HTTP failures instead of returning empty results`() =
        runBlocking {
            val repository = repository()
            val operations: List<suspend () -> Any?> =
                listOf(
                    { repository.getSpots() },
                    { repository.getSpotById(osmId) },
                    { repository.searchNearby(center, 1000) },
                )

            operations.forEach { operation ->
                server.enqueue(
                    json(
                        """{"error":{"code":"UPSTREAM_UNAVAILABLE","message":"Temporary service error","request_id":"4c59e8fc-1342-4f15-aa3f-4ecc97f0d1c2"}}""",
                    ).setResponseCode(503)
                        .addHeader("Retry-After", "60"),
                )
                assertError(NetworkError.Http(503, "UPSTREAM_UNAVAILABLE", 60), operation)
            }
        }

    @Test
    fun `HTTP 404 for lookup remains an HTTP error`() =
        runBlocking {
            server.enqueue(json("""{"code":"NOT_FOUND","message":"Missing"}""").setResponseCode(404))

            assertError(NetworkError.Http(404, "NOT_FOUND")) { repository().getSpotById(osmId) }
        }

    @Test
    fun `malformed null and inconsistent search payloads become Parsing`() =
        runBlocking {
            val repository = repository()
            val wrongCount = searchObject().apply { addProperty("count", 0) }
            val responses = listOf("{", "null", wrongCount.toString())

            responses.forEach { body ->
                server.enqueue(json(body))
                assertError(NetworkError.Parsing) { repository.searchNearby(center, 1000) }
            }
        }

    @Test
    fun `null and partial REST rows become Parsing`() =
        runBlocking {
            val repository = repository()

            listOf("[null]", "null", """[{"id":"osm:node:12345","name":"Spot","sport_type":"soccer"}]""").forEach { body ->
                server.enqueue(json(body))
                assertError(NetworkError.Parsing) { repository.getSpots() }
            }
        }

    @Test
    fun `HTTP string lighting is a parsing failure and cannot become false`() =
        runBlocking {
            val response =
                searchObject().apply {
                    getAsJsonArray("spots")[0].asJsonObject.addProperty("lighting", "unknown")
                }
            server.enqueue(json(response.toString()))

            assertError(NetworkError.Parsing) { repository().searchNearby(center, 1000) }
        }

    @Test
    fun `lookup rejects multiple rows and a row with another identifier`() =
        runBlocking {
            val repository = repository()
            server.enqueue(json(fixture("saved-spots.json")))
            server.enqueue(json(singleSavedRow(1)))
            server.enqueue(json("[null]"))

            repeat(3) { assertError(NetworkError.Parsing) { repository.getSpotById(osmId) } }
        }

    @Test
    fun `saved list reads every page with deterministic order and offsets`() =
        runBlocking {
            server.enqueue(json(page(firstId = 1, count = 100)))
            server.enqueue(json(page(firstId = 101, count = 2)))

            val spots = repository().getSpots()

            assertEquals((1..102).map { SpotId("osm", "node:$it") }, spots.map { it.id })
            val first = requireNotNull(takeRequest().requestUrl)
            val second = requireNotNull(takeRequest().requestUrl)
            assertEquals("0", first.queryParameter("offset"))
            assertEquals("100", second.queryParameter("offset"))
            listOf(first, second).forEach {
                assertEquals("100", it.queryParameter("limit"))
                assertEquals("id.asc", it.queryParameter("order"))
                assertEquals("*", it.queryParameter("select"))
            }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `duplicate rows inside a page are rejected`() =
        runBlocking {
            val row = savedRows()[0]
            server.enqueue(
                json(
                    JsonArray()
                        .apply {
                            add(row)
                            add(row.deepCopy())
                        }.toString(),
                ),
            )

            assertError(NetworkError.Parsing) { repository().getSpots() }
        }

    @Test
    fun `exactly full final page is followed by a successful empty page`() =
        runBlocking {
            server.enqueue(json(page(firstId = 1, count = 100)))
            server.enqueue(json("[]"))

            assertEquals(100, repository().getSpots().size)
            assertEquals("0", requireNotNull(takeRequest().requestUrl).queryParameter("offset"))
            assertEquals("100", requireNotNull(takeRequest().requestUrl).queryParameter("offset"))
        }

    @Test
    fun `server ignoring pagination is rejected instead of looping indefinitely`() =
        runBlocking {
            server.enqueue(json(page(firstId = 1, count = 100)))
            server.enqueue(json(page(firstId = 1, count = 100)))

            assertError(NetworkError.Parsing) { repository().getSpots() }
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `error on a later page does not return a partial list`() =
        runBlocking {
            server.enqueue(json(page(firstId = 1, count = 100)))
            server.enqueue(json("{}").setResponseCode(500))

            assertError(NetworkError.Http(500)) { repository().getSpots() }
        }

    @Test
    fun `real socket read timeout becomes Timeout`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            assertError(NetworkError.Timeout) {
                repository(readTimeoutMillis = 500, callTimeoutMillis = 5000).getSpots()
            }
            assertEquals("GET", takeRequest().method)
        }

    @Test
    fun `real OkHttp call timeout becomes Timeout`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            assertError(NetworkError.Timeout) {
                repository(readTimeoutMillis = 5000, callTimeoutMillis = 500).searchNearby(center, 1000)
            }
            assertEquals("GET", takeRequest().method)
        }

    private fun repository(
        online: Boolean = true,
        readTimeoutMillis: Long = 2000,
        callTimeoutMillis: Long = 5000,
    ): SpotRepository {
        val config =
            NetworkConfig(
                baseUrl = server.url("/"),
                publishableKey = "test-publishable-key",
                connectTimeoutMillis = 1000,
                readTimeoutMillis = readTimeoutMillis,
                writeTimeoutMillis = 1000,
                callTimeoutMillis = callTimeoutMillis,
            )
        val client = NetworkClient.createOkHttp(config, AccessTokenProvider { null }).also { clients += it }
        val api = NetworkClient.createRetrofit(config, client, gson).create(SpotApi::class.java)
        val remote = RemoteSpotDataSource(api, NetworkCall(NetworkMonitor { online }, gson))
        return object : SpotRepository {
            override suspend fun getSpots(): List<Spot> = remote.getSpots()

            override suspend fun getSpotById(id: SpotId): Spot? = remote.getSpotById(validatedCanonicalId(id))

            override suspend fun searchNearby(
                center: GeoPoint,
                radiusMeters: Int,
                sportType: SportType?,
            ): List<Spot> = remote.searchNearby(validatedSearchRequest(center, radiusMeters, sportType))
        }
    }

    private suspend fun assertError(
        expected: NetworkError,
        block: suspend () -> Any?,
    ) {
        try {
            block()
            fail("Expected a typed network failure")
        } catch (failure: NetworkException) {
            assertEquals(expected, failure.error)
        }
    }

    private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun takeRequest(): RecordedRequest = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun searchObject(): JsonObject = gson.fromJson(fixture("search-spots.json"), JsonObject::class.java)

    private fun savedRows(): JsonArray = gson.fromJson(fixture("saved-spots.json"), JsonArray::class.java)

    private fun singleSavedRow(index: Int): String = JsonArray().apply { add(savedRows()[index]) }.toString()

    // Deterministic contract-derived variants of the OSM fixture, not live server records.
    private fun page(
        firstId: Int,
        count: Int,
    ): String {
        val template = savedRows()[0].asJsonObject
        val rows = JsonArray()
        repeat(count) { index ->
            rows.add(
                template.deepCopy().apply {
                    val id = firstId + index
                    addProperty("id", "osm:node:$id")
                    addProperty("osm_id", id)
                },
            )
        }
        return rows.toString()
    }

    private fun fixture(name: String): String = javaClass.getResource("/fixtures/$name")!!.readText()
}
