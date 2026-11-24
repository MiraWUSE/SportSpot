package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonParser
import com.mirawuse.sportspot.data.remote.api.ProposalApi
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.result.DomainError
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpotProposalRepositoryTest {
    @get:Rule val server = MockWebServer()
    private val http = ContentHttpFixture(server)
    private val point = GeoPoint(56.3269, 44.0059)

    @Test
    fun submissionHasOnlyEditableFieldsAndMapsTheServerPendingId() =
        runBlocking {
            http.enqueue(proposal(), 201)
            val result = repo(http).createProposal(ALICE_ID, "Корт", SportType.TENNIS, point, null)
            assertEquals(ITEM_ID, result.id)
            assertEquals(ProposalStatus.PENDING, result.status)
            assertEquals(point, result.location)
            assertNull(result.address)
            val request = http.next()
            assertEquals("POST", request.method)
            assertEquals("/rest/v1/spot_proposals", request.requestUrl?.encodedPath)
            assertEquals("Bearer access-$ALICE_ID", request.getHeader("Authorization"))
            val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(setOf("name", "sport_type", "latitude", "longitude"), payload.keySet())
            assertEquals("tennis", payload["sport_type"].asString)
        }

    @Test
    fun ownerFilterAndPinnedCredentialsAreIndependentForTwoAccounts() =
        runBlocking {
            http.enqueue(proposal(status = "rejected", reason = "Не хватает адреса"))
            val first = repo(http).getMyProposals(ALICE_ID).single()
            assertEquals(ProposalStatus.REJECTED, first.status)
            assertEquals("Не хватает адреса", first.rejectReason)
            assertEquals("eq.$ALICE_ID", http.next().requestUrl?.queryParameter("user_id"))

            val other = ContentHttpFixture(server, BOB_ID)
            http.enqueue("[]")
            assertTrue(repo(other).getMyProposals(BOB_ID).isEmpty())
            val request = other.next()
            assertEquals("Bearer access-$BOB_ID", request.getHeader("Authorization"))
            assertEquals("eq.$BOB_ID", request.requestUrl?.queryParameter("user_id"))
            http.enqueue(proposal())
            assertEquals(DomainError.Unexpected, contentFailure { repo(other).getMyProposals(BOB_ID) })
        }

    @Test
    fun guestAndOfflineFailuresDoNotSubmit() =
        runBlocking {
            val guest = ContentHttpFixture(server, null)
            assertEquals(
                DomainError.Unauthorized,
                contentFailure { repo(guest).createProposal(ALICE_ID, "Корт", SportType.OTHER, point, null) },
            )
            http.online = false
            assertEquals(DomainError.Unavailable, contentFailure { repo(http).getMyProposals(ALICE_ID) })
            assertFalse(server.requestCount > 0)
        }

    private fun repo(fixture: ContentHttpFixture) =
        SpotProposalRepositoryImpl(fixture.retrofit.create(ProposalApi::class.java), fixture.call, fixture.network)

    private fun proposal(
        status: String = "pending",
        reason: String? = null,
    ) = """
        [{"id":"$ITEM_ID","user_id":"$ALICE_ID","name":"Корт","sport_type":"tennis","latitude":56.3269,"longitude":44.0059,
        "status":"$status","reject_reason":${reason?.let { "\"$it\"" } ?: "null"},"created_at":"2026-11-24T09:15:00Z"}]
        """.trimIndent()
}
