package com.mirawuse.sportspot.data.local

import androidx.room.withTransaction
import com.mirawuse.sportspot.data.local.db.SportSpotDatabase
import com.mirawuse.sportspot.data.local.entity.CachedSavedSpotEntity
import com.mirawuse.sportspot.data.local.entity.CachedSearchSpotEntity
import com.mirawuse.sportspot.data.local.entity.SavedSpotSnapshotEntity
import com.mirawuse.sportspot.data.local.entity.SearchSnapshotEntity
import com.mirawuse.sportspot.data.local.mapper.toCacheColumns
import com.mirawuse.sportspot.data.local.mapper.toDomain
import com.mirawuse.sportspot.domain.model.spot.Spot
import javax.inject.Inject

data class CacheSnapshot<T>(
    val value: T,
    val fetchedAtEpochMillis: Long,
)

class RoomSpotCache
    @Inject
    constructor(
        private val database: SportSpotDatabase,
    ) {
        private val dao = database.spotCacheDao()

        suspend fun readSaved(): CacheSnapshot<List<Spot>>? =
            database.withTransaction {
                dao.savedSnapshot()?.let { CacheSnapshot(savedSpots(), it.fetchedAtEpochMillis) }
            }

        suspend fun readSavedSpot(canonicalId: String): CacheSnapshot<Spot?>? =
            database.withTransaction {
                val spot = dao.savedSpot(canonicalId)
                if (spot != null) {
                    CacheSnapshot(spot.spot.toDomain(), spot.fetchedAtEpochMillis)
                } else {
                    dao.savedSnapshot()?.let { CacheSnapshot(null, it.fetchedAtEpochMillis) }
                }
            }

        suspend fun replaceSaved(
            spots: List<Spot>,
            fetchedAtEpochMillis: Long,
        ): List<Spot> =
            database.withTransaction {
                dao.clearSavedSpots()
                dao.insertSavedSpots(
                    spots.mapIndexed { index, spot -> CachedSavedSpotEntity(spot.toCacheColumns(), index, fetchedAtEpochMillis) },
                )
                dao.upsertSavedSnapshot(SavedSpotSnapshotEntity(fetchedAtEpochMillis = fetchedAtEpochMillis))
                savedSpots()
            }

        suspend fun replaceSavedSpot(
            canonicalId: String,
            spot: Spot?,
            fetchedAtEpochMillis: Long,
        ): Spot? =
            database.withTransaction {
                if (spot == null) {
                    dao.deleteSavedSpot(canonicalId)
                } else {
                    val columns = spot.toCacheColumns()
                    require(columns.canonicalId == canonicalId) { "Cached spot must match requested identifier" }
                    val position = dao.savedSpot(canonicalId)?.listPosition
                    dao.upsertSavedSpot(CachedSavedSpotEntity(columns, position, fetchedAtEpochMillis))
                }
                dao.savedSpot(canonicalId)?.spot?.toDomain()
            }

        suspend fun readSearch(queryKey: String): CacheSnapshot<List<Spot>>? =
            database.withTransaction {
                dao.searchSnapshot(queryKey)?.let { CacheSnapshot(searchSpots(queryKey), it.fetchedAtEpochMillis) }
            }

        suspend fun replaceSearch(
            queryKey: String,
            spots: List<Spot>,
            fetchedAtEpochMillis: Long,
        ): List<Spot> =
            database.withTransaction {
                dao.deleteSearchSnapshot(queryKey)
                dao.insertSearchSnapshot(SearchSnapshotEntity(queryKey, fetchedAtEpochMillis))
                dao.insertSearchSpots(
                    spots.mapIndexed { position, spot -> CachedSearchSpotEntity(queryKey, position, spot.toCacheColumns()) },
                )
                searchSpots(queryKey)
            }

        private suspend fun savedSpots(): List<Spot> = dao.savedRows().map { it.spot.toDomain() }

        private suspend fun searchSpots(queryKey: String): List<Spot> = dao.searchRows(queryKey).map { it.spot.toDomain() }
    }
