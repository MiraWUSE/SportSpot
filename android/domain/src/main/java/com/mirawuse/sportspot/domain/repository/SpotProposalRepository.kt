package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType

interface SpotProposalRepository {
    suspend fun getMyProposals(): List<SpotProposal>

    suspend fun getPendingProposals(): List<SpotProposal>

    suspend fun createProposal(
        name: String,
        sportType: SportType,
        location: GeoPoint,
    ): SpotProposal

    suspend fun approveProposal(proposalId: String): SpotProposal

    suspend fun rejectProposal(
        proposalId: String,
        reason: String,
    ): SpotProposal
}
