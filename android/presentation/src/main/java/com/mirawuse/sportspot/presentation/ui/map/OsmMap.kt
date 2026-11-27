package com.mirawuse.sportspot.presentation.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

internal const val MAP_SPOT_SOURCE = "sportspot-spots"
private const val SPOT_LAYER = "sportspot-markers"
private const val PICKED_POINT_SOURCE = "sportspot-picked-point"
internal const val MAP_PICKED_POINT_LAYER = "sportspot-picked-marker"

private enum class MapLoadState { LOADING, READY, ERROR }

@Composable
internal fun OsmMap(
    center: GeoPoint,
    spots: List<Spot>,
    selectedId: SpotId?,
    isInteractive: Boolean,
    modifier: Modifier = Modifier,
    focus: Spot? = null,
    onSelect: (SpotId) -> Unit = {},
    networkStatus: MapNetworkStatus = rememberMapNetworkStatus(),
    selectionFocusRequest: Int = 0,
    selectionFocusId: SpotId? = selectedId,
    onViewportMoveStarted: (() -> Unit)? = null,
    onViewportIdle: ((MapViewport, MapViewport?) -> Unit)? = null,
    onSpotVisibility: ((MapSpotVisibility) -> Unit)? = null,
    pickedPoint: GeoPoint? = null,
    onMapTap: ((GeoPoint) -> Unit)? = null,
    visibilitySpots: List<Spot> = spots,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var attempt by remember { mutableIntStateOf(0) }
    // CameraPosition is Parcelable. Keep pan/zoom through recreation and tab restoration.
    val camera = rememberSaveable { mutableStateOf<CameraPosition?>(null) }
    val previousCenter = rememberSaveable { mutableStateOf<String?>(null) }
    val previousFocus = rememberSaveable { mutableStateOf<String?>(null) }
    val appliedSelectionFocus = rememberSaveable(center, focus?.id) { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (isMapCoordinate(center.latitude, center.longitude)) {
                key(attempt) {
                    NativeOsmMap(
                        center,
                        spots,
                        selectedId,
                        focus,
                        isInteractive,
                        networkStatus,
                        camera.value,
                        previousCenter.value,
                        previousFocus.value,
                        selectionFocusRequest,
                        appliedSelectionFocus.intValue,
                        selectionFocusId,
                        onSelectionFocusApplied = { appliedSelectionFocus.intValue = selectionFocusRequest },
                        onCameraSaved = { camera.value = it },
                        onCameraApplied = {
                            previousCenter.value = mapCenterKey(center)
                            previousFocus.value = mapFocusKey(focus)
                        },
                        onSelect = onSelect,
                        onRetry = { attempt++ },
                        onViewportMoveStarted = onViewportMoveStarted.takeIf { isInteractive },
                        onViewportIdle = onViewportIdle.takeIf { isInteractive },
                        onSpotVisibility = onSpotVisibility,
                        pickedPoint = pickedPoint,
                        onMapTap = onMapTap,
                        visibilitySpots = visibilitySpots,
                    )
                }
            } else {
                LaunchedEffect(onSpotVisibility != null) { onSpotVisibility?.invoke(MapSpotVisibility.Unavailable) }
                Text(stringResource(R.string.map_area_unavailable), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            overlay()
        }
        // A separate footer cannot be covered by floating preview controls or the map.
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(role = Role.Button) { uriHandler.openUri(OsmMapTiles.COPYRIGHT_URL) }
                .heightIn(min = 24.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                stringResource(R.string.map_attribution),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NativeOsmMap(
    center: GeoPoint,
    spots: List<Spot>,
    selectedId: SpotId?,
    focus: Spot?,
    interactive: Boolean,
    networkStatus: MapNetworkStatus,
    savedCamera: CameraPosition?,
    previousCenter: String?,
    previousFocus: String?,
    selectionFocusRequest: Int,
    appliedSelectionFocus: Int,
    selectionFocusId: SpotId?,
    onSelectionFocusApplied: () -> Unit,
    onCameraSaved: (CameraPosition) -> Unit,
    onCameraApplied: () -> Unit,
    onSelect: (SpotId) -> Unit,
    onRetry: () -> Unit,
    onViewportMoveStarted: (() -> Unit)?,
    onViewportIdle: ((MapViewport, MapViewport?) -> Unit)?,
    onSpotVisibility: ((MapSpotVisibility) -> Unit)?,
    pickedPoint: GeoPoint?,
    onMapTap: ((GeoPoint) -> Unit)?,
    visibilitySpots: List<Spot>,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val colors = MaterialTheme.colorScheme
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var nativeMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var cameraApplied by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var settledCamera by remember { mutableIntStateOf(0) }
    var cameraMoving by remember { mutableStateOf(false) }
    var projectionFailed by remember { mutableStateOf(false) }
    val spotVisibility by rememberUpdatedState(onSpotVisibility)
    val currentSpots by rememberUpdatedState(spots)
    val currentSelected by rememberUpdatedState(selectedId)
    val select by rememberUpdatedState(onSelect)
    val mapTap by rememberUpdatedState(onMapTap)
    val saveCamera by rememberUpdatedState(onCameraSaved)
    val viewportMoveStarted by rememberUpdatedState(onViewportMoveStarted)
    val viewportIdle by rememberUpdatedState(onViewportIdle)
    val mapView =
        remember(context) {
            try {
                OsmMapTiles.initialize(context)
                val options =
                    MapLibreMapOptions.createFromAttributes(context).textureMode(true).apply {
                        prefetchZoomDelta =
                            OsmMapTiles.PREFETCH_ZOOM_DELTA
                    }
                MapView(context, options).apply {
                    contentDescription = context.getString(R.string.map_description)
                    onCreate(null)
                }
            } catch (_: Exception) {
                null
            } catch (_: LinkageError) {
                null
            }
        }

    if (mapView != null) {
        DisposableEffect(mapView, owner) {
            var disposed = false
            var started = false
            var resumed = false
            var gesture = false
            var previousViewport: MapViewport? = null
            val rendered =
                MapView.OnDidFinishRenderingMapListener { fully ->
                    // A completed frame may contain failed tiles. Keep the failure until retry.
                    if (!disposed && fully && loadState != MapLoadState.ERROR) loadState = MapLoadState.READY
                }

            fun projectionError() {
                if (!disposed) {
                    loadState = MapLoadState.ERROR
                    projectionFailed = true
                }
            }
            val failed = MapView.OnDidFailLoadingMapListener { projectionError() }
            val tileAction =
                MapView.OnTileActionListener { operation, _, _, _, _, _, source ->
                    if (!disposed && source == "osm" && operation == TileOperation.Error) loadState = MapLoadState.ERROR
                }
            val renderError = MapView.OnRenderErrorListener { projectionError() }
            val layoutChanged =
                View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    mapSize = IntSize(view.width, view.height)
                }
            val moving =
                MapView.OnCameraWillChangeListener { _ ->
                    if (!disposed) {
                        loadState = MapLoadState.LOADING
                        loadGeneration++
                    }
                }
            val moveStarted =
                MapLibreMap.OnCameraMoveStartedListener { reason ->
                    if (!disposed && cameraApplied && resumed && spotVisibility != null) cameraMoving = true
                    if (!disposed && cameraApplied && resumed && viewportIdle != null) {
                        gesture = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                        if (gesture) viewportMoveStarted?.invoke()
                    }
                }
            val idle =
                MapLibreMap.OnCameraIdleListener {
                    if (disposed || !cameraApplied) return@OnCameraIdleListener
                    val map = nativeMap ?: return@OnCameraIdleListener
                    saveCamera(map.cameraPosition)
                    if (spotVisibility != null) {
                        cameraMoving = false
                        settledCamera++
                    }
                    val afterGesture = gesture
                    gesture = false
                    val callback = viewportIdle ?: return@OnCameraIdleListener
                    if (!resumed || mapView.width == 0 || mapView.height == 0) return@OnCameraIdleListener
                    val viewport = map.currentViewport() ?: return@OnCameraIdleListener
                    val before = previousViewport
                    if (!afterGesture || before == null || !viewport.isSimilarTo(before)) previousViewport = viewport
                    if (afterGesture) callback(viewport, before)
                }
            val clicked =
                MapLibreMap.OnMapClickListener { point ->
                    val map = nativeMap
                    if (disposed || map == null) return@OnMapClickListener false
                    val pick = mapTap
                    if (pick != null) {
                        val coordinate = point.toViewportPoint()?.takeIf { isMapCoordinate(it.latitude, it.longitude) }
                        coordinate?.let(pick)
                        return@OnMapClickListener coordinate != null
                    }
                    val touch = map.projection.toScreenLocation(point)
                    val radius = 20 * context.resources.displayMetrics.density
                    val hits =
                        map.queryRenderedFeatures(
                            RectF(touch.x - radius, touch.y - radius, touch.x + radius, touch.y + radius),
                            SPOT_LAYER,
                        )
                    val marker =
                        hits
                            .mapNotNull { feature ->
                                val id = SpotId(feature.getStringProperty("source"), feature.getStringProperty("value"))
                                currentSpots.firstOrNull { it.id == id }
                            }.minByOrNull { spot ->
                                val screen = map.projection.toScreenLocation(LatLng(spot.location.latitude, spot.location.longitude))
                                (screen.x - touch.x) * (screen.x - touch.x) + (screen.y - touch.y) * (screen.y - touch.y)
                            }
                    val id = marker?.let { nextMapSpot(currentSpots, it.id, currentSelected) }
                    if (id != null) select(id)
                    id != null
                }

            fun pauseAndStop() {
                if (resumed) {
                    mapView.onPause()
                    resumed = false
                }
                if (started) {
                    mapView.onStop()
                    started = false
                }
            }

            fun release() {
                if (disposed) return
                disposed = true
                nativeMap?.removeOnMapClickListener(clicked)
                nativeMap?.removeOnCameraIdleListener(idle)
                nativeMap?.removeOnCameraMoveStartedListener(moveStarted)
                mapView.removeOnDidFinishRenderingMapListener(rendered)
                mapView.removeOnDidFailLoadingMapListener(failed)
                mapView.removeOnTileActionListener(tileAction)
                mapView.removeOnRenderErrorListener(renderError)
                mapView.removeOnCameraWillChangeListener(moving)
                mapView.removeOnLayoutChangeListener(layoutChanged)
                pauseAndStop()
                mapView.onDestroy()
            }
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START ->
                            if (!disposed && !started) {
                                mapView.onStart()
                                started = true
                            }
                        Lifecycle.Event.ON_RESUME ->
                            if (!disposed && !resumed) {
                                mapView.onResume()
                                resumed = true
                            }
                        Lifecycle.Event.ON_PAUSE ->
                            if (resumed) {
                                gesture = false
                                cameraMoving = false
                                mapView.onPause()
                                resumed = false
                            }
                        Lifecycle.Event.ON_STOP -> pauseAndStop()
                        Lifecycle.Event.ON_DESTROY -> release()
                        else -> Unit
                    }
                }
            val memory =
                object : ComponentCallbacks2 {
                    override fun onConfigurationChanged(newConfig: Configuration) = Unit

                    override fun onLowMemory() {
                        if (!disposed) mapView.onLowMemory()
                    }

                    override fun onTrimMemory(level: Int) {
                        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) onLowMemory()
                    }
                }
            context.applicationContext.registerComponentCallbacks(memory)
            mapView.addOnDidFinishRenderingMapListener(rendered)
            mapView.addOnDidFailLoadingMapListener(failed)
            mapView.addOnTileActionListener(tileAction)
            mapView.addOnRenderErrorListener(renderError)
            mapView.addOnCameraWillChangeListener(moving)
            if (spotVisibility != null) {
                mapSize = IntSize(mapView.width, mapView.height)
                mapView.addOnLayoutChangeListener(layoutChanged)
            }
            owner.lifecycle.addObserver(observer)
            mapView.getMapAsync { map ->
                if (!disposed) {
                    nativeMap = map
                    map.prefetchZoomDelta = OsmMapTiles.PREFETCH_ZOOM_DELTA
                    map.setMinZoomPreference(1.0)
                    map.setMaxZoomPreference(19.0)
                    map.uiSettings.apply {
                        isAttributionEnabled = false // Persistent clickable attribution is in the Compose footer.
                        isLogoEnabled = false
                        setAllGesturesEnabled(interactive)
                        isCompassEnabled = interactive
                    }
                    if (interactive) {
                        map.addOnMapClickListener(clicked)
                        map.addOnCameraMoveStartedListener(moveStarted)
                    }
                    map.addOnCameraIdleListener(idle)
                    map.setStyle(Style.Builder().fromJson(OsmMapTiles.styleJson)) { loaded ->
                        if (!disposed) {
                            loaded.addSource(GeoJsonSource(MAP_SPOT_SOURCE, FeatureCollection.fromFeatures(emptyList())))
                            loaded.addLayer(CircleLayer(SPOT_LAYER, MAP_SPOT_SOURCE))
                            style = loaded
                        }
                    }
                }
            }
            onDispose {
                owner.lifecycle.removeObserver(observer)
                context.applicationContext.unregisterComponentCallbacks(memory)
                release()
            }
        }
        LaunchedEffect(style, spots, selectedId, colors.primary, colors.onPrimary, interactive) {
            val loaded = style ?: return@LaunchedEffect
            val features =
                mapMarkerGroups(spots).map { (point, group) ->
                    Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                        addStringProperty("source", group.first().id.source)
                        addStringProperty("value", group.first().id.value)
                        addBooleanProperty("selected", group.any { it.id == selectedId })
                    }
                }
            loaded.getSourceAs<GeoJsonSource>(MAP_SPOT_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
            loaded.getLayerAs<CircleLayer>(SPOT_LAYER)?.setProperties(
                circleRadius(if (interactive) switchCase(get("selected"), literal(12f), literal(8f)) else literal(4f)),
                circleColor(switchCase(get("selected"), color(colors.primary.toArgb()), color(colors.onPrimary.toArgb()))),
                circleStrokeColor(switchCase(get("selected"), color(colors.onPrimary.toArgb()), color(colors.primary.toArgb()))),
                circleStrokeWidth(if (interactive) 2f else 1.5f),
            )
        }
        LaunchedEffect(style, center, focus?.id, focus?.location) {
            if (style == null) return@LaunchedEffect
            val map = nativeMap ?: return@LaunchedEffect
            val target = mapCameraTarget(center, focus, previousCenter, previousFocus, savedCamera != null, if (interactive) 13.5 else 12.0)
            cameraApplied = true
            if (target != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.point.latitude, target.point.longitude), target.zoom))
            } else if (savedCamera != null) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(savedCamera))
            }
            onCameraApplied()
        }
        if (onMapTap != null) {
            LaunchedEffect(style, pickedPoint, colors.primary, colors.onPrimary) {
                val loaded = style ?: return@LaunchedEffect
                val marker =
                    pickedPoint?.takeIf { isMapCoordinate(it.latitude, it.longitude) }?.let {
                        Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
                    }
                val features = FeatureCollection.fromFeatures(listOfNotNull(marker))
                val source = loaded.getSourceAs<GeoJsonSource>(PICKED_POINT_SOURCE)
                if (source == null) {
                    if (marker == null) return@LaunchedEffect
                    loaded.addSource(GeoJsonSource(PICKED_POINT_SOURCE, features))
                    loaded.addLayer(CircleLayer(MAP_PICKED_POINT_LAYER, PICKED_POINT_SOURCE))
                } else {
                    source.setGeoJson(features)
                }
                loaded.getLayerAs<CircleLayer>(MAP_PICKED_POINT_LAYER)?.setProperties(
                    circleRadius(10f),
                    circleColor(colors.primary.toArgb()),
                    circleStrokeColor(colors.onPrimary.toArgb()),
                    circleStrokeWidth(3f),
                )
            }
        }
        // A list choice may reveal an offscreen point. Marker taps keep the current camera.
        LaunchedEffect(style, selectionFocusRequest) {
            if (style == null || selectionFocusRequest == 0 || selectionFocusRequest == appliedSelectionFocus) return@LaunchedEffect
            val map = nativeMap ?: return@LaunchedEffect
            val chosen = spots.firstOrNull { it.id == selectionFocusId }
            if (chosen != null && isMapCoordinate(chosen.location.latitude, chosen.location.longitude)) {
                val target = LatLng(chosen.location.latitude, chosen.location.longitude)
                val screen = map.projection.toScreenLocation(target)
                if (!Offset(screen.x, screen.y).isInMap(IntSize(mapView.width, mapView.height))) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, maxOf(map.cameraPosition.zoom, 15.5)))
                }
            }
            onSelectionFocusApplied()
        }
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setOnTouchListener { _, event ->
                    if (interactive) {
                        view.parent?.requestDisallowInterceptTouchEvent(
                            event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL,
                        )
                    }
                    false
                }
            },
        )
        // The Compose gesture surface lets the parent LazyColumn keep vertical scrolling.
        if (!interactive) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} })
    }
    if (onSpotVisibility != null) {
        val geometryUnavailable = mapView == null || projectionFailed || (!cameraApplied && loadState == MapLoadState.ERROR)
        LaunchedEffect(cameraApplied, cameraMoving, settledCamera, mapSize, visibilitySpots, geometryUnavailable) {
            val map = nativeMap
            val visibility =
                when {
                    geometryUnavailable -> MapSpotVisibility.Unavailable
                    map == null || !cameraApplied || mapSize.width == 0 || mapSize.height == 0 -> MapSpotVisibility.Loading
                    cameraMoving -> MapSpotVisibility.Moving
                    else -> {
                        val projection = map.projection
                        MapSpotVisibility.Ready(
                            projectedSpotIds(visibilitySpots, mapSize) { point ->
                                val screen = projection.toScreenLocation(LatLng(point.latitude, point.longitude))
                                Offset(screen.x, screen.y)
                            },
                            center = map.cameraPosition.target?.toViewportPoint() ?: center,
                        )
                    }
                }
            spotVisibility?.invoke(visibility)
        }
    }
    LaunchedEffect(loadGeneration) {
        delay(20_000)
        if (loadState == MapLoadState.LOADING) loadState = MapLoadState.ERROR
    }
    if (mapView == null || networkStatus == MapNetworkStatus.NO_NETWORK || loadState != MapLoadState.READY) {
        val message =
            when {
                networkStatus == MapNetworkStatus.NO_NETWORK -> R.string.map_no_network
                mapView == null || loadState == MapLoadState.ERROR -> R.string.map_load_error
                networkStatus == MapNetworkStatus.UNVERIFIED -> R.string.map_network_unverified
                else -> R.string.map_loading
            }
        Surface(color = colors.surface.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(message), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                if (message == R.string.map_no_network || message == R.string.map_load_error) {
                    TextButton(
                        onClick = onRetry,
                        enabled = networkStatus != MapNetworkStatus.NO_NETWORK,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(stringResource(R.string.map_retry), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun MapLibreMap.currentViewport(): MapViewport? {
    val camera = cameraPosition
    val center = camera.target?.toViewportPoint() ?: return null
    val region = projection.visibleRegion
    val corners =
        listOf(region.nearLeft, region.nearRight, region.farLeft, region.farRight).map {
            it?.toViewportPoint() ?: return null
        }
    return mapViewport(center, corners, camera.zoom)
}

private fun LatLng.toViewportPoint(): GeoPoint? {
    val longitude = wrappedMapLongitude(longitude)
    if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite()) return null
    return GeoPoint(latitude, longitude)
}
