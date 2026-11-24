package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.mapper.toApiSport
import com.mirawuse.sportspot.data.mapper.toDomainSport
import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.remote.api.NewProposal
import com.mirawuse.sportspot.data.remote.api.ProposalApi
import com.mirawuse.sportspot.data.remote.api.ProposalDecision
import com.mirawuse.sportspot.data.remote.api.ProposalRow
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import javax.inject.Inject

internal class SpotProposalRepositoryImpl
    @Inject
    constructor(
        private val api: ProposalApi,
        private val call: ContentCall,
        private val network: NetworkCall,
        private val profiles: AuthApi,
    ) : SpotProposalRepository {
        override suspend fun getMyProposals(userId: String): List<SpotProposal> =
            call.execute(userId) { session ->
                list(session, owner = "eq.$userId").also { proposals ->
                    if (proposals.any { it.authorId != userId }) invalidContent()
                }
            }

        override suspend fun createProposal(
            userId: String,
            name: String,
            sportType: SportType,
            location: GeoPoint,
            address: String?,
        ): SpotProposal =
            call.execute(userId) { session ->
                val sport = if (sportType == SportType.OTHER) "other" else sportType.toApiSport()
                val body = NewProposal(name, sport, location.latitude, location.longitude, address)
                val proposal = network.body(api.create(body, session)).singleOrNull()?.toProposal() ?: invalidContent()
                if (proposal.authorId != userId || proposal.status != ProposalStatus.PENDING) invalidContent()
                proposal
            }

        override suspend fun getPendingProposals(userId: String): List<SpotProposal> =
            call.execute(userId) { session ->
                val profile = network.body(profiles.profile("eq.$userId", session)).singleOrNull()
                if (profile?.id != userId || profile.role != "moderator") throw NetworkException(NetworkError.Http(403))
                list(session, status = "eq.pending").also { proposals ->
                    if (proposals.any { it.status != ProposalStatus.PENDING }) invalidContent()
                }
            }

        override suspend fun approveProposal(
            userId: String,
            proposalId: String,
        ): SpotProposal = moderate(userId, proposalId, "approved", reason = null)

        override suspend fun rejectProposal(
            userId: String,
            proposalId: String,
            reason: String,
        ): SpotProposal = moderate(userId, proposalId, "rejected", reason)

        private suspend fun moderate(
            userId: String,
            proposalId: String,
            decision: String,
            reason: String?,
        ): SpotProposal {
            call.execute(userId) { session ->
                require(proposalId.isCanonicalUuid())
                val response = network.body(api.moderate(ProposalDecision(proposalId, decision, reason), session))
                val expectedSpotId = if (decision == "approved") "community:$proposalId" else null
                if (response.proposalId != proposalId || response.status != decision || response.spotId != expectedSpotId) {
                    invalidContent()
                }
            }
            // A 401 while rereading the row must only retry this GET.
            return call.execute(userId) { session ->
                val rows = network.body(api.list(null, null, "eq.$proposalId", 2, 0, session))
                val row = rows.singleOrNull() ?: invalidContent()
                if (row.id != proposalId || row.status != decision) invalidContent()
                row.toProposal()
            }
        }

        private suspend fun list(
            session: RequestSession,
            owner: String? = null,
            status: String? = null,
        ): List<SpotProposal> {
            val result = linkedMapOf<String, SpotProposal>()
            var offset = 0
            do {
                val page = network.body(api.list(owner, status, null, CONTENT_PAGE_SIZE, offset, session))
                val previousSize = result.size
                page.forEach { row ->
                    val proposal = row?.toProposal() ?: invalidContent()
                    result[proposal.id] = proposal
                }
                if (page.size == CONTENT_PAGE_SIZE && result.size == previousSize) invalidContent()
                offset += page.size
            } while (page.size == CONTENT_PAGE_SIZE)
            return result.values.toList()
        }

        private fun ProposalRow.toProposal(): SpotProposal =
            SpotProposal(
                id = id?.takeIf(String::isCanonicalUuid) ?: invalidContent(),
                authorId = userId?.takeIf(String::isCanonicalUuid) ?: invalidContent(),
                name = name?.takeIf(String::isNotBlank) ?: invalidContent(),
                sportType = sportType?.toDomainSport() ?: invalidContent(),
                location = GeoPoint(latitude ?: invalidContent(), longitude ?: invalidContent()),
                status =
                    when (status) {
                        "pending" -> ProposalStatus.PENDING
                        "approved" -> ProposalStatus.APPROVED
                        "rejected" -> ProposalStatus.REJECTED
                        else -> invalidContent()
                    },
                rejectReason = rejectReason,
                address = address,
                surface = surface,
                lighting = lighting,
                equipment = equipment,
                description = description,
                createdAt = createdAt,
            )
    }
