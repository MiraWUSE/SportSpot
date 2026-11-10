package com.mirawuse.sportspot.data.remote.api

import com.mirawuse.sportspot.data.remote.dto.SpotDto
import com.mirawuse.sportspot.data.remote.dto.SpotSearchResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SpotApi {
    @GET("functions/v1/search-sports-spots-v1")
    suspend fun searchSpots(
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("radius") radius: Int,
        @Query("sport") sport: String,
    ): Response<SpotSearchResponseDto>

    @GET("rest/v1/spots")
    suspend fun getSpots(
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("order") order: String = "id.asc",
        @Query("id") id: String? = null,
    ): Response<List<SpotDto?>>
}
