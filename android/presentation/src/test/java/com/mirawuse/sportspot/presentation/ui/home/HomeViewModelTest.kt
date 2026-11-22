package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
