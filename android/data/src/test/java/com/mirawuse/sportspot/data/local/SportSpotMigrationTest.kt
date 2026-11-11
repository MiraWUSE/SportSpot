package com.mirawuse.sportspot.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mirawuse.sportspot.data.di.LocalStorageModule
import com.mirawuse.sportspot.data.local.db.SportSpotDatabase
import com.mirawuse.sportspot.data.local.entity.CachedSavedSpotEntity
import com.mirawuse.sportspot.data.local.entity.CachedSearchSpotEntity
import com.mirawuse.sportspot.data.local.entity.SavedSpotSnapshotEntity
import com.mirawuse.sportspot.data.local.entity.SearchSnapshotEntity
import com.mirawuse.sportspot.data.local.entity.SpotColumns
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SportSpotMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val node =
        SpotColumns(
            canonicalId = "osm:node:12345",
            name = "Basketball court",
            sportType = "BASKETBALL",
            latitude = 56.3269,
            longitude = 44.0059,
            surface = "asphalt",
            lighting = false,
        )
    private val community =
        SpotColumns(
            canonicalId = "community:33333333-3333-4333-8333-333333333333",
            name = "Workout area",
            sportType = "WORKOUT",
            latitude = 56.32,
            longitude = 44.01,
            surface = null,
            lighting = null,
        )
    private val way =
        node.copy(
            canonicalId = "osm:way:456",
            name = "Tennis court",
            sportType = "TENNIS",
            surface = null,
            lighting = true,
        )

    @Test
    fun `migration from exported version one preserves cache data and accepts new timestamps`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication().withDatabaseDirectory(folder.root)
            createVersionOneDatabase(context)

            val database = LocalStorageModule.provideDatabase(context)
            try {
                val dao = database.spotCacheDao()
                val savedNode = CachedSavedSpotEntity(node, listPosition = 0, fetchedAtEpochMillis = 0L)
                val savedCommunity = CachedSavedSpotEntity(community, listPosition = 1, fetchedAtEpochMillis = 0L)
                val detailOnly = CachedSavedSpotEntity(way, listPosition = null, fetchedAtEpochMillis = 0L)
                val searchResults =
                    listOf(
                        CachedSearchSpotEntity(POPULATED_QUERY, 0, way),
                        CachedSearchSpotEntity(POPULATED_QUERY, 1, node),
                    )

                assertEquals(listOf(savedNode, savedCommunity), dao.savedRows())
                assertEquals(detailOnly, dao.savedSpot(way.canonicalId))
                assertEquals(SavedSpotSnapshotEntity(fetchedAtEpochMillis = 0L), dao.savedSnapshot())
                assertEquals(SearchSnapshotEntity(POPULATED_QUERY, 0L), dao.searchSnapshot(POPULATED_QUERY))
                assertEquals(searchResults, dao.searchRows(POPULATED_QUERY))
                assertEquals(SearchSnapshotEntity(EMPTY_QUERY, 0L), dao.searchSnapshot(EMPTY_QUERY))
                assertTrue(dao.searchRows(EMPTY_QUERY).isEmpty())
                assertEquals(2, database.openHelper.readableDatabase.version)

                val cache = RoomSpotCache(database)
                val domainNode = node.expectedSpot(SpotId("osm", "node:12345"), SportType.BASKETBALL)
                val domainCommunity =
                    community.expectedSpot(SpotId("community", "33333333-3333-4333-8333-333333333333"), SportType.WORKOUT)
                val domainWay = way.expectedSpot(SpotId("osm", "way:456"), SportType.TENNIS)
                assertEquals(CacheSnapshot(listOf(domainNode, domainCommunity), 0L), cache.readSaved())
                assertEquals(CacheSnapshot(domainWay, 0L), cache.readSavedSpot(way.canonicalId))
                assertEquals(CacheSnapshot(listOf(domainWay, domainNode), 0L), cache.readSearch(POPULATED_QUERY))
                assertEquals(CacheSnapshot(emptyList<Spot>(), 0L), cache.readSearch(EMPTY_QUERY))

                val now = 1_797_000_000_000L
                val refreshedNode = savedNode.copy(fetchedAtEpochMillis = now)
                val refreshedSavedSnapshot = SavedSpotSnapshotEntity(fetchedAtEpochMillis = now)
                val newSearchSnapshot = SearchSnapshotEntity(NEW_QUERY, now)
                val newSearchResult = CachedSearchSpotEntity(NEW_QUERY, 0, node)
                dao.upsertSavedSpot(refreshedNode)
                dao.upsertSavedSnapshot(refreshedSavedSnapshot)
                dao.insertSearchSnapshot(newSearchSnapshot)
                dao.insertSearchSpots(listOf(newSearchResult))

                assertEquals(listOf(refreshedNode, savedCommunity), dao.savedRows())
                assertEquals(refreshedSavedSnapshot, dao.savedSnapshot())
                assertEquals(newSearchSnapshot, dao.searchSnapshot(NEW_QUERY))
                assertEquals(listOf(newSearchResult), dao.searchRows(NEW_QUERY))
                assertEquals(searchResults, dao.searchRows(POPULATED_QUERY))
            } finally {
                database.close()
            }
        }

    private fun createVersionOneDatabase(context: Context) {
        val schema = loadVersionOneSchema()
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onConfigure(db: SupportSQLiteDatabase) {
                            db.setForeignKeyConstraintsEnabled(true)
                        }

                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createExportedSchema(db, schema)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            error("The fixture must be created at schema version 1")
                        }
                    },
                ).build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1, db.version)
            insertSavedSpot(db, community, position = 1)
            insertSavedSpot(db, node, position = 0)
            insertSavedSpot(db, way, position = null)
            db.execSQL("INSERT INTO saved_spot_snapshot (id) VALUES (?)", arrayOf<Any?>(0))
            listOf(POPULATED_QUERY, EMPTY_QUERY).forEach { query ->
                db.execSQL("INSERT INTO search_snapshots (query_key) VALUES (?)", arrayOf<Any?>(query))
            }
            insertSearchSpot(db, POPULATED_QUERY, position = 1, spot = node)
            insertSearchSpot(db, POPULATED_QUERY, position = 0, spot = way)
        }
    }

    private fun loadVersionOneSchema(): JsonObject {
        val directory = requireNotNull(System.getProperty("sportspot.room.schemas"))
        val schemaFile = File(directory, "${SportSpotDatabase::class.java.name}/1.json")
        val schema =
            schemaFile
                .bufferedReader()
                .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        assertEquals(1, schema.get("version").asInt)
        return schema
    }

    private fun createExportedSchema(
        db: SupportSQLiteDatabase,
        schema: JsonObject,
    ) {
        schema.getAsJsonArray("entities").forEach { item ->
            val entity = item.asJsonObject
            val table = entity.get("tableName").asString
            db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
            entity.getAsJsonArray("indices")?.forEach { index ->
                val sql = index.asJsonObject.get("createSql").asString
                db.execSQL(sql.replace("\${TABLE_NAME}", table))
            }
        }
        schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
    }

    private fun insertSavedSpot(
        db: SupportSQLiteDatabase,
        spot: SpotColumns,
        position: Int?,
    ) {
        db.execSQL(
            "INSERT INTO saved_spots ($SPOT_COLUMNS, list_position) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            spot.bindValues() + position,
        )
    }

    private fun insertSearchSpot(
        db: SupportSQLiteDatabase,
        query: String,
        position: Int,
        spot: SpotColumns,
    ) {
        db.execSQL(
            "INSERT INTO search_spots ($SPOT_COLUMNS, query_key, position) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            spot.bindValues() + query + position,
        )
    }

    private fun SpotColumns.bindValues(): Array<Any?> =
        arrayOf(canonicalId, name, sportType, latitude, longitude, surface, lighting?.let { if (it) 1 else 0 })

    private fun SpotColumns.expectedSpot(
        id: SpotId,
        sport: SportType,
    ): Spot = Spot(id, name, sport, GeoPoint(latitude, longitude), surface, lighting)

    private companion object {
        const val DATABASE_NAME = "sportspot.db"
        const val SPOT_COLUMNS = "canonical_id, name, sport_type, latitude, longitude, surface, lighting"
        const val POPULATED_QUERY = "populated"
        const val EMPTY_QUERY = "empty"
        const val NEW_QUERY = "new"
    }
}
