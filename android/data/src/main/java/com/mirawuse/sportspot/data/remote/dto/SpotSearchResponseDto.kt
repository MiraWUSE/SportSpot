package com.mirawuse.sportspot.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SpotSearchResponseDto(
    @SerializedName("spots") val spots: List<SpotDto?>? = null,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("attribution") val attribution: String? = null,
    @SerializedName("cache") val cache: String? = null,
)
