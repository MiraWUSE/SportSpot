package com.mirawuse.sportspot.presentation.ui.map

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
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
                advanceTimeBy(550)
                runCurrent()
                assertEquals(areaB.request, repository.calls.last().request)
                model.onMoveStarted()
                model.onViewportIdle(areaC, areaB)
                assertEquals(MapSearchStatus.LOADING, model.state.value.status)
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

                assertTrue(model.retry())
                advanceUntilIdle()
                assertEquals(Call(areaB.request!!, true), repository.calls.last())
                assertEquals(MapSearchStatus.READY, model.state.value.status)
                assertEquals(emptyList<Spot>(), model.state.value.spots)
                model.onViewportIdle(areaC, areaB)
                advanceUntilIdle()
                assertEquals(original, model.state.value.spots)
            }
        }

    private suspend fun TestScope.withMap(block: suspend (MapViewModel, TestRepository) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = TestRepository()
        val model = MapViewModel(SearchNearbySpotsUseCase(repository))
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
        var result: suspend (GeoPoint, Boolean) -> List<Spot> = { _, _ -> emptyList() }

        override suspend fun getSpots(): List<Spot> = error("Unexpected full catalog")

        override suspend fun getSpotById(id: SpotId): Spot? = error("Unexpected spot lookup")

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
