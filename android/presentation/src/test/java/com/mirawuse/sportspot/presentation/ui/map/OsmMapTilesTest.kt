package com.mirawuse.sportspot.presentation.ui.map

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OsmMapTilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `map requests identify SportSpot and reuse server cache headers`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("tile").setHeader("Cache-Control", "max-age=3600").setHeader("ETag", "tile-v1"))
            val client = OsmMapTiles.createClient(temporaryFolder.newFolder())
            try {
                val request = Request.Builder().url(server.url("/14/1/2.png")).build()
                repeat(2) { client.newCall(request).execute().use { assertEquals("tile", it.body?.string()) } }
                assertEquals(1, server.requestCount)
                val received = server.takeRequest()
                assertEquals(OsmMapTiles.USER_AGENT, received.getHeader("User-Agent"))
                assertNull(received.getHeader("Cache-Control"))
                assertNull(received.getHeader("Pragma"))
            } finally {
                client.cache?.close()
            }
        }
    }

    @Test
    fun `expired tiles are conditionally revalidated with etag`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("tile").setHeader("Cache-Control", "max-age=0").setHeader("ETag", "tile-v1"))
            server.enqueue(MockResponse().setResponseCode(304).setHeader("Cache-Control", "max-age=3600"))
            val client = OsmMapTiles.createClient(temporaryFolder.newFolder())
            try {
                val request = Request.Builder().url(server.url("/14/1/2.png")).build()
                repeat(2) { client.newCall(request).execute().use { assertEquals("tile", it.body?.string()) } }
                assertEquals(2, server.requestCount)
                server.takeRequest()
                assertEquals("tile-v1", server.takeRequest().getHeader("If-None-Match"))
            } finally {
                client.cache?.close()
            }
        }
    }

    @Test
    fun `failed tile responses do not replace a later successful cached tile`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("tile").setHeader("Cache-Control", "max-age=3600"))
            val client = OsmMapTiles.createClient(temporaryFolder.newFolder())
            try {
                val request = Request.Builder().url(server.url("/14/1/2.png")).build()
                client.newCall(request).execute().use { assertEquals(503, it.code) }
                repeat(2) { client.newCall(request).execute().use { assertEquals("tile", it.body?.string()) } }
                assertEquals(2, server.requestCount)
            } finally {
                client.cache?.close()
            }
        }
    }
}
