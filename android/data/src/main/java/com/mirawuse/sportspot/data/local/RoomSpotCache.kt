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

class RoomSpotCache
    @Inject
    constructor(
        private val database: SportSpotDatabase,
    ) {
        private val dao = database.spotCacheDao()

        suspend fun readSaved(): List<Spot>? =
            database.withTransaction {
                if (dao.savedSnapshot() == null) null else savedSpots()
            }

        suspend fun readSavedSpot(canonicalId: String): Spot? = dao.savedSpot(canonicalId)?.spot?.toDomain()

        suspend fun replaceSaved(spots: List<Spot>): List<Spot> =
            database.withTransaction {
                dao.clearSavedSpots()
                dao.insertSavedSpots(
                    spots.mapIndexed { index, spot -> CachedSavedSpotEntity(spot.toCacheColumns(), index) },
                )
                dao.upsertSavedSnapshot(SavedSpotSnapshotEntity())
                savedSpots()
            }

        suspend fun replaceSavedSpot(
            canonicalId: String,
            spot: Spot?,
        ): Spot? =
            database.withTransaction {
                if (spot == null) {
                    dao.deleteSavedSpot(canonicalId)
                } else {
                    val columns = spot.toCacheColumns()
                    require(columns.canonicalId == canonicalId) { "Cached spot must match requested identifier" }
                    val position = dao.savedSpot(canonicalId)?.listPosition
                    dao.upsertSavedSpot(CachedSavedSpotEntity(columns, position))
                }
                readSavedSpot(canonicalId)
            }

        suspend fun readSearch(queryKey: String): List<Spot>? =
            database.withTransaction {
                if (dao.searchSnapshot(queryKey) == null) null else searchSpots(queryKey)
            }

        suspend fun replaceSearch(
            queryKey: String,
            spots: List<Spot>,
        ): List<Spot> =
            database.withTransaction {
                dao.deleteSearchSnapshot(queryKey)
                dao.insertSearchSnapshot(SearchSnapshotEntity(queryKey))
                dao.insertSearchSpots(
                    spots.mapIndexed { position, spot -> CachedSearchSpotEntity(queryKey, position, spot.toCacheColumns()) },
                )
                searchSpots(queryKey)
            }

        private suspend fun savedSpots(): List<Spot> = dao.savedRows().map { it.spot.toDomain() }

        private suspend fun searchSpots(queryKey: String): List<Spot> = dao.searchRows(queryKey).map { it.spot.toDomain() }
    }
