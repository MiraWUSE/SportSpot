package com.mirawuse.sportspot.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import com.mirawuse.sportspot.data.local.db.SportSpotDatabase
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomSpotCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context
        get() = RuntimeEnvironment.getApplication().withDatabaseDirectory(folder.root)
    private val databaseName = "cache.db"
    private lateinit var database: SportSpotDatabase
    private lateinit var cache: RoomSpotCache

    private val node =
        Spot(
            id = SpotId("osm", "node:12345"),
            name = "Node",
            sportType = SportType.OTHER,
            location = GeoPoint(56.3269, 44.0059),
        )
    private val way =
        Spot(
            id = SpotId("osm", "way:456"),
            name = "Way",
            sportType = SportType.FOOTBALL,
            location = GeoPoint(56.32, 44.01),
            surface = "grass",
            lighting = false,
        )
    private val relation =
        Spot(
            id = SpotId("osm", "relation:789"),
            name = "Relation",
            sportType = SportType.WORKOUT,
            location = GeoPoint(-10.123456, 0.000001),
            surface = "asphalt",
            lighting = true,
        )
    private val community =
        Spot(
            id = SpotId("community", "33333333-3333-4333-8333-333333333333"),
            name = "Community",
            sportType = SportType.TENNIS,
            location = GeoPoint(56.33, 44.02),
        )

    @Before
    fun openDatabase() {
        database = Room.databaseBuilder(context, SportSpotDatabase::class.java, databaseName).build()
        cache = RoomSpotCache(database)
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun `saved snapshot preserves all fields and order across reopening and can be replaced by empty success`() =
        runBlocking {
            val saved = listOf(way, community, node, relation)
            assertNull(cache.readSaved())
            assertEquals(saved, cache.replaceSaved(saved))

            reopenDatabase()
            assertEquals(saved, cache.readSaved())
            assertEquals(relation, cache.readSavedSpot("osm:relation:789"))

            assertEquals(listOf(community), cache.replaceSaved(listOf(community)))
            assertEquals(listOf(community), cache.readSaved())
            assertNull(cache.readSavedSpot("osm:relation:789"))

            assertEquals(emptyList<Spot>(), cache.replaceSaved(emptyList()))
            reopenDatabase()
            assertEquals(emptyList<Spot>(), cache.readSaved())
            assertNull(cache.readSavedSpot("community:33333333-3333-4333-8333-333333333333"))
        }

    @Test
    fun `search snapshots preserve order and replacement affects only their own key`() =
        runBlocking {
            val first = "56.3269:44.0059:1000:all"
            val second = "56.3269:44.0059:2000:all"
            val missing = "56.3269:44.0059:3000:all"
            val firstSpots = listOf(relation, way, node)
            val secondSpots = listOf(community, node.copy(name = "Node in second response"))

            assertNull(cache.readSearch(first))
            assertEquals(firstSpots, cache.replaceSearch(first, firstSpots))
            assertEquals(secondSpots, cache.replaceSearch(second, secondSpots))

            reopenDatabase()
            assertEquals(firstSpots, cache.readSearch(first))
            assertEquals(secondSpots, cache.readSearch(second))
            assertNull(cache.readSaved())
            assertNull(cache.readSavedSpot("osm:node:12345"))

            assertEquals(listOf(way), cache.replaceSearch(first, listOf(way)))
            assertEquals(listOf(way), cache.readSearch(first))
            assertEquals(secondSpots, cache.readSearch(second))

            assertEquals(emptyList<Spot>(), cache.replaceSearch(first, emptyList()))
            reopenDatabase()
            assertEquals(emptyList<Spot>(), cache.readSearch(first))
            assertEquals(secondSpots, cache.readSearch(second))
            assertNull(cache.readSearch(missing))
        }

    @Test
    fun `individual detail writes do not create a full saved snapshot or add rows to its list`() =
        runBlocking {
            val nodeId = "osm:node:12345"
            val wayId = "osm:way:456"
            assertEquals(node, cache.replaceSavedSpot(nodeId, node))
            assertEquals(node, cache.readSavedSpot(nodeId))
            assertNull(cache.readSaved())

            assertEquals(listOf(relation), cache.replaceSaved(listOf(relation)))
            assertNull(cache.readSavedSpot(nodeId))
            assertEquals(way, cache.replaceSavedSpot(wayId, way))
            assertEquals(way, cache.readSavedSpot(wayId))
            assertEquals(listOf(relation), cache.readSaved())

            assertNull(cache.replaceSavedSpot(wayId, null))
            assertNull(cache.readSavedSpot(wayId))
            assertEquals(listOf(relation), cache.readSaved())
        }

    @Test
    fun `failed saved replacement rolls back deletions and partial inserts`() =
        runBlocking {
            val saved = listOf(way, node)
            cache.replaceSaved(saved)

            val failure = runCatching { cache.replaceSaved(listOf(relation, relation)) }.exceptionOrNull()

            assertTrue("Expected duplicate key failure, got $failure", failure is SQLiteConstraintException)
            reopenDatabase()
            assertEquals(saved, cache.readSaved())
            assertNull(cache.readSavedSpot("osm:relation:789"))
        }

    @Test
    fun `cancellation rolls back cache writes in a transaction`() =
        runBlocking {
            val key = "56.3269:44.0059:1000:all"
            val saved = listOf(way, node)
            val search = listOf(relation)
            cache.replaceSaved(saved)
            cache.replaceSearch(key, search)
            val writesCompleted = CompletableDeferred<Unit>()

            val write =
                launch {
                    database.withTransaction {
                        cache.replaceSaved(listOf(community))
                        cache.replaceSearch(key, emptyList())
                        writesCompleted.complete(Unit)
                        awaitCancellation()
                    }
                }
            withTimeout(10_000) { writesCompleted.await() }
            write.cancelAndJoin()

            assertTrue(write.isCancelled)
            reopenDatabase()
            assertEquals(saved, cache.readSaved())
            assertEquals(search, cache.readSearch(key))
        }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }
}
