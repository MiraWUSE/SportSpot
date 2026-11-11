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
)
