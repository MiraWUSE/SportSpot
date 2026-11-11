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
    private val initialFetch = 1_000L
    private val updatedFetch = 2_000L
    private val emptyFetch = 3_000L

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
            assertEquals(saved, cache.replaceSaved(saved, initialFetch))

            reopenDatabase()
            val savedSnapshot = cache.readSaved()
            assertEquals(saved, savedSnapshot?.value)
            assertEquals(initialFetch, savedSnapshot?.fetchedAtEpochMillis)
            val relationSnapshot = cache.readSavedSpot("osm:relation:789")
            assertEquals(relation, relationSnapshot?.value)
            assertEquals(initialFetch, relationSnapshot?.fetchedAtEpochMillis)

            assertEquals(listOf(community), cache.replaceSaved(listOf(community), updatedFetch))
            assertEquals(listOf(community), cache.readSaved()?.value)
            val removedRelation = cache.readSavedSpot("osm:relation:789")
            assertNull(removedRelation?.value)
            assertEquals(updatedFetch, removedRelation?.fetchedAtEpochMillis)

            assertEquals(emptyList<Spot>(), cache.replaceSaved(emptyList(), emptyFetch))
            reopenDatabase()
            val emptySnapshot = cache.readSaved()
            assertEquals(emptyList<Spot>(), emptySnapshot?.value)
            assertEquals(emptyFetch, emptySnapshot?.fetchedAtEpochMillis)
            val removedCommunity = cache.readSavedSpot("community:33333333-3333-4333-8333-333333333333")
            assertNull(removedCommunity?.value)
            assertEquals(emptyFetch, removedCommunity?.fetchedAtEpochMillis)
        }

    @Test
    fun `search snapshots preserve order and replacement affects only their own key`() =
        runBlocking {
            val first = "56.3269:44.0059:1000:all"
            val second = "56.3269:44.0059:2000:all"
            val missing = "56.3269:44.0059:3000:all"
            val firstSpots = listOf(relation, way, node)
            val secondSpots = listOf(community, node.copy(name = "Node in second response"))
            val secondFetch = 1_500L

            assertNull(cache.readSearch(first))
            assertEquals(firstSpots, cache.replaceSearch(first, firstSpots, initialFetch))
            assertEquals(secondSpots, cache.replaceSearch(second, secondSpots, secondFetch))

            reopenDatabase()
            val firstSnapshot = cache.readSearch(first)
            assertEquals(firstSpots, firstSnapshot?.value)
            assertEquals(initialFetch, firstSnapshot?.fetchedAtEpochMillis)
            assertEquals(secondSpots, cache.readSearch(second)?.value)
            assertNull(cache.readSaved())
            assertNull(cache.readSavedSpot("osm:node:12345"))

            assertEquals(listOf(way), cache.replaceSearch(first, listOf(way), updatedFetch))
            assertEquals(listOf(way), cache.readSearch(first)?.value)
            assertEquals(secondSpots, cache.readSearch(second)?.value)

            assertEquals(emptyList<Spot>(), cache.replaceSearch(first, emptyList(), emptyFetch))
            reopenDatabase()
            val emptySnapshot = cache.readSearch(first)
            assertEquals(emptyList<Spot>(), emptySnapshot?.value)
            assertEquals(emptyFetch, emptySnapshot?.fetchedAtEpochMillis)
            val secondSnapshot = cache.readSearch(second)
            assertEquals(secondSpots, secondSnapshot?.value)
            assertEquals(secondFetch, secondSnapshot?.fetchedAtEpochMillis)
            assertNull(cache.readSearch(missing))
        }

    @Test
    fun `individual detail writes do not create a full saved snapshot or add rows to its list`() =
        runBlocking {
            val nodeId = "osm:node:12345"
            val wayId = "osm:way:456"
            assertEquals(node, cache.replaceSavedSpot(nodeId, node, initialFetch))
            reopenDatabase()
            val nodeSnapshot = cache.readSavedSpot(nodeId)
            assertEquals(node, nodeSnapshot?.value)
            assertEquals(initialFetch, nodeSnapshot?.fetchedAtEpochMillis)
            assertNull(cache.readSaved())

            assertEquals(listOf(relation), cache.replaceSaved(listOf(relation), updatedFetch))
            val removedNode = cache.readSavedSpot(nodeId)
            assertNull(removedNode?.value)
            assertEquals(updatedFetch, removedNode?.fetchedAtEpochMillis)
            assertEquals(way, cache.replaceSavedSpot(wayId, way, 2_500L))
            assertEquals(way, cache.readSavedSpot(wayId)?.value)
            assertEquals(listOf(relation), cache.readSaved()?.value)

            assertNull(cache.replaceSavedSpot(wayId, null, emptyFetch))
            val removedWay = cache.readSavedSpot(wayId)
            assertNull(removedWay?.value)
            assertEquals(updatedFetch, removedWay?.fetchedAtEpochMillis)
            val savedSnapshot = cache.readSaved()
            assertEquals(listOf(relation), savedSnapshot?.value)
            assertEquals(updatedFetch, savedSnapshot?.fetchedAtEpochMillis)
        }

    @Test
    fun `failed saved replacement rolls back deletions and partial inserts`() =
        runBlocking {
            val saved = listOf(way, node)
            cache.replaceSaved(saved, initialFetch)

            val failure = runCatching { cache.replaceSaved(listOf(relation, relation), updatedFetch) }.exceptionOrNull()

            assertTrue("Expected duplicate key failure, got $failure", failure is SQLiteConstraintException)
            reopenDatabase()
            val savedSnapshot = cache.readSaved()
            assertEquals(saved, savedSnapshot?.value)
            assertEquals(initialFetch, savedSnapshot?.fetchedAtEpochMillis)
            val rolledBackRelation = cache.readSavedSpot("osm:relation:789")
            assertNull(rolledBackRelation?.value)
            assertEquals(initialFetch, rolledBackRelation?.fetchedAtEpochMillis)
        }

    @Test
    fun `cancellation rolls back cache writes in a transaction`() =
        runBlocking {
            val key = "56.3269:44.0059:1000:all"
            val saved = listOf(way, node)
            val search = listOf(relation)
            cache.replaceSaved(saved, initialFetch)
            cache.replaceSearch(key, search, initialFetch)
            val writesCompleted = CompletableDeferred<Unit>()

            val write =
                launch {
                    database.withTransaction {
                        cache.replaceSaved(listOf(community), updatedFetch)
                        cache.replaceSearch(key, emptyList(), updatedFetch)
                        writesCompleted.complete(Unit)
                        awaitCancellation()
                    }
                }
            withTimeout(10_000) { writesCompleted.await() }
            write.cancelAndJoin()

            assertTrue(write.isCancelled)
            reopenDatabase()
            val savedSnapshot = cache.readSaved()
            assertEquals(saved, savedSnapshot?.value)
            assertEquals(initialFetch, savedSnapshot?.fetchedAtEpochMillis)
            val searchSnapshot = cache.readSearch(key)
            assertEquals(search, searchSnapshot?.value)
            assertEquals(initialFetch, searchSnapshot?.fetchedAtEpochMillis)
        }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }
}
