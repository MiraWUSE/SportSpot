package com.mirawuse.sportspot.presentation.ui.home

import androidx.lifecycle.SavedStateHandle
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val demoCenter = GeoPoint(56.3269, 44.0059)
    private val spot = Spot(SpotId("osm", "node:1"), "Спортивная площадка", SportType.BASKETBALL, demoCenter)

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial loading searches the demo location once and shows content`() =
        runTest(dispatcher) {
            val repository =
                FakeSpotRepository { center, radius, sport ->
                    assertEquals(demoCenter, center)
                    assertEquals(1000, radius)
                    assertNull(sport)
                    listOf(spot)
                }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), FakeDeviceLocationProvider())

            assertEquals(HomeUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(HomeUiState.Content(listOf(spot)), viewModel.uiState.value)
            assertEquals(1, repository.searchCalls)
        }

    @Test
    fun `text and sport persist together and can be cleared without querying again`() =
        runTest(dispatcher) {
            val repository = FakeSpotRepository { _, _, _ -> listOf(spot) }
            val search = SearchNearbySpotsUseCase(repository)
            val saved = SavedStateHandle()
            val viewModel = HomeViewModel(search, FakeDeviceLocationProvider(), saved)
            advanceUntilIdle()

            viewModel.setSearchQuery(" Красная улица ")
            viewModel.setSearchSport(SportType.BASKETBALL)
            advanceUntilIdle()
            assertEquals(SearchFilterState(" Красная улица ", SportType.BASKETBALL), viewModel.searchFilter.value)
            assertEquals(1, repository.searchCalls)

            val restoredHandle = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            val restored = HomeViewModel(search, FakeDeviceLocationProvider(), restoredHandle)
            advanceUntilIdle()
            assertEquals(viewModel.searchFilter.value, restored.searchFilter.value)
            restored.resetSearchFilter()
            advanceUntilIdle()
            assertEquals(SearchFilterState(), restored.searchFilter.value)
            assertEquals(2, repository.searchCalls)
        }

    @Test
    fun `successful empty result shows Empty`() =
        runTest(dispatcher) {
            val repository = FakeSpotRepository { _, _, _ -> emptyList() }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), FakeDeviceLocationProvider())

            advanceUntilIdle()

            assertEquals(HomeUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `failed search shows a safe message`() =
        runTest(dispatcher) {
            val repository = FakeSpotRepository { _, _, _ -> throw IOException("Internal transport details") }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), FakeDeviceLocationProvider())

            advanceUntilIdle()

            assertEquals(
                HomeUiState.Error("Не удалось загрузить площадки. Проверьте соединение и попробуйте снова."),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `retry after failure starts one more search and can show content`() =
        runTest(dispatcher) {
            var online = false
            val repository =
                FakeSpotRepository { _, _, _ ->
                    if (!online) throw IOException("Offline")
                    listOf(spot)
                }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), FakeDeviceLocationProvider())
            advanceUntilIdle()

            online = true
            viewModel.retry()
            viewModel.retry()
            assertEquals(HomeUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(HomeUiState.Content(listOf(spot)), viewModel.uiState.value)
            assertEquals(2, repository.searchCalls)
        }

    @Test
    fun `cancelled search does not show Error`() =
        runTest(dispatcher) {
            val repository = FakeSpotRepository { _, _, _ -> throw CancellationException() }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), FakeDeviceLocationProvider())

            advanceUntilIdle()

            assertEquals(HomeUiState.Loading, viewModel.uiState.value)
            assertEquals(1, repository.searchCalls)
        }

    @Test
    fun `device point is read only on request and used by the existing search`() =
        runTest(dispatcher) {
            val devicePoint = GeoPoint(48.8566, 2.3522)
            val centers = mutableListOf<GeoPoint>()
            val device = FakeDeviceLocationProvider { devicePoint }
            val repository =
                FakeSpotRepository { center, radius, _ ->
                    centers.add(center)
                    assertEquals(1000, radius)
                    listOf(spot.copy(location = center))
                }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            assertEquals(0, device.calls)
            viewModel.useDeviceLocation(approximate = false)
            advanceUntilIdle()

            assertEquals(listOf(demoCenter, devicePoint), centers)
            assertEquals(devicePoint, viewModel.location.value.center)
            assertEquals(LocationSource.DEVICE, viewModel.location.value.source)
            assertFalse(viewModel.location.value.locating)
            assertEquals(HomeUiState.Content(listOf(spot.copy(location = devicePoint))), viewModel.uiState.value)
        }

    @Test
    fun `no fix keeps the previous device center and content`() =
        runTest(dispatcher) {
            var point: GeoPoint? = GeoPoint(48.8566, 2.3522)
            val device = FakeDeviceLocationProvider { point }
            val repository = FakeSpotRepository { _, _, _ -> listOf(spot) }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            viewModel.useDeviceLocation(approximate = true)
            advanceUntilIdle()
            val previous = viewModel.location.value
            point = null
            viewModel.useDeviceLocation(approximate = false)
            advanceUntilIdle()

            assertEquals(previous.center, viewModel.location.value.center)
            assertEquals(LocationSource.APPROXIMATE, viewModel.location.value.source)
            assertEquals(R.string.location_unavailable, viewModel.location.value.message)
            assertEquals(HomeUiState.Content(listOf(spot)), viewModel.uiState.value)
            assertEquals(2, repository.searchCalls)
            assertFalse(viewModel.location.value.locating)
        }

    @Test
    fun `permission denial keeps default search without reading hardware`() =
        runTest(dispatcher) {
            val device = FakeDeviceLocationProvider()
            val repository = FakeSpotRepository { _, _, _ -> listOf(spot) }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            viewModel.permissionDenied()

            assertEquals(0, device.calls)
            assertEquals(1, repository.searchCalls)
            assertEquals(demoCenter, viewModel.location.value.center)
            assertEquals(R.string.location_denied, viewModel.location.value.message)
            assertEquals(HomeUiState.Content(listOf(spot)), viewModel.uiState.value)
        }

    @Test
    fun `cancelling a fix clears progress and permits another attempt`() =
        runTest(dispatcher) {
            var waitForFix = true
            val point = GeoPoint(48.8566, 2.3522)
            val device = FakeDeviceLocationProvider { if (waitForFix) awaitCancellation() else point }
            val repository = FakeSpotRepository { _, _, _ -> listOf(spot) }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            val previousState = viewModel.uiState.value
            val previousLocation = viewModel.location.value
            viewModel.useDeviceLocation(approximate = false)
            runCurrent()
            viewModel.cancelLocation()
            runCurrent()

            assertFalse(viewModel.location.value.locating)
            assertEquals(previousState, viewModel.uiState.value)
            assertEquals(previousLocation, viewModel.location.value)
            assertEquals(1, repository.searchCalls)
            assertEquals(1, device.calls)

            waitForFix = false
            viewModel.useDeviceLocation(approximate = false)
            advanceUntilIdle()

            assertFalse(viewModel.location.value.locating)
            assertEquals(point, viewModel.location.value.center)
            assertEquals(2, device.calls)
            assertEquals(2, repository.searchCalls)
            assertEquals(HomeUiState.Content(listOf(spot)), viewModel.uiState.value)
        }

    @Test
    fun `late cancelled fix cannot clear a new attempt or change the search`() =
        runTest(dispatcher) {
            val oldFix = CompletableDeferred<GeoPoint>()
            val newFix = CompletableDeferred<GeoPoint>()
            val point = GeoPoint(48.8566, 2.3522)
            var attempts = 0
            val device =
                FakeDeviceLocationProvider {
                    if (attempts++ == 0) withContext(NonCancellable) { oldFix.await() } else newFix.await()
                }
            val repository = FakeSpotRepository { center, _, _ -> listOf(spot.copy(location = center)) }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            viewModel.useDeviceLocation(approximate = false)
            runCurrent()
            viewModel.cancelLocation()
            viewModel.useDeviceLocation(approximate = true)
            runCurrent()
            oldFix.complete(GeoPoint(51.5074, -0.1278))
            runCurrent()
            assertEquals(demoCenter, viewModel.location.value.center)
            assertEquals(true, viewModel.location.value.locating)
            assertEquals(1, repository.searchCalls)
            newFix.complete(point)
            advanceUntilIdle()

            assertFalse(viewModel.location.value.locating)
            assertEquals(point, viewModel.location.value.center)
            assertEquals(LocationSource.APPROXIMATE, viewModel.location.value.source)
            assertEquals(HomeUiState.Content(listOf(spot.copy(location = point))), viewModel.uiState.value)
            assertEquals(2, repository.searchCalls)
        }

    @Test
    fun `late responses cannot replace the latest gps search`() =
        runTest(dispatcher) {
            val initialResponse = CompletableDeferred<List<Spot>>()
            val firstGpsResponse = CompletableDeferred<List<Spot>>()
            val firstPoint = GeoPoint(48.8566, 2.3522)
            val secondPoint = GeoPoint(51.5074, -0.1278)
            var point = firstPoint
            val device = FakeDeviceLocationProvider { point }
            val repository =
                FakeSpotRepository { center, _, _ ->
                    when (center) {
                        demoCenter -> withContext(NonCancellable) { initialResponse.await() }
                        firstPoint -> withContext(NonCancellable) { firstGpsResponse.await() }
                        else -> listOf(spot.copy(location = center))
                    }
                }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            runCurrent()
            viewModel.useDeviceLocation(approximate = false)
            runCurrent()
            point = secondPoint
            viewModel.useDeviceLocation(approximate = true)
            runCurrent()
            initialResponse.completeExceptionally(IOException("Late initial failure"))
            firstGpsResponse.complete(emptyList())
            advanceUntilIdle()

            assertEquals(secondPoint, viewModel.location.value.center)
            assertEquals(LocationSource.APPROXIMATE, viewModel.location.value.source)
            assertEquals(HomeUiState.Content(listOf(spot.copy(location = secondPoint))), viewModel.uiState.value)
            assertEquals(3, repository.searchCalls)
        }

    @Test
    fun `repeated gps search failures keep the center until a successful retry`() =
        runTest(dispatcher) {
            val point = GeoPoint(48.856612345, 2.352212345)
            val centers = mutableListOf<GeoPoint>()
            val device = FakeDeviceLocationProvider { point }
            var failures = 2
            val repository =
                FakeSpotRepository { center, _, _ ->
                    centers.add(center)
                    if (center == point && failures-- > 0) throw IOException("External service unavailable")
                    listOf(spot.copy(location = center))
                }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            viewModel.useDeviceLocation(approximate = false)
            advanceUntilIdle()
            assertEquals(point, viewModel.location.value.center)
            assertEquals(
                HomeUiState.Error("Не удалось загрузить площадки. Проверьте соединение и попробуйте снова."),
                viewModel.uiState.value,
            )
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(point, viewModel.location.value.center)
            assertEquals(
                HomeUiState.Error("Не удалось загрузить площадки. Проверьте соединение и попробуйте снова."),
                viewModel.uiState.value,
            )
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(listOf(demoCenter, point, point, point), centers)
            assertEquals(1, device.calls)
            assertEquals(HomeUiState.Content(listOf(spot.copy(location = point))), viewModel.uiState.value)
        }

    @Test
    fun `retry uses the selected approximate point even after a failed search`() =
        runTest(dispatcher) {
            val point = GeoPoint(48.8566, 2.3522)
            val device = FakeDeviceLocationProvider { point }
            var available = false
            val centers = mutableListOf<GeoPoint>()
            val repository =
                FakeSpotRepository { center, _, _ ->
                    centers.add(center)
                    if (center == point && !available) throw IOException()
                    emptyList()
                }
            val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repository), device)
            advanceUntilIdle()
            viewModel.useDeviceLocation(approximate = true)
            advanceUntilIdle()
            available = true
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(listOf(demoCenter, point, point), centers)
            assertEquals(LocationSource.APPROXIMATE, viewModel.location.value.source)
            assertEquals(HomeUiState.Empty, viewModel.uiState.value)
            assertEquals(1, device.calls)
        }

    private class FakeSpotRepository(
        private val search: suspend (GeoPoint, Int, SportType?) -> List<Spot>,
    ) : SpotRepository {
        var searchCalls = 0
            private set

        override suspend fun getSpots(): List<Spot> = error("Home only calls searchNearby")

        override suspend fun getSpotById(id: SpotId): Spot? = error("Home only calls searchNearby")

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            searchCalls++
            return search(center, radiusMeters, sportType)
        }
    }
}
