package com.mirawuse.sportspot.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkClientTest {
    @get:Rule
    val server = MockWebServer()

    private val clients = mutableListOf<OkHttpClient>()

    @After
    fun closeClients() {
        clients.forEach {
            it.connectionPool.evictAll()
            it.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun `public request uses configured apikey without authorization`() {
        server.enqueue(MockResponse().setBody("[]"))
        val client = client()
        client
            .newCall(
                Request
                    .Builder()
                    .url(server.url("/rest/v1/spots"))
                    .header("apikey", "incorrect-key")
                    .header("Authorization", "Bearer stale-token")
                    .build(),
            ).execute()
            .use { assertEquals(200, it.code) }

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("test-publishable-key", request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `session token and apikey use separate headers`() {
        server.enqueue(MockResponse())
        client(token = "test.user.token").newCall(request()).execute().close()
        val recorded = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("Bearer test.user.token", recorded.getHeader("Authorization"))
        assertEquals("test-publishable-key", recorded.getHeader("apikey"))
    }

    @Test
    fun `unconfigured public key and blank session omit credential headers`() {
        server.enqueue(MockResponse())
        client(key = "", token = " ").newCall(request()).execute().close()
        val recorded = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertNull(recorded.getHeader("apikey"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `api key cannot be supplied as session token`() {
        val failure =
            assertThrows(IOException::class.java) {
                client(token = "sb_publishable_test-only").newCall(request()).execute()
            }
        assertFalse(failure.message.orEmpty().contains("sb_publishable_test-only"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `debug logs contain only safe path status and duration`() {
        val logs = mutableListOf<String>()
        server.enqueue(MockResponse().setBody("secret-response-body").addHeader("Set-Cookie", "secret-cookie"))
        val config = NetworkConfig(server.url("/"), "test-publishable-key", debugLogging = true)
        val client = create(config, "test.user.token", logs)
        val request =
            Request
                .Builder()
                .url(server.url("/functions/v1/search-sports-spots-v1?lat=56.3269&lon=44.0059&access_token=secret-query"))
                .header("Cookie", "secret-cookie")
                .build()
        client.newCall(request).execute().use { it.body?.string() }

        val log = logs.single()
        assertTrue(log.contains("GET /functions/v1/search-sports-spots-v1 -> 200"))
        listOf("56.3269", "44.0059", "test.user.token", "test-publishable-key", "secret-", "Cookie", "Authorization", "apikey")
            .forEach { assertFalse(log.contains(it)) }
    }

    @Test
    fun `release logs are disabled`() {
        val logs = mutableListOf<String>()
        server.enqueue(MockResponse())
        create(NetworkConfig(server.url("/")), null, logs).newCall(request()).execute().close()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `failed request logs do not reveal exception messages or query values`() {
        val logs = mutableListOf<String>()
        val client =
            create(NetworkConfig(server.url("/"), debugLogging = true), null, logs)
                .newBuilder()
                .addInterceptor { throw IOException("sensitive-transport-details") }
                .build()
                .also { clients += it }

        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(server.url("/rest/v1/spots?private=sensitive-query")).build()).execute()
        }

        assertTrue(logs.single().contains("GET /rest/v1/spots -> failed"))
        assertFalse(logs.single().contains("sensitive-"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `secret and malformed API key configuration is rejected`() {
        listOf("sb_secret_test-only", "test-key\r\nInjected: value").forEach { key ->
            val failure = assertThrows(IllegalArgumentException::class.java) { NetworkConfig(publishableKey = key) }
            assertFalse(failure.message.orEmpty().contains(key))
        }
    }

    @Test
    fun `unknown paths do not expose identifiers in logs`() {
        val logs = mutableListOf<String>()
        server.enqueue(MockResponse())
        create(NetworkConfig(server.url("/"), debugLogging = true), null, logs)
            .newCall(Request.Builder().url(server.url("/users/private-user-id")).build())
            .execute()
            .close()
        assertTrue(logs.single().contains("/[redacted]"))
        assertFalse(logs.single().contains("private-user-id"))
    }

    @Test
    fun `redirects cannot forward credentials to another origin`() {
        MockWebServer().use { target ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", target.url("/")))
            client().newCall(request()).execute().use { assertEquals(302, it.code) }
            assertEquals(0, target.requestCount)
        }
    }

    @Test
    fun `requests to another origin are rejected before sending credentials`() {
        MockWebServer().use { target ->
            assertThrows(IOException::class.java) {
                client().newCall(Request.Builder().url(target.url("/rest/v1/spots")).build()).execute()
            }
            assertEquals(0, target.requestCount)
        }
    }

    @Test
    fun `production endpoint uses HTTPS and all timeouts are bounded`() {
        val config = NetworkConfig()
        val client = NetworkClient.createOkHttp(config, AccessTokenProvider { null }).also { clients += it }
        assertTrue(config.baseUrl.isHttps)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(45_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.callTimeoutMillis)
        assertFalse(client.retryOnConnectionFailure)
        assertThrows(IllegalArgumentException::class.java) {
            NetworkConfig(okhttp3.HttpUrl.Companion.run { "http://example.com/".toHttpUrl() })
        }
    }

    private fun request(): Request = Request.Builder().url(server.url("/rest/v1/spots")).build()

    private fun client(
        key: String = "test-publishable-key",
        token: String? = null,
    ): OkHttpClient = create(NetworkConfig(server.url("/"), key), token, mutableListOf())

    private fun create(
        config: NetworkConfig,
        token: String?,
        logs: MutableList<String>,
    ): OkHttpClient =
        NetworkClient
            .createOkHttp(config, AccessTokenProvider { token }, NetworkLogger { logs += it })
            .also { clients += it }
}
