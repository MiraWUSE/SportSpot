package com.mirawuse.sportspot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_spot_snapshot")
data class SavedSpotSnapshotEntity(
    @PrimaryKey
    val id: Int = 0,
)
