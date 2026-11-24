package com.mirawuse.sportspot.data.remote.api

import com.google.gson.annotations.SerializedName
import com.mirawuse.sportspot.data.remote.RequestSession
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Tag

private const val PROPOSALS =
    "rest/v1/spot_proposals?select=" +
        "id,user_id,name,sport_type,latitude,longitude,address,surface,lighting,equipment,description,status,reject_reason,created_at"

internal interface ProposalApi {
    @GET("$PROPOSALS&order=created_at.desc,id.asc")
    suspend fun list(
        @Query("user_id") owner: String?,
        @Query("status") status: String?,
        @Query("id") id: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Tag session: RequestSession,
    ): Response<List<ProposalRow?>>

    @Headers("Prefer: return=representation,missing=default")
    @POST(PROPOSALS)
    suspend fun create(
        @Body proposal: NewProposal,
        @Tag session: RequestSession,
    ): Response<List<ProposalRow?>>

    @POST("functions/v1/moderate-spot-proposal-v1")
    suspend fun moderate(
        @Body decision: ProposalDecision,
        @Tag session: RequestSession,
    ): Response<ModerationResult>
}

internal data class ProposalDecision(
    @SerializedName("proposal_id") val proposalId: String,
    val decision: String,
    val reason: String?,
)

internal class ModerationResult(
    @SerializedName("proposal_id") val proposalId: String? = null,
    val status: String? = null,
    @SerializedName("spot_id") val spotId: String? = null,
)

internal data class NewProposal(
    val name: String,
    @SerializedName("sport_type") val sportType: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
)

internal class ProposalRow(
    val id: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    val name: String? = null,
    @SerializedName("sport_type") val sportType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val surface: String? = null,
    val lighting: Boolean? = null,
    val equipment: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerializedName("reject_reason") val rejectReason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)
