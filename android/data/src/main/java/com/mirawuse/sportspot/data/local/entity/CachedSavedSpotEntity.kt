package com.mirawuse.sportspot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "saved_spots",
    primaryKeys = ["canonical_id"],
    indices = [Index(value = ["list_position"], unique = true)],
)
data class CachedSavedSpotEntity(
    @Embedded
    val spot: SpotColumns,
    @ColumnInfo(name = "list_position")
    val listPosition: Int?,
)
