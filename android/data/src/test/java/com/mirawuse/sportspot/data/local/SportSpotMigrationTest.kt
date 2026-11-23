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
import com.mirawuse.sportspot.domain.model.spot.SpotPhoto
import com.mirawuse.sportspot.domain.model.spot.SpotSport
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
    private val photo =
        SpotPhoto(
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Test.jpg/500px-Test.jpg",
            "https://commons.wikimedia.org/wiki/File:Test.jpg",
            "Test Author",
            "CC BY 4.0",
            "https://creativecommons.org/licenses/by/4.0/",
        )

    @Test
    fun `migration from exported version one preserves cache data and accepts new timestamps`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication().withDatabaseDirectory(folder.root)
            createOldDatabase(context, version = 1)

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
                assertEquals(4, database.openHelper.readableDatabase.version)

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

    @Test
    fun `migration from version two preserves timestamps and order and caches photo metadata`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication().withDatabaseDirectory(folder.root)
            createOldDatabase(context, version = 2)
            val database = LocalStorageModule.provideDatabase(context)
            try {
                val cache = RoomSpotCache(database)
                val first = way.expectedSpot(SpotId("osm", "way:456"), SportType.TENNIS)
                val second = node.expectedSpot(SpotId("osm", "node:12345"), SportType.BASKETBALL)
                val saved = community.expectedSpot(SpotId("community", "33333333-3333-4333-8333-333333333333"), SportType.WORKOUT)
                assertEquals(4, database.openHelper.readableDatabase.version)
                assertEquals(CacheSnapshot(listOf(second, saved), 1200L), cache.readSaved())
                assertEquals(CacheSnapshot(first, 1100L), cache.readSavedSpot(way.canonicalId))
                assertEquals(CacheSnapshot(listOf(first, second), 1300L), cache.readSearch(POPULATED_QUERY))
                assertEquals(CacheSnapshot(emptyList<Spot>(), 1300L), cache.readSearch(EMPTY_QUERY))

                cache.replaceSearch(NEW_QUERY, listOf(first.copy(photo = photo), second), 1500L)
            } finally {
                database.close()
            }
            val reopened = LocalStorageModule.provideDatabase(context)
            try {
                val cached = requireNotNull(RoomSpotCache(reopened).readSearch(NEW_QUERY))
                assertEquals(1500L, cached.fetchedAtEpochMillis)
                assertEquals(
                    "Test Author",
                    cached.value
                        .first()
                        .photo
                        ?.author,
                )
                assertEquals(
                    "https://creativecommons.org/licenses/by/4.0/",
                    cached.value
                        .first()
                        .photo
                        ?.licenseUrl,
                )
                assertEquals(null, cached.value[1].photo)
            } finally {
                reopened.close()
            }
        }

    @Test
    fun `migration from version three preserves photos and reads new metadata after reopening`() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication().withDatabaseDirectory(folder.root)
            createOldDatabase(context, version = 3)
            val nodeWithPhoto = node.expectedSpot(SpotId("osm", "node:12345"), SportType.BASKETBALL).copy(photo = photo)
            val saved = community.expectedSpot(SpotId("community", "33333333-3333-4333-8333-333333333333"), SportType.WORKOUT)
            val detail = way.expectedSpot(SpotId("osm", "way:456"), SportType.TENNIS)
            val refreshed =
                nodeWithPhoto.copy(
                    sports = listOf(SpotSport.BASKETBALL, SpotSport.TABLE_TENNIS),
                    address = "Остров, 2А",
                    equipment = "horizontal_bar;parallel_bars",
                )

            val database = LocalStorageModule.provideDatabase(context)
            try {
                val cache = RoomSpotCache(database)
                assertEquals(4, database.openHelper.readableDatabase.version)
                assertEquals(CacheSnapshot(listOf(nodeWithPhoto, saved), 1200L), cache.readSaved())
                assertEquals(CacheSnapshot(nodeWithPhoto, 1100L), cache.readSavedSpot(node.canonicalId))
                assertEquals(CacheSnapshot(detail, 1100L), cache.readSavedSpot(way.canonicalId))
                assertEquals(CacheSnapshot(listOf(detail, nodeWithPhoto), 1300L), cache.readSearch(POPULATED_QUERY))
                assertEquals(CacheSnapshot(emptyList<Spot>(), 1300L), cache.readSearch(EMPTY_QUERY))

                cache.replaceSaved(listOf(refreshed, saved), 1500L)
                cache.replaceSearch(NEW_QUERY, listOf(refreshed, detail.copy(sports = emptyList())), 1600L)
            } finally {
                database.close()
            }
            val reopened = LocalStorageModule.provideDatabase(context)
            try {
                val cache = RoomSpotCache(reopened)
                assertEquals(CacheSnapshot(listOf(refreshed, saved), 1500L), cache.readSaved())
                assertEquals(CacheSnapshot(refreshed, 1500L), cache.readSavedSpot(node.canonicalId))
                assertEquals(CacheSnapshot(listOf(refreshed, detail.copy(sports = emptyList())), 1600L), cache.readSearch(NEW_QUERY))
                assertEquals(CacheSnapshot(listOf(detail, nodeWithPhoto), 1300L), cache.readSearch(POPULATED_QUERY))
            } finally {
                reopened.close()
            }
        }

    private fun createOldDatabase(
        context: Context,
        version: Int,
    ) {
        val schema = loadSchema(version)
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
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
                            error("The fixture must be created at schema version $version")
                        }
                    },
                ).build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            assertEquals(version, db.version)
            insertSavedSpot(db, community, position = 1)
            insertSavedSpot(db, node, position = 0)
            insertSavedSpot(db, way, position = null)
            db.execSQL("INSERT INTO saved_spot_snapshot (id) VALUES (?)", arrayOf<Any?>(0))
            listOf(POPULATED_QUERY, EMPTY_QUERY).forEach { query ->
                db.execSQL("INSERT INTO search_snapshots (query_key) VALUES (?)", arrayOf<Any?>(query))
            }
            insertSearchSpot(db, POPULATED_QUERY, position = 1, spot = node)
            insertSearchSpot(db, POPULATED_QUERY, position = 0, spot = way)
            if (version >= 2) {
                db.execSQL("UPDATE saved_spots SET fetched_at_epoch_millis = 1100")
                db.execSQL("UPDATE saved_spot_snapshot SET fetched_at_epoch_millis = 1200")
                db.execSQL("UPDATE search_snapshots SET fetched_at_epoch_millis = 1300")
            }
            if (version == 3) {
                for (table in listOf("saved_spots", "search_spots")) {
                    db.execSQL(
                        "UPDATE $table SET photo_url=?, photo_source_url=?, photo_author=?, photo_license=?, photo_license_url=? WHERE canonical_id=?",
                        arrayOf(photo.url, photo.sourceUrl, photo.author, photo.license, photo.licenseUrl, node.canonicalId),
                    )
                }
            }
        }
    }

    private fun loadSchema(version: Int): JsonObject {
        val directory = requireNotNull(System.getProperty("sportspot.room.schemas"))
        val schemaFile = File(directory, "${SportSpotDatabase::class.java.name}/$version.json")
        val schema =
            schemaFile
                .bufferedReader()
                .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        assertEquals(version, schema.get("version").asInt)
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
