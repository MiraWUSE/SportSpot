package com.mirawuse.sportspot.data.remote.api

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.mirawuse.sportspot.data.remote.RequestSession
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Tag

internal interface AuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body body: JsonObject,
        @Tag session: RequestSession,
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun token(
        @Query("grant_type") grantType: String,
        @Body body: JsonObject,
        @Tag session: RequestSession,
    ): Response<AuthResponse>

    @GET("rest/v1/profiles?select=id,name,role")
    suspend fun profile(
        @Query("id") id: String,
        @Tag session: RequestSession,
    ): Response<List<ProfileRow>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles?select=id,name")
    suspend fun updateProfileName(
        @Query("id") id: String,
        @Body body: JsonObject,
        @Tag session: RequestSession,
    ): Response<List<ProfileRow?>>

    @POST("auth/v1/logout?scope=local")
    suspend fun signOut(
        @Tag session: RequestSession,
    ): Response<Unit>
}

internal class AuthResponse(
    @SerializedName("id") val id: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("user") val user: AuthUser? = null,
)

internal class AuthUser(
    val id: String? = null,
    val email: String? = null,
)

internal class ProfileRow(
    val id: String? = null,
    val name: String? = null,
    val role: String? = null,
)
