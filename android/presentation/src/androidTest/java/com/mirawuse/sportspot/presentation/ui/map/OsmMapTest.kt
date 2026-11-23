package com.mirawuse.sportspot.presentation.ui.map

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
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
    fun cameraSurvivesSelectionRestorationAndBackgroundAndMovesForNewSearchCenter() {
        val original = GeoPoint(56.3269, 44.0059)
        val moved = GeoPoint(48.8566, 2.3522)
        val spot = Spot(SpotId("osm", "node:1"), "Корт", SportType.BASKETBALL, original)
        val center = mutableStateOf(original)
        val selected = mutableStateOf<SpotId?>(spot.id)
        val restore = StateRestorationTester(compose)
        restore.setContent {
            SportSpotTheme {
                if (showing.value) OsmMap(center.value, listOf(spot), selected.value, true, Modifier.size(300.dp), isOnline = false)
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
                        isOnline = false,
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
                    if (showing.value) OsmMap(GeoPoint(-33.8688, 151.2093), emptyList(), null, true, Modifier.size(300.dp), isOnline = true)
                }
            }
            nativeMap()
            compose.waitUntil(5_000) { requests.get() > 0 }
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Не удалось загрузить карту. Попробуйте ещё раз.").fetchSemanticsNodes().isNotEmpty()
            }
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
