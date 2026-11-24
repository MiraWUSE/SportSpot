package com.mirawuse.sportspot.data.remote.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.mirawuse.sportspot.data.remote.RequestSession
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Tag

internal interface FavoriteApi {
    @GET("rest/v1/favorites?select=user_id,spot_id,created_at,spot_snapshot&order=created_at.desc,spot_id.asc")
    suspend fun list(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("spot_id") spotId: String?,
        @Tag session: RequestSession,
    ): Response<List<FavoriteRow?>>

    @Headers("Prefer: resolution=ignore-duplicates,return=representation,missing=default")
    @POST("rest/v1/favorites?on_conflict=user_id,spot_id&select=user_id,spot_id,created_at,spot_snapshot")
    suspend fun add(
        @Body body: JsonObject,
        @Tag session: RequestSession,
    ): Response<List<FavoriteRow?>>

    @DELETE("rest/v1/favorites")
    suspend fun remove(
        @Query("spot_id") spotId: String,
        @Tag session: RequestSession,
    ): Response<Unit>
}

internal class FavoriteRow(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("spot_id") val spotId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("spot_snapshot") val snapshot: JsonElement? = null,
)
