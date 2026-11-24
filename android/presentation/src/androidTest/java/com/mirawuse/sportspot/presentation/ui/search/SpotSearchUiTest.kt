package com.mirawuse.sportspot.presentation.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.ui.home.HomeScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.home.HomeViewModel
import com.mirawuse.sportspot.presentation.ui.map.MapScreen
import com.mirawuse.sportspot.presentation.ui.map.MapSearchStatus
import com.mirawuse.sportspot.presentation.ui.map.MapViewportState
import com.mirawuse.sportspot.presentation.ui.map.OsmMapTiles
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre

class SpotSearchUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val showing = mutableStateOf(true)
    private val center = GeoPoint(56.3269, 44.0059)
    private val football = Spot(SpotId("osm", "node:42"), "Поле", SportType.FOOTBALL, center, address = "Красная улица, 14")
    private val volleyball =
        football.copy(
            id = SpotId("osm:node", "42"),
            name = "Волейбольная площадка",
            sportType = SportType.VOLLEYBALL,
        )
    private val tennis =
        football.copy(
            id = SpotId("community", "node:42"),
            name = "Корт",
            sportType = SportType.TENNIS,
            address = "Зелёная улица, 2",
        )
    private val spots = listOf(football, volleyball, tennis)

    @Before
    fun disableExternalTiles() {
        compose.activityRule.scenario.onActivity {
            OsmMapTiles.initialize(it)
            MapLibre.setConnected(false)
        }
    }

    @After
    fun disposeMap() {
        compose.runOnIdle { showing.value = false }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { MapLibre.setConnected(null) }
    }

    @Test
    fun homeAndMapShareFiltersAndResetWithoutAnotherSearch() {
        val repo = SearchFixture(spots)
        val noLocation =
            object : DeviceLocationProvider {
                override suspend fun getCurrentLocation(): GeoPoint? = null
            }
        val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repo), noLocation)
        val showMap = mutableStateOf(false)
        var viewportEvents = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SportSpotTheme {
                if (showing.value) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val filter by viewModel.searchFilter.collectAsStateWithLifecycle()
                    if (showMap.value) {
                        MapScreen(
                            state = state,
                            center = center,
                            locationLabel = "Нижний Новгород",
                            focus = null,
                            onRetry = { error("Filter must not retry the search") },
                            onOpenSpot = {},
                            onViewportIdle = { _, _ -> viewportEvents++ },
                            filter = filter,
                            onSearchQuery = viewModel::setSearchQuery,
                            onSearchSport = viewModel::setSearchSport,
                            onResetSearch = viewModel::resetSearchFilter,
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            location = viewModel.location.value,
                            onLocate = {},
                            onPermissionDenied = {},
                            onCancelLocation = {},
                            onRetry = { error("Filter must not retry the search") },
                            onOpenSpot = {},
                            onOpenMap = { showMap.value = true },
                            filter = filter,
                            onSearchQuery = viewModel::setSearchQuery,
                            onSearchSport = viewModel::setSearchSport,
                            onResetSearch = viewModel::resetSearchFilter,
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Название, вид спорта или адрес").performTextInput("Красная")
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        compose
            .onNode(hasText("Волейбол") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithText("Найдено 1").assertIsDisplayed()
        compose.onNodeWithText("Карта →").performClick()
        compose.onNodeWithText("Поиск в загруженной области").assertIsDisplayed()
        compose.onNodeWithText("Найдено 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(SearchFilterState("Красная", SportType.VOLLEYBALL), viewModel.searchFilter.value) }

        compose.onNodeWithContentDescription("Выбрать вид спорта").performClick()
        compose.onNode(hasText("Волейбол") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()
        compose.onNodeWithText("Теннис").performClick()
        compose.onNodeWithText("Нет совпадений среди загруженных площадок.").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Нет совпадений среди загруженных площадок.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Сбросить фильтры").performScrollTo().performClick()
        compose.onNodeWithText("Найдено 3").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(SearchFilterState(), viewModel.searchFilter.value)
            assertEquals(1, repo.searches)
            assertEquals(0, viewportEvents)
            showMap.value = false
        }
        compose.onNodeWithText("Все").assertIsSelected()
        compose.onNodeWithContentDescription("Название, вид спорта или адрес").performTextInput("Нет такого адреса")
        compose.activityRule.scenario.onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithText("По заданным фильтрам ничего не найдено.").assertIsDisplayed()
        compose.onNodeWithText("Сбросить фильтры").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Найдено 3").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, repo.searches) }
    }

    @Test
    fun pickerAndDetailsUseTheFilteredSpotAndReportLoadingErrorsSeparately() {
        val viewport = mutableStateOf(MapViewportState(spots = spots, status = MapSearchStatus.READY))
        var opened: SpotId? = null
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    MapScreen(
                        state = HomeUiState.Content(spots),
                        center = center,
                        locationLabel = "Нижний Новгород",
                        focus = football.id,
                        onRetry = {},
                        onOpenSpot = { opened = it },
                        viewport = viewport.value,
                        filter = SearchFilterState(sport = SportType.TENNIS),
                    )
                }
            }
        }
        compose.onNodeWithText("Найдено 1").assertIsDisplayed()
        compose.onNodeWithText("Выбрать площадку").performClick()
        compose.onNodeWithText("1. Корт").assertIsDisplayed()
        compose.onNodeWithText("2. Волейбольная площадка").assertDoesNotExist()
        compose.onNodeWithText("1. Корт").performClick()
        compose.onNodeWithText("Подробнее").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(tennis.id, opened)
            viewport.value = MapViewportState(spots = listOf(volleyball), status = MapSearchStatus.READY)
        }
        compose.onNodeWithText("Нет совпадений среди загруженных площадок.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подробнее").assertDoesNotExist()
        compose.onNodeWithText("Выбрать площадку").assertIsNotEnabled()

        compose.runOnIdle { viewport.value = viewport.value.copy(status = MapSearchStatus.RATE_LIMITED) }
        compose.onNodeWithText("Нет совпадений среди загруженных площадок.").assertDoesNotExist()
        compose.onNodeWithText("Сервер временно ограничил поиск площадок.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { viewport.value = MapViewportState(spots = emptyList(), status = MapSearchStatus.READY) }
        compose.onNodeWithText("В этой области площадки не найдены.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Нет совпадений среди загруженных площадок.").assertDoesNotExist()
    }

    private class SearchFixture(
        private val spots: List<Spot>,
    ) : SpotRepository {
        var searches = 0
            private set

        override suspend fun getSpots(): List<Spot> = error("Expected nearby search")

        override suspend fun getSpotById(id: SpotId): Spot? = error("Expected nearby search")

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            searches++
            return spots
        }
    }
}
