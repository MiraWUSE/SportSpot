package com.mirawuse.sportspot.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mirawuse.sportspot.data.local.dao.SpotCacheDao
import com.mirawuse.sportspot.data.local.entity.CachedSavedSpotEntity
import com.mirawuse.sportspot.data.local.entity.CachedSearchSpotEntity
import com.mirawuse.sportspot.data.local.entity.SavedSpotSnapshotEntity
import com.mirawuse.sportspot.data.local.entity.SearchSnapshotEntity

@Database(
    entities = [
        CachedSavedSpotEntity::class,
        SavedSpotSnapshotEntity::class,
        SearchSnapshotEntity::class,
        CachedSearchSpotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SportSpotDatabase : RoomDatabase() {
    abstract fun spotCacheDao(): SpotCacheDao
}
