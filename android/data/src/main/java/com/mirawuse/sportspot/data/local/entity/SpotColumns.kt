package com.mirawuse.sportspot.data.local.entity

import androidx.room.ColumnInfo

data class SpotColumns(
    @ColumnInfo(name = "canonical_id")
    val canonicalId: String,
    val name: String,
    @ColumnInfo(name = "sport_type")
    val sportType: String,
    val latitude: Double,
    val longitude: Double,
    val surface: String?,
    val lighting: Boolean?,
    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,
    @ColumnInfo(name = "photo_source_url")
    val photoSourceUrl: String? = null,
    @ColumnInfo(name = "photo_author")
    val photoAuthor: String? = null,
    @ColumnInfo(name = "photo_license")
    val photoLicense: String? = null,
    @ColumnInfo(name = "photo_license_url")
    val photoLicenseUrl: String? = null,
    val address: String? = null,
    val equipment: String? = null,
    val sports: String? = null,
)
