package com.mirawuse.sportspot.data.remote.api

import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.dto.SpotDto
import com.mirawuse.sportspot.data.remote.dto.SpotSearchResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Tag

internal interface SpotApi {
    @GET("functions/v1/search-sports-spots-v1")
    suspend fun searchSpots(
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("radius") radius: Int,
        @Query("sport") sport: String,
        @Tag session: RequestSession? = null,
    ): Response<SpotSearchResponseDto>

    @GET("rest/v1/spots")
    suspend fun getSpots(
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("order") order: String = "id.asc",
        @Query("id") id: String? = null,
    ): Response<List<SpotDto?>>

    @GET("rest/v1/spots?source=eq.community&order=id.asc")
    suspend fun getCommunitySpots(
        @Query("and") bounds: String,
        @Query("or") wrappedLongitudes: String?,
        @Query("sport_type") sport: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Tag session: RequestSession? = null,
    ): Response<List<SpotDto?>>
}
