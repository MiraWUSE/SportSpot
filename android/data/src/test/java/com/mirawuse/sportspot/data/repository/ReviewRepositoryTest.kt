package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonParser
import com.mirawuse.sportspot.data.remote.api.ReviewApi
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.result.DomainError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReviewRepositoryTest {
    @get:Rule val server = MockWebServer()
    private val http = ContentHttpFixture(server)
    private val repo by lazy { ReviewRepositoryImpl(http.retrofit.create(ReviewApi::class.java), http.call, http.network) }

    @Test
    fun publicReadsUseCanonicalFilterAndNoUserToken() =
        runBlocking {
            for ((id, canonical) in listOf(osm to "osm:way:42", SpotId("community", ITEM_ID) to "community:$ITEM_ID")) {
                http.enqueue(row(canonical))
                val review = repo.getReviewsBySpot(id).single()
                assertEquals(id, review.spotId)
                assertEquals(5, review.rating)
                assertEquals("Хороший корт", review.title)
                assertEquals("2026-11-24", review.createdAt?.take(10))
                val request = http.next()
                assertEquals("eq.$canonical", request.requestUrl?.queryParameter("spot_id"))
                assertNull(request.getHeader("Authorization"))
                assertFalse(
                    request.requestUrl
                        ?.queryParameter("select")
                        .orEmpty()
                        .contains("profiles"),
                )
            }
            http.enqueue("[]")
            assertTrue(repo.getReviewsBySpot(osm).isEmpty())
        }

    @Test
    fun ownReviewsUseTheAccountTokenAndReadPagesInServerOrder() =
        runBlocking {
            val ids = (1..101).map { "12345678-1234-4123-8123-${it.toString().padStart(12, '0')}" }
            val firstPage = ids.take(100).map { reviewRow(id = it) }
            http.enqueue(firstPage.joinToString(",", "[", "]"))
            http.enqueue("[${firstPage.last()},${reviewRow(canonical = "community:$ITEM_ID", id = ids.last())}]")

            val reviews = repo.getReviewsByUser(ALICE_ID)
            assertEquals(ids, reviews.map { it.id })
            assertTrue(reviews.all { it.authorId == ALICE_ID })
            assertEquals(osm, reviews.first().spotId)
            assertEquals(SpotId("community", ITEM_ID), reviews.last().spotId)
            for (offset in listOf("0", "100")) {
                val request = http.next()
                assertEquals("Bearer access-$ALICE_ID", request.getHeader("Authorization"))
                val url = checkNotNull(request.requestUrl)
                assertEquals("/rest/v1/reviews", url.encodedPath)
                assertEquals("eq.$ALICE_ID", url.queryParameter("user_id"))
                assertNull(url.queryParameter("spot_id"))
                assertEquals("created_at.desc,id.asc", url.queryParameter("order"))
                assertEquals("100", url.queryParameter("limit"))
                assertEquals(offset, url.queryParameter("offset"))
            }
        }

    @Test
    fun ownReviewsRejectUnexpectedRowsAndNonAdvancingPages() =
        runBlocking {
            for (invalid in listOf(reviewRow(user = BOB_ID), reviewRow(id = "bad-id"), reviewRow(canonical = "osm:invalid"))) {
                http.enqueue("[$invalid]")
                assertEquals(DomainError.Unexpected, contentFailure { repo.getReviewsByUser(ALICE_ID) })
            }
            val stuck = List(100) { reviewRow() }.joinToString(",", "[", "]")
            http.enqueue(stuck)
            http.enqueue(stuck)
            assertEquals(DomainError.Unexpected, contentFailure { repo.getReviewsByUser(ALICE_ID) })
            assertEquals(5, server.requestCount)
        }

    @Test
    fun ownReviewsSeparateEmptyResultsFromPermissionAndNetworkErrors() =
        runBlocking {
            assertEquals(DomainError.Unauthorized, contentFailure { repo.getReviewsByUser(BOB_ID) })
            assertEquals(0, server.requestCount)
            http.enqueue("[]")
            assertTrue(repo.getReviewsByUser(ALICE_ID).isEmpty())
            http.enqueue("{}", 403)
            assertEquals(DomainError.Forbidden, contentFailure { repo.getReviewsByUser(ALICE_ID) })
            http.online = false
            assertEquals(DomainError.Unavailable, contentFailure { repo.getReviewsByUser(ALICE_ID) })
        }

    @Test
    fun protectedWritesPinTheAuthorAndAllowClearingTheTitle() =
        runBlocking {
            http.enqueue(row(), 201)
            assertEquals(ALICE_ID, repo.createReview(ALICE_ID, osm, 5, "Текст", "Хороший корт").authorId)
            val post = http.next()
            assertEquals("Bearer access-$ALICE_ID", post.getHeader("Authorization"))
            val body = JsonParser.parseString(post.body.readUtf8()).asJsonObject
            assertEquals(setOf("spot_id", "rating", "text", "title"), body.keySet())

            http.enqueue(row(title = "null"))
            assertNull(repo.updateReview(ALICE_ID, ITEM_ID, 5, "Текст", null).title)
            val patch = http.next()
            assertEquals("PATCH", patch.method)
            assertEquals("eq.$ITEM_ID", patch.requestUrl?.queryParameter("id"))
            assertEquals("eq.$ALICE_ID", patch.requestUrl?.queryParameter("user_id"))
            assertTrue(JsonParser.parseString(patch.body.readUtf8()).asJsonObject["title"].isJsonNull)

            http.enqueue("""[{"id":"$ITEM_ID"}]""")
            repo.deleteReview(ALICE_ID, ITEM_ID)
            assertEquals("DELETE", http.next().method)
        }

    @Test
    fun rejectsWrongSessionAndMapsConflictForbiddenMissingAndOffline() =
        runBlocking {
            assertEquals(DomainError.Unauthorized, contentFailure { repo.deleteReview(BOB_ID, ITEM_ID) })
            assertEquals(0, server.requestCount)
            http.enqueue("""{"code":"23505"}""", 409)
            assertEquals(DomainError.Conflict, contentFailure { repo.createReview(ALICE_ID, osm, 5, "Текст", null) })
            http.enqueue("{}", 403)
            assertEquals(DomainError.Forbidden, contentFailure { repo.deleteReview(ALICE_ID, ITEM_ID) })
            http.enqueue("[]")
            assertEquals(DomainError.NotFound, contentFailure { repo.deleteReview(ALICE_ID, ITEM_ID) })
            http.enqueue("{}", 400)
            assertTrue(contentFailure { repo.createReview(ALICE_ID, osm, 0, "Текст", null) } is DomainError.Validation)
            http.online = false
            assertEquals(DomainError.Unavailable, contentFailure { repo.getReviewsBySpot(osm) })
            http.auth.signOut()
            assertEquals(DomainError.Unauthorized, contentFailure { repo.deleteReview(ALICE_ID, ITEM_ID) })
            assertEquals(4, server.requestCount)
        }

    private fun row(
        canonical: String = "osm:way:42",
        title: String = "\"Хороший корт\"",
    ) = "[${reviewRow(canonical, title)}]"

    private fun reviewRow(
        canonical: String = "osm:way:42",
        title: String = "\"Хороший корт\"",
        id: String = ITEM_ID,
        user: String = ALICE_ID,
    ) = """
        {"id":"$id","user_id":"$user","spot_id":"$canonical",
        "rating":5,"text":"Текст","title":$title,"created_at":"2026-11-24T09:00:00+00:00"}
        """.trimIndent()

    private companion object {
        val osm = SpotId("osm", "way:42")
    }
}
