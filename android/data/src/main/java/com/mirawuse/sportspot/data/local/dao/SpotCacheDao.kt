package com.mirawuse.sportspot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.mirawuse.sportspot.data.local.entity.CachedSavedSpotEntity
import com.mirawuse.sportspot.data.local.entity.CachedSearchSpotEntity
import com.mirawuse.sportspot.data.local.entity.SavedSpotSnapshotEntity
import com.mirawuse.sportspot.data.local.entity.SearchSnapshotEntity

@Dao
interface SpotCacheDao {
    @Query("SELECT * FROM saved_spot_snapshot WHERE id = 0")
    suspend fun savedSnapshot(): SavedSpotSnapshotEntity?

    @Query("SELECT * FROM saved_spots WHERE list_position IS NOT NULL ORDER BY list_position")
    suspend fun savedRows(): List<CachedSavedSpotEntity>

    @Query("SELECT * FROM saved_spots WHERE canonical_id = :canonicalId")
    suspend fun savedSpot(canonicalId: String): CachedSavedSpotEntity?

    @Query("DELETE FROM saved_spots")
    suspend fun clearSavedSpots()

    @Query("DELETE FROM saved_spots WHERE canonical_id = :canonicalId")
    suspend fun deleteSavedSpot(canonicalId: String)

    @Insert
    suspend fun insertSavedSpots(rows: List<CachedSavedSpotEntity>)

    @Upsert
    suspend fun upsertSavedSpot(row: CachedSavedSpotEntity)

    @Upsert
    suspend fun upsertSavedSnapshot(snapshot: SavedSpotSnapshotEntity)

    @Query("SELECT * FROM search_snapshots WHERE query_key = :queryKey")
    suspend fun searchSnapshot(queryKey: String): SearchSnapshotEntity?

    @Query("SELECT * FROM search_spots WHERE query_key = :queryKey ORDER BY position")
    suspend fun searchRows(queryKey: String): List<CachedSearchSpotEntity>

    @Query("DELETE FROM search_snapshots WHERE query_key = :queryKey")
    suspend fun deleteSearchSnapshot(queryKey: String)

    @Insert
    suspend fun insertSearchSnapshot(snapshot: SearchSnapshotEntity)

    @Insert
    suspend fun insertSearchSpots(rows: List<CachedSearchSpotEntity>)
}
