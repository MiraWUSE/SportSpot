package com.mirawuse.sportspot.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_spot_snapshot")
data class SavedSpotSnapshotEntity(
    @PrimaryKey
    val id: Int = 0,
    @ColumnInfo(name = "fetched_at_epoch_millis", defaultValue = "0")
    val fetchedAtEpochMillis: Long,
)
