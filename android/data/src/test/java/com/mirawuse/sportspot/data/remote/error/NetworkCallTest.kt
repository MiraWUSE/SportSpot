package com.mirawuse.sportspot.data.remote.error

import com.google.gson.Gson
import com.google.gson.JsonDeserializer
import com.google.gson.JsonParseException
import com.mirawuse.sportspot.data.mapper.SpotMappingException
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.NetworkConfig
import com.mirawuse.sportspot.data.remote.api.SpotApi
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.dto.SpotDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class NetworkCallTest {
    @get:Rule
    val server = MockWebServer()

    private val gson = NetworkClient.createGson()
    private val calls = NetworkCall(NetworkMonitor { true }, gson)
    private val clients = mutableListOf<OkHttpClient>()
    private val api by lazy { createApi() }

    private fun createApi(gson: Gson = this.gson): SpotApi {
        val config = NetworkConfig(server.url("/"))
        val client = NetworkClient.createOkHttp(config, AccessTokenProvider { null }).also { clients += it }
        return NetworkClient.createRetrofit(config, client, gson).create(SpotApi::class.java)
    }

    @After
    fun closeClients() {
        clients.forEach {
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `HTTP statuses remain distinguishable even without error JSON`() =
        runBlocking {
            listOf(400, 401, 403, 404, 409, 429, 500, 503).forEach { status ->
                server.enqueue(MockResponse().setResponseCode(status))

                val failure = failure { calls.body(api.getSpots()) }

                assertEquals(NetworkError.Http(status), failure.error)
                assertRestRequest()
            }
        }

    @Test
    fun `Edge error preserves safe code and retry delay without exposing server message`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .addHeader("Retry-After", "60")
                    .setBody(
                        """
                        {"error":{"code":"RATE_LIMITED","message":"sensitive-server-message","request_id":"4c59e8fc-1342-4f15-aa3f-4ecc97f0d1c2"}}
                        """.trimIndent(),
                    ),
            )

            val failure = failure { calls.body(api.getSpots()) }

            assertEquals(NetworkError.Http(429, "RATE_LIMITED", 60L), failure.error)
            assertFalse(failure.message.orEmpty().contains("sensitive-server-message"))
            assertFalse(failure.toString().contains("4c59e8fc-1342-4f15-aa3f-4ecc97f0d1c2"))
            assertRestRequest()
        }

    @Test
    fun `PostgREST and gateway errors preserve their flat codes`() =
        runBlocking {
            val errors =
                listOf(
                    Triple(409, "23505", """{"code":"23505","message":"sensitive details","details":null,"hint":null}"""),
                    Triple(
                        401,
                        "UNAUTHORIZED_NO_AUTH_HEADER",
                        """{"code":"UNAUTHORIZED_NO_AUTH_HEADER","message":"Missing authorization header"}""",
                    ),
                )
            errors.forEach { (status, code, body) ->
                server.enqueue(MockResponse().setResponseCode(status).setBody(body))

                val failure = failure { calls.body(api.getSpots()) }

                assertEquals(NetworkError.Http(status, code), failure.error)
                assertFalse(failure.message.orEmpty().contains("sensitive details"))
                assertRestRequest()
            }
        }

    @Test
    fun `malformed and unsafe error bodies cannot replace the HTTP failure`() =
        runBlocking {
            val bodies =
                listOf(
                    "<html>Service temporarily unavailable</html>",
                    "{",
                    "[]",
                    "null",
                    """{"error":null}""",
                    """{"code":["NOT_A_STRING"]}""",
                    """{"error":{"code":{"value":"NOT_A_STRING"}}}""",
                    """{"code":"sb_secret_private-value"}""",
                    """{"code":"user.token.value"}""",
                    """{"code":"${"A".repeat(65)}"}""",
                    """{"code":"SAFE_CODE","message":"${"A".repeat(8192)}"}""",
                )
            bodies.forEach { body ->
                server.enqueue(MockResponse().setResponseCode(503).setBody(body))

                val failure = failure { calls.body(api.getSpots()) }

                assertEquals(NetworkError.Http(503), failure.error)
                assertFalse(failure.message.orEmpty().contains("private-value"))
                assertRestRequest()
            }
        }

    @Test
    fun `invalid retry headers are ignored while HTTP status is retained`() =
        runBlocking {
            listOf("-1", "not-a-number", "9999999999999999999999999999999999").forEach { retryAfter ->
                server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", retryAfter))

                val failure = failure { calls.body(api.getSpots()) }

                assertEquals(NetworkError.Http(429), failure.error)
                assertRestRequest()
            }
        }

    @Test
    fun `offline precheck stops the HTTP request`() =
        runBlocking {
            val offline = NetworkCall(NetworkMonitor { false }, gson)
            var invoked = false
            server.enqueue(MockResponse().setBody("[]"))

            val failure =
                failure(offline) {
                    invoked = true
                    offline.body(api.getSpots())
                }

            assertEquals(NetworkError.Offline, failure.error)
            assertFalse(invoked)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `DNS connection IO and both timeout exceptions have distinct categories`() =
        runBlocking {
            val failures =
                listOf(
                    UnknownHostException("sensitive-host") to NetworkError.Dns,
                    ConnectException("sensitive-address") to NetworkError.Connection,
                    IOException("sensitive-transport-message") to NetworkError.Connection,
                    SocketTimeoutException("sensitive-timeout-message") to NetworkError.Timeout,
                    InterruptedIOException("timeout") to NetworkError.Timeout,
                )
            failures.forEach { (cause, expected) ->
                val failure = failure { throw cause }

                assertEquals(expected, failure.error)
                assertSame(cause, failure.cause)
                assertFalse(failure.message.orEmpty().contains("sensitive-"))
            }
        }

    @Test
    fun `Retrofit decoder errors including wrong shape EOF and invalid numbers are parsing failures`() =
        runBlocking {
            val bodies =
                listOf(
                    "",
                    "[",
                    "{}",
                    """[{"latitude":"not-a-number"}]""",
                    """[{"osm_id":1.5}]""",
                    "[] trailing-data",
                )
            bodies.forEach { body ->
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))

                val failure = failure { calls.body(api.getSpots()) }

                assertEquals(NetworkError.Parsing, failure.error)
                assertRestRequest()
            }
        }

    @Test
    fun `mapping and JSON failures are parsing while application argument errors are invalid requests`() =
        runBlocking {
            listOf(SpotMappingException(), JsonParseException("sensitive-json-message")).forEach { cause ->
                val failure = failure { throw cause }
                assertEquals(NetworkError.Parsing, failure.error)
                assertSame(cause, failure.cause)
            }

            val cause = IllegalArgumentException("sensitive-input")
            val failure = failure { throw cause }

            assertEquals(NetworkError.InvalidRequest, failure.error)
            assertSame(cause, failure.cause)
            assertFalse(failure.message.orEmpty().contains("sensitive-input"))
        }

    @Test
    fun `HTTP exceptions use the same typed handler as response errors`() =
        runBlocking {
            val response =
                Response.error<List<SpotDto?>>(
                    401,
                    """{"code":"PGRST301","message":"sensitive-token-diagnostic"}""".toResponseBody(),
                )
            val failure = failure { throw HttpException(response) }

            assertEquals(NetworkError.Http(401, "PGRST301"), failure.error)
            assertFalse(failure.message.orEmpty().contains("sensitive-token-diagnostic"))
        }

    @Test
    fun `absent successful body is a parsing failure`() =
        runBlocking {
            val failure = failure { calls.body(Response.success<List<SpotDto?>>(null)) }
            assertEquals(NetworkError.Parsing, failure.error)
        }

    @Test
    fun `cancellation during Retrofit decoding reaches the caller as cancellation`() =
        runBlocking {
            val cancelled = CancellationException("Test cancellation")
            val adapter = JsonDeserializer<SpotDto> { _, _, _ -> throw cancelled }
            val cancellingApi = createApi(gson.newBuilder().registerTypeAdapter(SpotDto::class.java, adapter).create())
            server.enqueue(MockResponse().setBody("[{}]"))

            val failure = runCatching { calls.execute { calls.body(cancellingApi.getSpots()) } }.exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertEquals(cancelled.message, failure?.message)
            assertRestRequest()
        }

    @Test
    fun `unexpected application failures preserve their cause without revealing its message`() =
        runBlocking {
            val cause = IllegalStateException("sensitive-application-state")

            val failure = failure { throw cause }

            assertEquals(NetworkError.Unexpected, failure.error)
            assertSame(cause, failure.cause)
            assertFalse(failure.message.orEmpty().contains("sensitive-application-state"))
        }

    @Test
    fun `existing typed failures are passed through unchanged`() =
        runBlocking {
            val expected = NetworkException(NetworkError.Offline)

            val actual = failure { throw expected }

            assertSame(expected, actual)
        }

    @Test
    fun `cancellation exception from the block is rethrown unchanged`() =
        runBlocking {
            val expected = CancellationException("Caller cancelled")
            var actual: CancellationException? = null

            try {
                calls.execute<Unit> { throw expected }
            } catch (failure: CancellationException) {
                actual = failure
            }

            assertSame(expected, actual)
        }

    @Test
    fun `already cancelled coroutine never checks connectivity or invokes its block`() =
        runBlocking {
            var monitored = false
            var invoked = false
            var observed: Throwable? = null
            val offline =
                NetworkCall(
                    NetworkMonitor {
                        monitored = true
                        false
                    },
                    gson,
                )
            val job =
                launch {
                    currentCoroutineContext().cancel()
                    try {
                        offline.execute { invoked = true }
                    } catch (failure: Throwable) {
                        observed = failure
                    }
                }

            job.join()

            assertTrue(observed is CancellationException)
            assertFalse(monitored)
            assertFalse(invoked)
        }

    @Test
    fun `transport failure racing with coroutine cancellation remains cancellation`() =
        runBlocking {
            var observed: Throwable? = null
            val job =
                launch {
                    try {
                        calls.execute<Unit> {
                            currentCoroutineContext().cancel()
                            throw IOException("Transport cancelled")
                        }
                    } catch (failure: Throwable) {
                        observed = failure
                    }
                }

            job.join()

            assertTrue(observed is CancellationException)
        }

    @Test
    fun `cancellation during synchronous response processing cannot escape as success`() =
        runBlocking {
            var completed = false
            var observed: Throwable? = null
            val job =
                launch {
                    try {
                        calls.execute {
                            currentCoroutineContext().cancel()
                            "completed response"
                        }
                        completed = true
                    } catch (failure: Throwable) {
                        observed = failure
                    }
                }

            job.join()

            assertTrue(observed is CancellationException)
            assertFalse(completed)
        }

    private suspend fun failure(
        executor: NetworkCall = calls,
        block: suspend () -> Any?,
    ): NetworkException {
        try {
            executor.execute(block)
        } catch (failure: NetworkException) {
            return failure
        }
        throw AssertionError("Expected a typed network failure")
    }

    private fun assertRestRequest() {
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/rest/v1/spots", request.requestUrl?.encodedPath)
    }
}
