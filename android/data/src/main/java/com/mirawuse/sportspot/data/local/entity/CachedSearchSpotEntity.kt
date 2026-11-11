package com.mirawuse.sportspot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "search_spots",
    primaryKeys = ["query_key", "position"],
    foreignKeys = [
        ForeignKey(
            entity = SearchSnapshotEntity::class,
            parentColumns = ["query_key"],
            childColumns = ["query_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CachedSearchSpotEntity(
    @ColumnInfo(name = "query_key")
    val queryKey: String,
    val position: Int,
    @Embedded
    val spot: SpotColumns,
)
