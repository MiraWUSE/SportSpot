package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType

interface SpotProposalRepository {
    suspend fun getMyProposals(userId: String): List<SpotProposal>

    suspend fun getPendingProposals(userId: String): List<SpotProposal>

    suspend fun createProposal(
        userId: String,
        name: String,
        sportType: SportType,
        location: GeoPoint,
        address: String?,
    ): SpotProposal

    suspend fun approveProposal(
        userId: String,
        proposalId: String,
    ): SpotProposal

    suspend fun rejectProposal(
        userId: String,
        proposalId: String,
        reason: String,
    ): SpotProposal
}
