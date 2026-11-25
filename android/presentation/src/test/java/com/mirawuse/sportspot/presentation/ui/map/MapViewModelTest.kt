package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.repository.ViewportSearchResult
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @Test
    fun mapKeepsItsInitialRadiusWhenHomeHasNoDefaultSnapshot() =
        runTest {
            withMap { model, repository ->
                model.activate(anchor, loadInitial = true)
                advanceUntilIdle()
                assertEquals(listOf(Call(MapSearchRequest(anchor.center, 1000), false)), repository.calls)
                model.onViewportIdle(areaB, null)
                advanceUntilIdle()
                assertEquals(areaB.request, repository.calls.last().request)
                model.deactivate(anchor)
                model.activate(anchor, loadInitial = true)
                advanceUntilIdle()
                assertEquals(2, repository.calls.size)
            }
        }

    @Test
    fun onlyTheFinalDistinctIdleLoadsAndRegionalZoomDoesNotSearch() =
        runTest {
            withMap { model, repository ->
                val jitter = areaA.copy(center = GeoPoint(56.32701, 44.00601))
                model.onMoveStarted()
                model.onViewportIdle(jitter, areaA)
                model.onMoveStarted()
                model.onViewportIdle(areaB, areaA)
                advanceTimeBy(400)
                model.onMoveStarted()
                model.onViewportIdle(areaC, areaB)
                advanceTimeBy(549)
                assertTrue(repository.calls.isEmpty())
                advanceUntilIdle()
                assertEquals(listOf(Call(areaC.request!!, false)), repository.calls)

                model.onViewportIdle(areaC, areaC)
                model.onViewportIdle(areaC.copy(request = null, limited = true), areaC)
                advanceUntilIdle()
                assertEquals(MapSearchStatus.ZOOM_IN, model.state.value.status)
                assertTrue(model.state.value.limited)
                assertEquals(1, repository.calls.size)

                val closer = areaC.copy(visibleRadius = 550.0, request = MapSearchRequest(areaC.center, 600))
                model.onViewportIdle(closer, areaC)
                advanceUntilIdle()
                assertEquals(MapSearchRequest(areaC.center, 600), repository.calls.last().request)
                assertEquals(2, repository.calls.size)
            }
        }

    @Test
    fun newGestureCancelsTheOldRequestAndKeepsMarkersUntilTheNewResult() =
        runTest {
            withMap { model, repository ->
                val first = spot("osm", areaA)
                val coincident = spot("community", areaA)
                val late = CompletableDeferred<List<Spot>>()
                val final = spot("osm", areaC)
                repository.result = { center, _ ->
                    when (center) {
                        areaA.center -> listOf(first, first, coincident)
                        areaB.center -> withContext(NonCancellable) { late.await() }
                        else -> listOf(final)
                    }
                }
                model.onViewportIdle(areaA, null)
                advanceUntilIdle()
                assertEquals(listOf(first, coincident), model.state.value.spots)
                model.onViewportIdle(areaB, areaA)
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(areaB.request, repository.calls.last().request)
                model.onMoveStarted()
                model.onViewportIdle(areaC, areaB)
                assertEquals(MapSearchStatus.WAITING, model.state.value.status)
                assertEquals(listOf(first, coincident), model.state.value.spots)
                advanceUntilIdle()
                late.complete(listOf(spot("osm", areaB)))
                advanceUntilIdle()
                assertEquals(listOf(final), model.state.value.spots)
            }
        }

    @Test
    fun tabExitCancelsDebounceAndOnlyAnInterruptedIdleResumesOnce() =
        runTest {
            withMap { model, repository ->
                model.onViewportIdle(areaB, areaA)
                advanceTimeBy(300)
                model.deactivate(anchor)
                model.onViewportIdle(areaC, areaB)
                advanceUntilIdle()
                assertTrue(repository.calls.isEmpty())
                model.activate(anchor)
                advanceUntilIdle()
                assertEquals(listOf(Call(areaB.request!!, false)), repository.calls)
                model.deactivate(anchor)
                model.activate(anchor)
                advanceUntilIdle()
                assertEquals(1, repository.calls.size)
            }
        }

    @Test
    fun errorKeepsMarkersAndExplicitRetryAndLaterPanWorkAfterEmpty() =
        runTest {
            withMap { model, repository ->
                repository.result = { center, refresh ->
                    if (center == areaB.center && !refresh) throw IOException("Test network failure")
                    if (center == areaB.center) emptyList() else listOf(spot("osm", areaA))
                }
                model.onViewportIdle(areaA, null)
                advanceUntilIdle()
                val original = model.state.value.spots
                model.onViewportIdle(areaB, areaA)
                advanceUntilIdle()
                assertEquals(MapSearchStatus.ERROR, model.state.value.status)
                assertEquals(original, model.state.value.spots)
                model.onViewportIdle(areaB, areaB)
                advanceUntilIdle()
                assertEquals(2, repository.calls.size)

                model.retry()
                advanceUntilIdle()
                assertEquals(Call(areaB.request!!, true), repository.calls.last())
                assertEquals(MapSearchStatus.READY, model.state.value.status)
                assertEquals(emptyList<Spot>(), model.state.value.spots)
                model.onViewportIdle(areaC, areaB)
                advanceUntilIdle()
                assertEquals(original, model.state.value.spots)
            }
        }

    @Test
    fun separateGesturesWithinOneMinuteOnlySearchTheLastAreaOfEachWindow() =
        runTest {
            withMap { model, repository ->
                val areas = List(8) { area(56.3 + it * 0.02) }
                repository.result = { center, _ -> listOf(Spot(SpotId("osm", "latest"), "Площадка", SportType.TENNIS, center)) }
                model.onViewportIdle(areas.first(), null)
                advanceTo(550)
                for ((index, viewport) in areas.drop(1).withIndex()) {
                    advanceTo((index + 1) * 1500L)
                    model.onMoveStarted()
                    model.onViewportIdle(viewport, areas[index])
                }
                advanceTo(20_549)
                assertEquals(listOf(areas.first().request), repository.calls.map(Call::request))
                assertEquals(
                    areas.first().center,
                    model.state.value.spots
                        ?.single()
                        ?.location,
                )
                advanceTo(20_550)
                assertEquals(listOf(areas.first().request, areas.last().request), repository.calls.map(Call::request))
                assertEquals(
                    areas.last().center,
                    model.state.value.spots
                        ?.single()
                        ?.location,
                )

                model.onViewportIdle(areaB, areas.last())
                advanceTo(40_550)
                model.onViewportIdle(areaC, areaB)
                advanceTo(60_000)
                assertEquals(listOf(areas.first().request, areas.last().request, areaB.request), repository.calls.map(Call::request))
                assertEquals(
                    areaB.center,
                    model.state.value.spots
                        ?.single()
                        ?.location,
                )
            }
        }

    @Test
    fun rateLimitHonorsAnHourAndManualRetriesCannotBypassIt() =
        runTest {
            withMap { model, repository ->
                val saved = listOf(spot("osm", areaA))
                repository.nextRateLimit = ViewportSearchResult.RateLimited(3600, saved)
                repository.result = { center, _ -> listOf(Spot(SpotId("osm", "new"), "Площадка", SportType.TENNIS, center)) }
                model.retry()
                runCurrent()
                assertEquals(listOf(Call(areaA.request!!, true)), repository.calls)
                assertEquals(MapSearchStatus.RATE_LIMITED, model.state.value.status)
                assertEquals(saved, model.state.value.spots)
                advanceTo(500)
                assertEquals(1, repository.calls.size)
                repeat(4) { model.retry() }
                model.onMoveStarted()
                model.onViewportIdle(areaB, areaA)
                advanceTo(2_000)
                model.onMoveStarted()
                model.onViewportIdle(areaC, areaB)
                repeat(4) { model.retry() }
                advanceTo(3_599_999)
                assertEquals(1, repository.calls.size)
                assertEquals(MapSearchStatus.RATE_LIMITED, model.state.value.status)
                assertEquals(saved, model.state.value.spots)

                advanceTo(3_600_000)
                assertEquals(listOf(Call(areaA.request!!, true), Call(areaC.request!!, false)), repository.calls)
                assertEquals(
                    areaC.center,
                    model.state.value.spots
                        ?.single()
                        ?.location,
                )
                advanceTo(3_640_000)
                assertEquals(2, repository.calls.size)
            }
        }

    @Test
    fun revisitingFreshRoomAreaDoesNotWaitForTheNetworkWindow() =
        runTest {
            withMap { model, repository ->
                val first = listOf(spot("osm", areaA))
                val second = listOf(spot("community", areaB))
                repository.result = { center, _ -> if (center == areaA.center) first else second }
                model.onViewportIdle(areaA, null)
                advanceTo(550)
                repository.cached[areaA.request!!] = first
                model.onViewportIdle(areaB, areaA)
                advanceTo(20_550)
                assertEquals(second, model.state.value.spots)

                model.onViewportIdle(areaA, areaB)
                advanceTo(21_100)
                assertEquals(listOf(areaA.request, areaB.request), repository.calls.map(Call::request))
                assertEquals(first, model.state.value.spots)
                assertEquals(MapSearchStatus.READY, model.state.value.status)
            }
        }

    @Test
    fun leavingDuringAnotherGesturePreservesTheLatestSettledArea() =
        runTest {
            withMap { model, repository ->
                model.onViewportIdle(areaA, null)
                advanceTo(550)
                model.onViewportIdle(areaB, areaA)
                advanceTo(2_000)
                model.onViewportIdle(areaC, areaB)
                advanceTo(3_000)
                assertEquals(MapSearchStatus.WAITING, model.state.value.status)
                model.onMoveStarted()
                model.deactivate(anchor)
                model.onViewportIdle(area(56.5), areaC)
                advanceTo(25_000)
                assertEquals(listOf(areaA.request), repository.calls.map(Call::request))
                model.activate(anchor)
                advanceTo(25_550)
                model.deactivate(anchor)
                model.activate(anchor)
                advanceTo(60_000)
                assertEquals(listOf(areaA.request, areaC.request), repository.calls.map(Call::request))
            }
        }

    private fun TestScope.advanceTo(timeMillis: Long) {
        advanceTimeBy(timeMillis - testScheduler.currentTime)
        runCurrent()
    }

    private suspend fun TestScope.withMap(block: suspend (MapViewModel, TestRepository) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = TestRepository()
        val model = MapViewModel(SearchNearbySpotsUseCase(repository)) { testScheduler.currentTime }
        try {
            model.activate(anchor)
            block(model, repository)
        } finally {
            model.deactivate(anchor)
            Dispatchers.resetMain()
        }
    }

    private data class Call(
        val request: MapSearchRequest,
        val refresh: Boolean,
    )

    private class TestRepository : SpotRepository {
        val calls = mutableListOf<Call>()
        val cached = mutableMapOf<MapSearchRequest, List<Spot>>()
        var nextRateLimit: ViewportSearchResult.RateLimited? = null
        var result: suspend (GeoPoint, Boolean) -> List<Spot> = { _, _ -> emptyList() }

        override suspend fun getSpots(): List<Spot> = error("Unexpected full catalog")

        override suspend fun getSpotById(id: SpotId): Spot? = error("Unexpected spot lookup")

        override suspend fun searchViewport(
            center: GeoPoint,
            radiusMeters: Int,
            refresh: Boolean,
            permitNetwork: () -> Boolean,
        ): ViewportSearchResult {
            val request = MapSearchRequest(center, radiusMeters)
            if (!refresh) cached[request]?.let { return ViewportSearchResult.Available(it) }
            val limit = nextRateLimit ?: return super.searchViewport(center, radiusMeters, refresh, permitNetwork)
            if (!permitNetwork()) return ViewportSearchResult.Deferred
            nextRateLimit = null
            calls += Call(request, refresh)
            return limit
        }

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            calls += Call(MapSearchRequest(center, radiusMeters), false)
            return result(center, false)
        }

        override suspend fun refreshNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            calls += Call(MapSearchRequest(center, radiusMeters), true)
            return result(center, true)
        }
    }

    private companion object {
        val areaA = area(56.327)
        val areaB = area(56.36)
        val areaC = area(56.39)
        val anchor = MapAnchor(areaA.center, null)

        fun area(latitude: Double): MapViewport {
            val center = GeoPoint(latitude, 44.006)
            return MapViewport(center, 950.0, MapSearchRequest(center, 1000), limited = false)
        }

        fun spot(
            source: String,
            area: MapViewport,
        ): Spot = Spot(SpotId(source, "test"), "Площадка", SportType.TENNIS, area.center)
    }
}
