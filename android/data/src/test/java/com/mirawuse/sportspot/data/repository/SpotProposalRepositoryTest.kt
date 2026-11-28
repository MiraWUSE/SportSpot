package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonParser
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.remote.api.ProposalApi
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.NetworkIssue
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
            assertEquals(DomainError.Network(NetworkIssue.INVALID_RESPONSE), contentFailure { repo(other).getMyProposals(BOB_ID) })
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
            assertEquals(DomainError.Network(NetworkIssue.OFFLINE), contentFailure { repo(http).getMyProposals(ALICE_ID) })
            assertFalse(server.requestCount > 0)
        }

    private fun repo(fixture: ContentHttpFixture) =
        SpotProposalRepositoryImpl(
            fixture.retrofit.create(ProposalApi::class.java),
            fixture.call,
            fixture.network,
            fixture.retrofit.create(AuthApi::class.java),
        )

    @Test
    fun pendingQueueRequiresAConfirmedModeratorRole() =
        runBlocking {
            http.enqueue("""[{"id":"$ALICE_ID","role":"user"}]""")
            assertEquals(DomainError.Forbidden, contentFailure { repo(http).getPendingProposals(ALICE_ID) })
            assertEquals("/rest/v1/profiles", http.next().requestUrl?.encodedPath)
            assertEquals(1, server.requestCount)
            http.enqueue("""[{"id":"$ALICE_ID","role":"moderator"}]""")
            http.enqueue(proposal())
            assertEquals(ITEM_ID, repo(http).getPendingProposals(ALICE_ID).single().id)
            http.next()
            val queue = http.next()
            assertEquals("eq.pending", queue.requestUrl?.queryParameter("status"))
            assertNull(queue.requestUrl?.queryParameter("user_id"))
            assertEquals("Bearer access-$ALICE_ID", queue.getHeader("Authorization"))
        }

    @Test
    fun decisionsUseTheEdgeFunctionAndReadTheFullProposalAfterItsAcknowledgment() =
        runBlocking {
            http.enqueue("""{"proposal_id":"$ITEM_ID","status":"approved","spot_id":"community:$ITEM_ID"}""")
            http.enqueue(proposal(status = "approved"))
            assertEquals(ProposalStatus.APPROVED, repo(http).approveProposal(ALICE_ID, ITEM_ID).status)
            val approve = http.next()
            assertEquals("/functions/v1/moderate-spot-proposal-v1", approve.requestUrl?.encodedPath)
            assertEquals("Bearer access-$ALICE_ID", approve.getHeader("Authorization"))
            assertEquals(setOf("proposal_id", "decision"), JsonParser.parseString(approve.body.readUtf8()).asJsonObject.keySet())
            assertEquals("eq.$ITEM_ID", http.next().requestUrl?.queryParameter("id"))

            http.enqueue("""{"proposal_id":"$ITEM_ID","status":"rejected"}""")
            http.enqueue(proposal(status = "rejected", reason = "Не хватает адреса"))
            val result = repo(http).rejectProposal(ALICE_ID, ITEM_ID, "Не хватает адреса")
            assertEquals("Не хватает адреса", result.rejectReason)
            val reject = http.next()
            assertEquals("Не хватает адреса", JsonParser.parseString(reject.body.readUtf8()).asJsonObject["reason"].asString)
        }

    @Test
    fun moderationFailuresNeverReadOrPublishAProposalFromTheClient() =
        runBlocking {
            val failureCases =
                mapOf(
                    403 to DomainError.Forbidden,
                    404 to DomainError.NotFound,
                    409 to DomainError.Conflict,
                    503 to DomainError.Network(NetworkIssue.SERVER),
                )
            for ((status, failure) in failureCases) {
                http.enqueue("{}", status)
                assertEquals(failure, contentFailure { repo(http).approveProposal(ALICE_ID, ITEM_ID) })
                assertEquals("/functions/v1/moderate-spot-proposal-v1", http.next().requestUrl?.encodedPath)
            }
            assertEquals(failureCases.size, server.requestCount)
        }

    private fun proposal(
        status: String = "pending",
        reason: String? = null,
    ) = """
        [{"id":"$ITEM_ID","user_id":"$ALICE_ID","name":"Корт","sport_type":"tennis","latitude":56.3269,"longitude":44.0059,
        "status":"$status","reject_reason":${reason?.let { "\"$it\"" } ?: "null"},"created_at":"2026-11-24T09:15:00Z"}]
        """.trimIndent()
}
