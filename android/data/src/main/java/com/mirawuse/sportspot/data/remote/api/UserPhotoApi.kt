package com.mirawuse.sportspot.data.remote.api

import com.google.gson.annotations.SerializedName
import com.mirawuse.sportspot.data.remote.RequestSession
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Tag

private const val PHOTO_API = "functions/v1/user-photos-v1"

internal interface UserPhotoApi {
    @POST("$PHOTO_API?action=upload")
    suspend fun upload(
        @Query("id") id: String,
        @Query("target_type") type: String,
        @Query("target_id") target: String,
        @Body file: RequestBody,
        @Tag session: RequestSession,
    ): Response<PhotoRow>

    @GET(PHOTO_API)
    suspend fun submissions(
        @Query("action") action: String,
        @Query("target_type") type: String?,
        @Query("target_id") target: String?,
        @Tag session: RequestSession,
    ): Response<List<PhotoRow>>

    @GET("$PHOTO_API?action=gallery")
    suspend fun gallery(
        @Query("target_type") type: String,
        @Query("target_id") target: String,
        @Tag session: RequestSession,
    ): Response<List<String>>

    @Streaming
    @GET(PHOTO_API)
    suspend fun image(
        @Query("action") action: String,
        @Query("id") id: String,
        @Tag session: RequestSession,
    ): Response<ResponseBody>

    @POST(PHOTO_API)
    suspend fun mutate(
        @Query("action") action: String,
        @Query("id") id: String,
        @Body payload: Map<String, String>,
        @Tag session: RequestSession,
    ): Response<ResponseBody>
}

internal class PhotoRow(
    val id: String? = null,
    @SerializedName("owner_id") val ownerId: String? = null,
    @SerializedName("target_type") val targetType: String? = null,
    @SerializedName("target_id") val targetId: String? = null,
    val status: String? = null,
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
)
