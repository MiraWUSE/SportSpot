package com.mirawuse.sportspot.presentation.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.ModuleProvider
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.http.HttpRequest
import org.maplibre.android.http.HttpResponder
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class OsmMapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val showing = mutableStateOf(true)

    @Before
    fun disableExternalTiles() {
        compose.activityRule.scenario.onActivity {
            OsmMapTiles.initialize(it)
            // Exercise the real native renderer without relying on an external tile server.
            MapLibre.setConnected(false)
        }
    }

    @After
    fun restoreConnectivity() {
        compose.runOnIdle { showing.value = false }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { MapLibre.setConnected(null) }
    }

    @Test
    fun picksAnEmptyLocationAndKeepsTheMarkerAtItsCoordinatesAfterPanning() {
        val center = GeoPoint(56.3269, 44.0059)
        val point = mutableStateOf<GeoPoint?>(null)
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    OsmMap(
                        center = center,
                        spots = emptyList(),
                        selectedId = null,
                        isInteractive = true,
                        modifier = Modifier.size(300.dp),
                        networkStatus = MapNetworkStatus.NO_NETWORK,
                        pickedPoint = point.value,
                        onMapTap = { point.value = it },
                    )
                }
            }
        }
        val map = nativeMap()
        waitForCamera(map, center, 13.5)
        assertEquals(null, point.value)

        val expectedFirst = tapAt(map, 0.35f, 0.55f)
        compose.waitUntil(5_000) { point.value != null }
        val first = checkNotNull(point.value)
        assertEquals(expectedFirst.latitude, first.latitude, 0.000001)
        assertEquals(expectedFirst.longitude, first.longitude, 0.000001)
        waitForPickedMarker(map, first)

        val expectedSecond = tapAt(map, 0.65f, 0.60f)
        compose.waitUntil(5_000) { point.value != first }
        val second = checkNotNull(point.value)
        assertEquals(expectedSecond.latitude, second.latitude, 0.000001)
        assertEquals(expectedSecond.longitude, second.longitude, 0.000001)
        waitForPickedMarker(map, second)

        val moved = center.copy(longitude = center.longitude + 0.0008)
        compose.runOnIdle {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(moved.latitude, moved.longitude), 14.0))
        }
        waitForCamera(map, moved, 14.0)
        waitForPickedMarker(map, second)
        assertEquals(second, point.value)
    }

    @Test
    fun panFromAnEmptyMapLoadsOneAreaAndOpensTheNewCoincidentSpot() {
        val center = GeoPoint(56.3269, 44.0059)
        val anchor = MapAnchor(center, null)
        val calls = AtomicInteger()
        val opened = AtomicReference<SpotId>()
        val first = SpotId("osm", "node:123")
        val second = SpotId("community", "33333333-3333-4333-8333-333333333333")
        val repository =
            object : SpotRepository {
                override suspend fun getSpots(): List<Spot> = error("Unexpected catalog request")

                override suspend fun getSpotById(id: SpotId): Spot? = error("Unexpected spot request")

                override suspend fun searchNearby(
                    center: GeoPoint,
                    radiusMeters: Int,
                    sportType: SportType?,
                ): List<Spot> {
                    calls.incrementAndGet()
                    check(center.longitude > 44.01 && radiusMeters in 100..3000)
                    return listOf(
                        Spot(first, "Футбольное поле нового района", SportType.FOOTBALL, center),
                        Spot(second, "Корт нового района", SportType.TENNIS, center),
                    )
                }
            }
        val model = MapViewModel(SearchNearbySpotsUseCase(repository))
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    LifecycleResumeEffect(anchor) {
                        model.activate(anchor)
                        onPauseOrDispose { model.deactivate(anchor) }
                    }
                    val viewport by model.state.collectAsStateWithLifecycle()
                    MapScreen(
                        state = HomeUiState.Empty,
                        center = center,
                        locationLabel = "Нижний Новгород",
                        focus = null,
                        onRetry = { model.retry() },
                        onOpenSpot = { opened.set(it) },
                        viewport = viewport,
                        onViewportMoveStarted = model::onMoveStarted,
                        onViewportIdle = model::onViewportIdle,
                        showVisibleSpots = true,
                    )
                }
            }
        }
        val map = nativeMap()
        waitForCamera(map, center, 13.5)
        val scroll = compose.onNode(hasScrollToIndexAction())
        scroll.performScrollToNode(hasText("Площадки на карте"))
        compose.onNodeWithText("Загружено и видно: 0").assertIsDisplayed()
        scroll.performScrollToIndex(0)
        SystemClock.sleep(650)
        assertEquals(0, calls.get())
        onView(isAssignableFrom(MapView::class.java)).perform(swipeLeft())
        compose.waitUntil(10_000) {
            model.state.value.spots
                ?.size == 2
        }
        assertEquals(1, calls.get())
        val marker = checkNotNull(model.state.value.spots).first().location
        compose.waitUntil(10_000) {
            var rendered = false
            compose.runOnIdle {
                val point = map.projection.toScreenLocation(LatLng(marker.latitude, marker.longitude))
                rendered =
                    map
                        .queryRenderedFeatures(
                            RectF(point.x - 10, point.y - 10, point.x + 10, point.y + 10),
                            "sportspot-markers",
                        ).isNotEmpty()
            }
            rendered
        }
        onView(isAssignableFrom(MapView::class.java)).perform(
            GeneralClickAction(
                Tap.SINGLE,
                { view ->
                    val origin = IntArray(2)
                    view.getLocationOnScreen(origin)
                    val point = map.projection.toScreenLocation(LatLng(marker.latitude, marker.longitude))
                    floatArrayOf(origin[0] + point.x, origin[1] + point.y)
                },
                Press.FINGER,
                InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.BUTTON_PRIMARY,
            ),
        )
        val courtName = "Корт нового района"
        scroll.performScrollToNode(hasText("Площадки на карте"))
        compose.onNodeWithText("Загружено и видно: 2").assertIsDisplayed()
        val courtRow = hasText(courtName) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        scroll.performScrollToNode(courtRow)
        compose.onNode(courtRow).assertIsSelected()
        compose.onNodeWithContentDescription("Подробнее о площадке «$courtName»").performScrollTo().performClick()
        assertEquals(second, opened.get())
        assertEquals(1, calls.get())
    }

    @Test
    fun cameraSurvivesSelectionRestorationAndBackgroundAndMovesForNewSearchCenter() {
        val original = GeoPoint(56.3269, 44.0059)
        val moved = GeoPoint(48.8566, 2.3522)
        val spot = Spot(SpotId("osm", "node:1"), "Корт", SportType.BASKETBALL, original)
        val center = mutableStateOf(original)
        val selected = mutableStateOf<SpotId?>(spot.id)
        val focusRequest = mutableStateOf(0)
        val restore = StateRestorationTester(compose)
        restore.setContent {
            SportSpotTheme {
                if (showing.value) {
                    OsmMap(
                        center.value,
                        listOf(spot),
                        selected.value,
                        true,
                        Modifier.size(300.dp),
                        networkStatus = MapNetworkStatus.NO_NETWORK,
                        selectionFocusRequest = focusRequest.value,
                    )
                }
            }
        }
        var map = nativeMap()
        waitForCamera(map, original, 13.5)
        compose.runOnIdle {
            assertEquals(0, map.prefetchZoomDelta)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(moved.latitude, moved.longitude), 16.0))
        }
        waitForCamera(map, moved, 16.0)
        compose.runOnIdle { selected.value = null }
        waitForCamera(map, moved, 16.0)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForCamera(map, moved, 16.0)
        restore.emulateSavedInstanceStateRestore()
        map = nativeMap()
        waitForCamera(map, moved, 16.0)
        compose.runOnIdle { center.value = moved }
        waitForCamera(map, moved, 13.5)
        compose.runOnIdle {
            selected.value = spot.id
            focusRequest.value++
        }
        waitForCamera(map, original, 15.5)
        compose.runOnIdle { map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(moved.latitude, moved.longitude), 16.0)) }
        waitForCamera(map, moved, 16.0)
        restore.emulateSavedInstanceStateRestore()
        map = nativeMap()
        waitForCamera(map, moved, 16.0)
    }

    @Test
    fun nativeMarkerTapCyclesCoincidentSpotsWithoutChangingCamera() {
        val center = GeoPoint(56.3269, 44.0059)
        val first = Spot(SpotId("osm", "node:1"), "Корт", SportType.BASKETBALL, center)
        val second = first.copy(id = SpotId("osm", "way:1"))
        val selected = mutableStateOf(first.id)
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    OsmMap(
                        center,
                        listOf(first, second),
                        selected.value,
                        true,
                        Modifier.size(300.dp),
                        onSelect = { selected.value = it },
                        networkStatus = MapNetworkStatus.NO_NETWORK,
                    )
                }
            }
        }
        val map = nativeMap()
        waitForCamera(map, center, 13.5)
        compose.waitUntil(10_000) {
            var rendered = false
            compose.runOnIdle {
                val point = map.projection.toScreenLocation(LatLng(center.latitude, center.longitude))
                rendered =
                    map
                        .queryRenderedFeatures(
                            RectF(point.x - 20, point.y - 20, point.x + 20, point.y + 20),
                            "sportspot-markers",
                        ).isNotEmpty()
            }
            rendered
        }
        onView(isAssignableFrom(MapView::class.java)).perform(click())
        compose.waitUntil(5_000) { selected.value == second.id }
        onView(isAssignableFrom(MapView::class.java)).perform(click())
        compose.waitUntil(5_000) { selected.value == first.id }
        waitForCamera(map, center, 13.5)
    }

    @Test
    fun passiveVisibilityWrapsTheDateLineAndRespondsToResizeAndZoom() {
        val center = GeoPoint(0.0, 179.999)
        val home = Spot(SpotId("osm", "node:1"), "Якорь", SportType.FOOTBALL, center)
        val acrossDateLine = Spot(SpotId("osm", "node:2"), "Корт", SportType.TENNIS, GeoPoint(0.0, -179.995))
        val mapSize = mutableStateOf(300.dp)
        val visibility = AtomicReference<MapSpotVisibility>()
        val gestures = AtomicInteger()
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    OsmMap(
                        center,
                        listOf(home, acrossDateLine),
                        null,
                        true,
                        Modifier.size(mapSize.value),
                        networkStatus = MapNetworkStatus.NO_NETWORK,
                        onViewportIdle = { _, _ -> gestures.incrementAndGet() },
                        onSpotVisibility = visibility::set,
                    )
                }
            }
        }
        val map = nativeMap()
        compose.waitUntil(10_000) { (visibility.get() as? MapSpotVisibility.Ready)?.ids == setOf(home.id, acrossDateLine.id) }
        compose.runOnIdle { mapSize.value = 120.dp }
        compose.waitUntil(10_000) { (visibility.get() as? MapSpotVisibility.Ready)?.ids == setOf(home.id) }
        compose.runOnIdle { mapSize.value = 300.dp }
        compose.waitUntil(10_000) { (visibility.get() as? MapSpotVisibility.Ready)?.ids == setOf(home.id, acrossDateLine.id) }
        compose.runOnIdle {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(center.latitude, center.longitude), 16.0))
        }
        compose.waitUntil(10_000) { (visibility.get() as? MapSpotVisibility.Ready)?.ids == setOf(home.id) }
        assertEquals(0, gestures.get())
    }

    @Test
    fun visibleListSharesSelectionAndFiltersAndPickerCanStillRevealAnOffscreenSpot() {
        val center = GeoPoint(56.3269, 44.0059)
        val field = Spot(SpotId("osm", "node:1"), "Поле у школы", SportType.FOOTBALL, center, address = "Школьная, 2")
        val court = field.copy(id = SpotId("community", field.id.value), name = "Корт у школы", sportType = SportType.TENNIS)
        val far = court.copy(id = SpotId("osm", "node:3"), name = "Дальний корт", location = center.copy(longitude = 44.2))
        val spots = listOf(field, court, far)
        val viewport = mutableStateOf(MapViewportState(spots = spots, status = MapSearchStatus.READY))
        val filter = mutableStateOf(SearchFilterState())
        val opened = AtomicReference<SpotId>()
        val routeDestination = AtomicReference<GeoPoint>()
        val gestures = AtomicInteger()
        val restore = StateRestorationTester(compose)
        restore.setContent {
            SportSpotTheme {
                if (showing.value) {
                    MapScreen(
                        state = HomeUiState.Content(spots),
                        center = center,
                        locationLabel = "Нижний Новгород",
                        focus = null,
                        onRetry = { error("Unexpected retry") },
                        onOpenSpot = opened::set,
                        viewport = viewport.value,
                        onViewportIdle = { _, _ -> gestures.incrementAndGet() },
                        filter = filter.value,
                        showVisibleSpots = true,
                        onBuildRoute = routeDestination::set,
                    )
                }
            }
        }
        val scroll = compose.onNode(hasScrollToIndexAction())
        val rowRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val courtRow = hasText(court.name) and rowRole
        val fieldRow = hasText(field.name) and rowRole
        val listTitle = hasText("Площадки на карте")
        var map = nativeMap()
        waitForCamera(map, center, 13.5)
        scroll.performScrollToNode(listTitle)
        compose.onNodeWithText("Загружено и видно: 2").assertIsDisplayed()
        compose.onNodeWithText("Свернуть").performClick()
        compose.onNode(courtRow).assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore()
        map = nativeMap()
        waitForCamera(map, center, 13.5)
        scroll.performScrollToNode(listTitle)
        compose.onNodeWithText("Развернуть").performClick()
        scroll.performScrollToNode(courtRow)
        compose.onNode(courtRow and hasText("Школьная, 2")).assertIsNotSelected()
        compose
            .onNodeWithContentDescription("Подробнее о площадке «Корт у школы»")
            .performScrollTo()
            .performTouchInput { click() }
        assertEquals(court.id, opened.get())
        compose.onNode(courtRow).assertIsNotSelected()
        compose.onNode(courtRow).performScrollTo().performTouchInput { click() }
        compose.onNode(courtRow).assertIsSelected()
        scroll.performScrollToIndex(0)
        compose.onNodeWithText("Построить маршрут").performScrollTo().performClick()
        assertEquals(court.location, routeDestination.get())
        scroll.performScrollToIndex(0)
        waitForCamera(map, center, 13.5)
        compose.waitUntil(10_000) {
            var ready = false
            compose.runOnIdle {
                val point = map.projection.toScreenLocation(LatLng(center.latitude, center.longitude))
                ready =
                    map
                        .queryRenderedFeatures(RectF(point.x - 8, point.y - 8, point.x + 8, point.y + 8), "sportspot-markers")
                        .any { it.getBooleanProperty("selected") }
            }
            ready
        }
        onView(isAssignableFrom(MapView::class.java)).perform(click())
        scroll.performScrollToNode(fieldRow)
        compose.onNode(fieldRow).assertIsSelected()

        compose.runOnIdle { filter.value = SearchFilterState("у школы", SportType.TENNIS) }
        scroll.performScrollToNode(listTitle)
        compose.onNodeWithText("Загружено и видно: 1").assertIsDisplayed()
        compose.onNode(fieldRow).assertDoesNotExist()
        compose.runOnIdle { viewport.value = viewport.value.copy(status = MapSearchStatus.RATE_LIMITED) }
        val cached = "Сервер временно ограничил поиск площадок. Показаны ранее загруженные площадки."
        scroll.performScrollToNode(hasText(cached))
        compose.onNodeWithText(cached).assertIsDisplayed()
        scroll.performScrollToNode(courtRow)
        compose.onNode(courtRow).assertIsSelected()

        compose.runOnIdle {
            filter.value = SearchFilterState(sport = SportType.VOLLEYBALL)
            viewport.value = viewport.value.copy(status = MapSearchStatus.READY)
        }
        scroll.performScrollToNode(listTitle)
        compose.onNodeWithText("Загружено и видно: 0").assertIsDisplayed()
        compose.onAllNodesWithText("Нет совпадений среди загруженных площадок.")[0].assertExists()
        compose.runOnIdle { viewport.value = MapViewportState(spots = emptyList(), status = MapSearchStatus.READY) }
        compose.onAllNodesWithText("В этой области площадки не найдены.")[0].assertExists()
        compose.onNodeWithText("Нет совпадений среди загруженных площадок.").assertDoesNotExist()

        compose.runOnIdle {
            filter.value = SearchFilterState()
            viewport.value = MapViewportState(spots = spots, status = MapSearchStatus.READY)
        }
        scroll.performScrollToIndex(0)
        compose.onNodeWithText("Выбрать площадку").performClick()
        compose.onNodeWithText("3. Дальний корт").performClick()
        waitForCamera(map, far.location, 15.5)
        scroll.performScrollToNode(listTitle)
        compose.onNodeWithText("Загружено и видно: 1").assertIsDisplayed()
        scroll.performScrollToNode(hasText(far.name) and rowRole)
        compose.onNode(hasText(far.name) and rowRole).assertIsSelected()
        scroll.performScrollToIndex(0)
        compose.onNodeWithText("Построить маршрут").performScrollTo().performClick()
        assertEquals(far.location, routeDestination.get())

        val moreSpots = List(15) { index -> far.copy(id = SpotId("osm", "way:$index"), name = "Корт № ${index + 1}") }
        compose.runOnIdle { viewport.value = viewport.value.copy(spots = listOf(far) + moreSpots) }
        val lastRow = hasText(moreSpots.last().name) and rowRole
        scroll.performScrollToNode(lastRow)
        compose.onNode(lastRow).assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        map = nativeMap()
        waitForCamera(map, far.location, 15.5)
        scroll.performScrollToNode(listTitle)
        compose.onNodeWithText("Загружено и видно: 16").assertIsDisplayed()
        scroll.performScrollToNode(hasText(far.name) and rowRole)
        compose.onNode(hasText(far.name) and rowRole).assertIsSelected()
        assertEquals(0, gestures.get())
    }

    @Test
    fun tileServiceFailureShowsRetryWithoutExternalRequests() {
        val original = MapLibre.getModuleProvider()
        val requests = AtomicInteger()
        val handler = Handler(Looper.getMainLooper())
        compose.activityRule.scenario.onActivity {
            MapLibre.setConnected(true)
            MapLibre.setModuleProvider(
                object : ModuleProvider by original {
                    override fun createHttpRequest(): HttpRequest =
                        object : HttpRequest {
                            @Volatile private var canceled = false

                            override fun executeRequest(
                                responder: HttpResponder,
                                nativePtr: Long,
                                resourceUrl: String,
                                dataRange: String?,
                                etag: String?,
                                modified: String?,
                                offline: Boolean,
                            ) {
                                requests.incrementAndGet()
                                handler.post { if (!canceled) responder.onResponse(503, null, null, null, null, null, null, byteArrayOf()) }
                            }

                            override fun cancelRequest() {
                                canceled = true
                            }
                        }
                },
            )
        }
        try {
            compose.setContent {
                SportSpotTheme {
                    if (showing.value) {
                        OsmMap(
                            GeoPoint(-33.8688, 151.2093),
                            emptyList(),
                            null,
                            true,
                            Modifier.size(300.dp),
                            networkStatus = MapNetworkStatus.UNVERIFIED,
                        )
                    }
                }
            }
            nativeMap()
            compose.waitUntil(5_000) { requests.get() > 0 }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Не удалось загрузить карту. Попробуйте ещё раз.").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Нет доступной сети. Сохранённая карта может отображаться частично.").assertDoesNotExist()
            val beforeRetry = requests.get()
            compose.onNodeWithText("Повторить").performClick()
            compose.waitUntil(10_000) { requests.get() > beforeRetry }
        } finally {
            compose.runOnIdle { showing.value = false }
            compose.waitForIdle()
            compose.activityRule.scenario.onActivity {
                MapLibre.setModuleProvider(original)
                MapLibre.setConnected(false)
            }
        }
    }

    private fun tapAt(
        map: MapLibreMap,
        xFraction: Float,
        yFraction: Float,
    ): LatLng {
        val expected = AtomicReference<LatLng>()
        onView(isAssignableFrom(MapView::class.java)).perform(
            GeneralClickAction(
                Tap.SINGLE,
                { view ->
                    val x = view.width * xFraction
                    val y = view.height * yFraction
                    val origin = IntArray(2)
                    view.getLocationOnScreen(origin)
                    expected.set(map.projection.fromScreenLocation(PointF(x, y)))
                    floatArrayOf(origin[0] + x, origin[1] + y)
                },
                Press.FINGER,
                InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.BUTTON_PRIMARY,
            ),
        )
        return expected.get()
    }

    private fun waitForPickedMarker(
        map: MapLibreMap,
        point: GeoPoint,
    ) {
        compose.waitUntil(5_000) {
            compose.runOnIdle {
                val screen = map.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                val area = RectF(screen.x - 4, screen.y - 4, screen.x + 4, screen.y + 4)
                map.queryRenderedFeatures(area, MAP_PICKED_POINT_LAYER).isNotEmpty()
            }
        }
    }

    private fun nativeMap(): MapLibreMap {
        val result = AtomicReference<MapLibreMap>()
        onView(isAssignableFrom(MapView::class.java)).perform(
            object : ViewAction {
                override fun getConstraints(): Matcher<View> = isAssignableFrom(MapView::class.java)

                override fun getDescription(): String = "Read the current native map"

                override fun perform(
                    uiController: UiController,
                    view: View,
                ) {
                    (view as MapView).getMapAsync { result.set(it) }
                }
            },
        )
        compose.waitUntil(10_000) { result.get() != null }
        return result.get()
    }

    private fun waitForCamera(
        map: MapLibreMap,
        point: GeoPoint,
        zoom: Double,
    ) {
        compose.waitUntil(10_000) {
            var matches = false
            compose.runOnIdle {
                val camera = map.cameraPosition
                val target = camera.target
                matches = target != null &&
                    abs(target.latitude - point.latitude) < 0.0001 &&
                    abs(target.longitude - point.longitude) < 0.0001 &&
                    abs(camera.zoom - zoom) < 0.01
            }
            matches
        }
    }
}
