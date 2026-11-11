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

/**
 * A missing snapshot means the cache has no answer.
 * An existing snapshot can contain an empty list or confirm that a saved spot is absent.
 */
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
                val snapshot = dao.savedSnapshot() ?: return@withTransaction null
                CacheSnapshot(savedSpots(), snapshot.fetchedAtEpochMillis)
            }

        suspend fun readSavedSpot(canonicalId: String): CacheSnapshot<Spot?>? =
            database.withTransaction {
                val row = dao.savedSpot(canonicalId)
                if (row != null) {
                    return@withTransaction CacheSnapshot(row.spot.toDomain(), row.fetchedAtEpochMillis)
                }
                val snapshot = dao.savedSnapshot() ?: return@withTransaction null
                CacheSnapshot(null, snapshot.fetchedAtEpochMillis)
            }

        suspend fun replaceSaved(
            spots: List<Spot>,
            fetchedAtEpochMillis: Long,
        ): List<Spot> =
            database.withTransaction {
                dao.clearSavedSpots()
                val rows =
                    spots.mapIndexed { position, spot ->
                        CachedSavedSpotEntity(
                            spot = spot.toCacheColumns(),
                            listPosition = position,
                            fetchedAtEpochMillis = fetchedAtEpochMillis,
                        )
                    }
                dao.insertSavedSpots(rows)
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
                    val listPosition = dao.savedSpot(canonicalId)?.listPosition
                    dao.upsertSavedSpot(
                        CachedSavedSpotEntity(
                            spot = columns,
                            listPosition = listPosition,
                            fetchedAtEpochMillis = fetchedAtEpochMillis,
                        ),
                    )
                }
                dao.savedSpot(canonicalId)?.spot?.toDomain()
            }

        suspend fun readSearch(queryKey: String): CacheSnapshot<List<Spot>>? =
            database.withTransaction {
                val snapshot = dao.searchSnapshot(queryKey) ?: return@withTransaction null
                CacheSnapshot(searchSpots(queryKey), snapshot.fetchedAtEpochMillis)
            }

        suspend fun replaceSearch(
            queryKey: String,
            spots: List<Spot>,
            fetchedAtEpochMillis: Long,
        ): List<Spot> =
            database.withTransaction {
                dao.deleteSearchSnapshot(queryKey)
                dao.insertSearchSnapshot(SearchSnapshotEntity(queryKey = queryKey, fetchedAtEpochMillis = fetchedAtEpochMillis))
                val rows =
                    spots.mapIndexed { position, spot ->
                        CachedSearchSpotEntity(
                            queryKey = queryKey,
                            position = position,
                            spot = spot.toCacheColumns(),
                        )
                    }
                dao.insertSearchSpots(rows)
                searchSpots(queryKey)
            }

        private suspend fun savedSpots(): List<Spot> = dao.savedRows().map { it.spot.toDomain() }

        private suspend fun searchSpots(queryKey: String): List<Spot> = dao.searchRows(queryKey).map { it.spot.toDomain() }
    }
