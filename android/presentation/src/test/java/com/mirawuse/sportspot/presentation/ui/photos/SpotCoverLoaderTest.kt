package com.mirawuse.sportspot.presentation.ui.photos

import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.spot.SpotId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotCoverLoaderTest {
    private val target = PhotoTarget.spot(SpotId("osm", "node:1"))

    @Test
    fun emptyAndFailedGalleriesAreCachedBriefly() =
        runTest {
            var now = 0L
            var requests = 0
            val loader =
                SpotCoverLoader(
                    readGallery = {
                        requests++
                        if (requests > 1) throw PhotoException(PhotoError.OFFLINE)
                        emptyList()
                    },
                    readImage = { error("An empty or failed gallery has no image") },
                    nowMillis = { now },
                )

            repeat(2) { assertNull(loader.cover(target, SpotCoverSize.THUMBNAIL)) }
            assertEquals(1, requests)
            now = 45_000
            repeat(2) { assertNull(loader.cover(target, SpotCoverSize.THUMBNAIL)) }
            assertEquals(2, requests)
            now += 8_000
            assertNull(loader.cover(target, SpotCoverSize.THUMBNAIL))
            assertEquals(3, requests)
        }

    @Test
    fun visibleCardsShareGalleryReadsAndUseTwoSlots() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val opened = mutableListOf<PhotoTarget>()
            val loader =
                SpotCoverLoader(
                    readGallery = {
                        opened += it
                        release.await()
                        emptyList()
                    },
                    readImage = { error("No image") },
                )
            val targets = (1..4).map { PhotoTarget.spot(SpotId("osm", "node:$it")) }
            val covers = (listOf(target) + targets).map { async { loader.cover(it, SpotCoverSize.THUMBNAIL) } }
            runCurrent()
            assertEquals(targets.take(2), opened)
            release.complete(Unit)
            covers.awaitAll().forEach { assertNull(it) }
            assertEquals(targets, opened)
        }

    @Test
    fun invalidationDiscardsAnInFlightResult() =
        runTest {
            val stale = CompletableDeferred<List<String>>()
            var requests = 0
            val loader =
                SpotCoverLoader(
                    readGallery = { if (requests++ == 0) stale.await() else emptyList() },
                    readImage = { error("An invalidated photo must not be downloaded") },
                )
            val previous = async { loader.cover(target, SpotCoverSize.DETAIL) }
            runCurrent()
            loader.invalidate(target)
            stale.complete(listOf("removed"))

            assertNull(previous.await())
            assertNull(loader.cover(target, SpotCoverSize.DETAIL))
            assertEquals(2, requests)
        }

    @Test
    fun missingPublishedImageIsDiscardedAndNextCandidateIsTriedOnlyOnceOnFailure() =
        runTest {
            val attempts = mutableListOf<String>()
            val loader =
                SpotCoverLoader(
                    readGallery = { listOf("removed", "offline") },
                    readImage = { id ->
                        attempts += id
                        throw PhotoException(if (id == "removed") PhotoError.NOT_FOUND else PhotoError.OFFLINE)
                    },
                )
            repeat(2) { assertNull(loader.cover(target, SpotCoverSize.THUMBNAIL)) }
            assertEquals(listOf("removed", "offline"), attempts)
            assertEquals(listOf("offline"), loader.gallery(target))
        }
}
