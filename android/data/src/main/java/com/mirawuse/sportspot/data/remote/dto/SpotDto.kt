package com.mirawuse.sportspot.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Shared wire fields of OpenAPI Spot and SearchSpot; nullable values are validated by the mapper. */
data class SpotDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("sport_type") val sportType: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("surface") val surface: String? = null,
    @SerializedName("lighting") val lighting: Boolean? = null,
    @SerializedName("equipment") val equipment: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("osm_type") val osmType: String? = null,
    @SerializedName("osm_id") val osmId: Long? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)
