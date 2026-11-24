package com.mirawuse.sportspot.data.remote.api

import com.google.gson.annotations.SerializedName
import com.mirawuse.sportspot.data.remote.RequestSession
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Tag

private const val REVIEWS = "rest/v1/reviews?select=id,user_id,spot_id,rating,text,title,created_at"

internal interface ReviewApi {
    @GET("$REVIEWS&order=created_at.desc,id.asc")
    suspend fun list(
        @Query("spot_id") spotId: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Tag session: RequestSession,
    ): Response<List<ReviewRow?>>

    @Headers("Prefer: return=representation,missing=default")
    @POST(REVIEWS)
    suspend fun create(
        @Body body: RequestBody,
        @Tag session: RequestSession,
    ): Response<List<ReviewRow?>>

    @Headers("Prefer: return=representation")
    @PATCH(REVIEWS)
    suspend fun update(
        @Query("id") id: String,
        @Query("user_id") owner: String,
        @Body body: RequestBody,
        @Tag session: RequestSession,
    ): Response<List<ReviewRow?>>

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/reviews?select=id")
    suspend fun delete(
        @Query("id") id: String,
        @Query("user_id") owner: String,
        @Tag session: RequestSession,
    ): Response<List<ReviewRow?>>
}

internal class ReviewRow(
    val id: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("spot_id") val spotId: String? = null,
    val rating: Int? = null,
    val text: String? = null,
    val title: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)
