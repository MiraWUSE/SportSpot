package com.mirawuse.sportspot.presentation.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
            SportSpotTheme(darkTheme = true) {
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
                            onApplySearch = viewModel::applySearchFilter,
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
                            onApplySearch = viewModel::applySearchFilter,
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
        waitForText("Найдено 1")
        compose.onNodeWithText("Найдено 1").assertIsDisplayed()
        compose.onNodeWithText("Карта →").performClick()
        compose.onNodeWithText("Поиск в загруженной области").assertIsDisplayed()
        waitForText("Найдено 1")
        compose.onNodeWithText("Найдено 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(SearchFilterState("Красная", SportType.VOLLEYBALL), viewModel.searchFilter.value) }

        compose.onNodeWithContentDescription("Фильтры").performClick()
        compose
            .onNode(
                hasText("Волейбол") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
            ).performScrollTo()
            .assertIsSelected()
        compose.onNodeWithText("Радиус поиска").assertDoesNotExist()
        compose.onNodeWithText("Теннис").performScrollTo().performClick()
        compose.onNodeWithText("Применить").performClick()
        waitForText("По заданным фильтрам ничего не найдено.")
        compose.onNodeWithText("По заданным фильтрам ничего не найдено.").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        waitForText("По заданным фильтрам ничего не найдено.")
        compose.onNodeWithText("По заданным фильтрам ничего не найдено.").performScrollTo().assertIsDisplayed()
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
        waitForText("По заданным фильтрам ничего не найдено.")
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
                        focus = null,
                        onRetry = {},
                        onOpenSpot = { opened = it },
                        viewport = viewport.value,
                        filter = SearchFilterState(sport = SportType.TENNIS),
                    )
                }
            }
        }
        waitForText("Найдено 1")
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
        waitForText("По заданным фильтрам ничего не найдено.")
        compose.onNodeWithText("По заданным фильтрам ничего не найдено.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подробнее").assertDoesNotExist()
        compose.onNodeWithText("Выбрать площадку").assertIsNotEnabled()

        compose.runOnIdle { viewport.value = viewport.value.copy(status = MapSearchStatus.RATE_LIMITED) }
        compose.onNodeWithText("По заданным фильтрам ничего не найдено.").assertDoesNotExist()
        compose.onNodeWithText("Сервер временно ограничил поиск площадок.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { viewport.value = MapViewportState(spots = emptyList(), status = MapSearchStatus.READY) }
        compose.onNodeWithText("В этой области площадки не найдены.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("По заданным фильтрам ничего не найдено.").assertDoesNotExist()
    }

    @Test
    fun panelKeepsItsDraftOnRecreationAndAppliesTheRadiusOnlyOnConfirmation() {
        val repo = SearchFixture(spots)
        val noLocation =
            object : DeviceLocationProvider {
                override suspend fun getCurrentLocation(): GeoPoint? = null
            }
        val viewModel = HomeViewModel(SearchNearbySpotsUseCase(repo), noLocation)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SportSpotTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.width(300.dp)) {
                        val filter by viewModel.searchFilter.collectAsStateWithLifecycle()
                        SpotSearchBar(filter, viewModel::setSearchQuery, viewModel::applySearchFilter, viewModel::resetSearchFilter, true)
                    }
                }
            }
        }
        compose.onNodeWithText("Радиус поиска: 1 км").assertIsDisplayed()
        compose.onNodeWithContentDescription("Фильтры").performClick()
        compose
            .onNodeWithText("Сначала ближайшие")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        compose
            .onNodeWithText("Есть освещение")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        compose
            .onNodeWithText("Твёрдое")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        compose
            .onNodeWithText("3 км")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        compose.runOnIdle {
            assertEquals(SearchFilterState(), viewModel.searchFilter.value)
            assertEquals(listOf(1000), repo.radii)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("3 км").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Применить").performClick()
        waitForText("Радиус поиска: 3 км", substring = true)
        compose.onNodeWithText("От центра поиска", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                SearchFilterState(
                    sort = SpotSort.NEAREST,
                    lighting = LightingFilter.LIT,
                    surface = SurfaceFilter.HARD,
                    radiusMeters = 3000,
                ),
                viewModel.searchFilter.value,
            )
            assertEquals(listOf(1000, 3000), repo.radii)
        }
        compose.onNodeWithContentDescription("Фильтры").performClick()
        compose.onNodeWithText("500 м").performScrollTo().performClick()
        compose.onNodeWithText("Закрыть").performClick()
        compose.runOnIdle { assertEquals(listOf(1000, 3000), repo.radii) }
        compose.onNodeWithContentDescription("Фильтры").performClick()
        compose.onNodeWithText("Сбросить").performClick()
        compose.onNodeWithText("Радиус поиска: 1 км").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(SearchFilterState(), viewModel.searchFilter.value)
            assertEquals(listOf(1000, 3000, 1000), repo.radii)
        }
    }

    @Test
    fun explicitFocusAndFavoriteStayOpenWithIncompatibleFilters() {
        val favorite = mutableStateOf(false)
        val filter = mutableStateOf(SearchFilterState(lighting = LightingFilter.LIT, surface = SurfaceFilter.RUBBER))
        var opened: SpotId? = null
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    MapScreen(
                        state = HomeUiState.Content(listOf(football)),
                        center = center,
                        locationLabel = "Нижний Новгород",
                        focus = football.id,
                        onRetry = {},
                        onOpenSpot = { opened = it },
                        viewport = if (favorite.value) null else MapViewportState(spots = listOf(tennis), status = MapSearchStatus.READY),
                        filter = filter.value.takeUnless { favorite.value },
                        onApplySearch = { filter.value = it },
                    )
                }
            }
        }
        compose.onNodeWithText("Подробнее").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(football.id, opened) }
        compose.onNodeWithContentDescription("Фильтры").performScrollTo().performClick()
        compose.onNodeWithText("Песок").performScrollTo().performClick()
        compose.onNodeWithText("Применить").performClick()
        waitForText("По заданным фильтрам ничего не найдено.")
        compose.onNodeWithText("Подробнее").assertDoesNotExist()
        compose.runOnIdle { favorite.value = true }
        compose.onNodeWithContentDescription("Фильтры").assertDoesNotExist()
        compose.onNodeWithText("Поле").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Подробнее").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(football.id, opened) }
    }

    private fun waitForText(
        label: String,
        substring: Boolean = false,
    ) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(label, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private class SearchFixture(
        private val spots: List<Spot>,
    ) : SpotRepository {
        val radii = mutableListOf<Int>()
        val searches: Int get() = radii.size

        override suspend fun getSpots(): List<Spot> = error("Expected nearby search")

        override suspend fun getSpotById(id: SpotId): Spot? = error("Expected nearby search")

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            radii.add(radiusMeters)
            return spots
        }
    }
}
