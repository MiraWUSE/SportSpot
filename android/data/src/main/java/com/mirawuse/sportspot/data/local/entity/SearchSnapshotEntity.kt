package com.mirawuse.sportspot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_snapshots")
data class SearchSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "query_key")
    val queryKey: String,
    @ColumnInfo(name = "fetched_at_epoch_millis", defaultValue = "0")
    val fetchedAtEpochMillis: Long,
)
