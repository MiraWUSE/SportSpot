package com.mirawuse.sportspot.data.remote

import com.mirawuse.sportspot.data.remote.api.UserPhotoApi
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.repository.ALICE_ID
import com.mirawuse.sportspot.data.repository.ContentHttpFixture
import com.mirawuse.sportspot.data.repository.ITEM_ID
import com.mirawuse.sportspot.data.repository.UserPhotoRepositoryImpl
import com.mirawuse.sportspot.data.repository.toPhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoStatus
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class UserPhotoApiTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun photoRequestsPinAccountAndUseTheSameUploadIdOnRetry() =
        runBlocking {
            val config = NetworkConfig(server.url("/"), publishableKey = "test-publishable-key")
            val client = NetworkClient.createOkHttp(config, AccessTokenProvider { "new-account-token" })
            val api = NetworkClient.createRetrofit(config, client).create(UserPhotoApi::class.java)
            val id = "11111111-1111-4111-8111-111111111111"
            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(503))
                api.upload(
                    id,
                    "spot",
                    "osm:node:4",
                    byteArrayOf(1).toRequestBody("image/jpeg".toMediaType()),
                    RequestSession("owner-token"),
                )
                val request = server.takeRequest()
                assertEquals("Bearer owner-token", request.getHeader("Authorization"))
                assertEquals("image/jpeg", request.getHeader("Content-Type"))
                assertEquals("test-publishable-key", request.getHeader("apikey"))
                assertEquals("/functions/v1/user-photos-v1", request.requestUrl?.encodedPath)
                assertEquals(id, request.requestUrl?.queryParameter("id"))
                assertEquals("osm:node:4", request.requestUrl?.queryParameter("target_id"))
            }
            server.enqueue(MockResponse().setBody("[]"))
            api.gallery("spot", "osm:node:4", RequestSession(null))
            assertNull(server.takeRequest().getHeader("Authorization"))
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }

    @Test
    fun repositoryReadsPendingAndSeparatesPublicDownloadsFromPrivateUploads() =
        runBlocking {
            val fixture = ContentHttpFixture(server)
            val api = fixture.retrofit.create(UserPhotoApi::class.java)
            val photos = UserPhotoRepositoryImpl(api, fixture.auth, fixture.network)
            val target = PhotoTarget(PhotoTargetType.SPOT, "osm:way:123")
            val bytes = byteArrayOf(1, 2, 3)
            fixture.enqueue(
                """{"id":"$ITEM_ID","owner_id":"$ALICE_ID","target_type":"spot","target_id":"osm:way:123","status":"pending"}""",
                201,
            )
            val pending = photos.upload(ALICE_ID, target, PhotoAttachment(ITEM_ID, bytes))
            assertEquals(PhotoStatus.PENDING, pending.status)
            assertEquals(target, pending.target)
            assertArrayEquals(bytes, fixture.next().body.readByteArray())

            server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes)))
            assertArrayEquals(bytes, photos.image(ITEM_ID))
            val image = fixture.next()
            assertNull(image.getHeader("Authorization"))
            assertEquals("image", image.requestUrl?.queryParameter("action"))

            fixture.enqueue("""{"code":"PHOTO_LIMIT"}""", 409)
            val limit = runCatching { photos.upload(ALICE_ID, target, PhotoAttachment(ITEM_ID, bytes)) }.exceptionOrNull()
            assertEquals(PhotoError.LIMIT, (limit as? PhotoException)?.error)
            fixture.next()

            fixture.online = false
            val offline = runCatching { photos.image(ITEM_ID) }.exceptionOrNull()
            assertEquals(PhotoError.OFFLINE, (offline as? PhotoException)?.error)
            assertEquals(3, server.requestCount)
        }

    @Test
    fun photoErrorsStayDistinct() {
        assertEquals(PhotoError.OFFLINE, NetworkError.Offline.toPhotoError())
        assertEquals(PhotoError.UNAUTHORIZED, NetworkError.Http(401).toPhotoError())
        assertEquals(PhotoError.FORBIDDEN, NetworkError.Http(403).toPhotoError())
        assertEquals(PhotoError.CONFLICT, NetworkError.Http(409).toPhotoError())
        assertEquals(PhotoError.LIMIT, NetworkError.Http(409, "PHOTO_LIMIT").toPhotoError())
        assertEquals(PhotoError.TOO_LARGE, NetworkError.Http(413).toPhotoError())
        assertEquals(PhotoError.RATE_LIMITED, NetworkError.Http(429).toPhotoError())
        assertEquals(PhotoError.UNAVAILABLE, NetworkError.Http(503).toPhotoError())
    }
}
