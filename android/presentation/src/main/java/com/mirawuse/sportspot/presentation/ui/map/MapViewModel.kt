package com.mirawuse.sportspot.presentation.ui.map

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.ViewportSearchResult
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class MapAnchor(
    val center: GeoPoint,
    val focus: SpotId?,
)

internal enum class MapSearchStatus { INITIAL, LOADING, READY, ERROR, WAITING, RATE_LIMITED, ZOOM_IN }

internal data class MapViewportState(
    val anchor: MapAnchor? = null,
    val spots: List<Spot>? = null,
    val status: MapSearchStatus = MapSearchStatus.INITIAL,
    val limited: Boolean = false,
)

@HiltViewModel
internal class MapViewModel(
    private val search: SearchNearbySpotsUseCase,
    private val elapsedMillis: () -> Long,
) : ViewModel() {
    @Inject
    constructor(search: SearchNearbySpotsUseCase) : this(search, SystemClock::elapsedRealtime)

    private val _state = MutableStateFlow(MapViewportState())
    val state = _state.asStateFlow()
    private val _openedSpot = MutableStateFlow<Spot?>(null)
    val openedSpot = _openedSpot.asStateFlow()

    private var active = false
    private var moving = false
    private var target: MapViewport? = null
    private var pending = false
    private var refresh = false
    private var job: Job? = null
    private var nextAttemptAt = 0L
    private var serverRetryAt = 0L

    fun activate(
        anchor: MapAnchor,
        loadInitial: Boolean = false,
    ) {
        if (_state.value.anchor != anchor) {
            job?.cancel()
            target = null
            pending = false
            moving = false
            _state.value = MapViewportState(anchor)
        }
        active = true
        if (pending && job?.isActive != true) {
            start()
        } else if (loadInitial && _state.value.status == MapSearchStatus.INITIAL) {
            initialViewport()?.let { select(it, refresh = false) }
        }
    }

    fun deactivate(anchor: MapAnchor) {
        if (_state.value.anchor != anchor) return
        active = false
        job?.cancel()
        moving = false
    }

    fun onMoveStarted() {
        if (!active) return
        moving = true
        job?.cancel()
    }

    fun onViewportIdle(
        viewport: MapViewport,
        previous: MapViewport?,
    ) {
        if (!active) return
        moving = false
        val selected = target
        if (selected != null && viewport.isSimilarTo(selected)) {
            if (pending && job?.isActive != true) start()
            return
        }
        if (!pending && previous != null && viewport.isSimilarTo(previous)) return
        select(viewport, refresh = false)
    }

    fun retry() {
        if (!active || moving || job?.isActive == true) return
        val viewport = target ?: initialViewport() ?: return
        if (viewport.request != null) select(viewport, refresh = true)
    }

    fun openSpot(spot: Spot?) {
        _openedSpot.value = spot
    }

    private fun select(
        viewport: MapViewport,
        refresh: Boolean,
    ) {
        job?.cancel()
        target = viewport
        this.refresh = refresh
        pending = viewport.request != null
        _state.update {
            it.copy(
                status = if (pending) waitingStatus() else MapSearchStatus.ZOOM_IN,
                limited = viewport.limited,
            )
        }
        if (pending) start()
    }

    private fun start() {
        val request = target?.request ?: return
        val forceRefresh = refresh
        job =
            viewModelScope.launch {
                if (!forceRefresh) delay(DEBOUNCE_MILLIS)
                try {
                    load(request, forceRefresh)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    pending = false
                    _state.update { it.copy(status = MapSearchStatus.ERROR) }
                }
            }
    }

    private suspend fun load(
        request: MapSearchRequest,
        refresh: Boolean,
    ) {
        val context = currentCoroutineContext()
        while (active && !moving) {
            val result =
                search.forViewport(request.center, request.radiusMeters, refresh) {
                    context.ensureActive()
                    tryStartNetwork()
                }
            context.ensureActive()
            when (result) {
                ViewportSearchResult.Deferred -> {
                    _state.update { it.copy(status = waitingStatus()) }
                    delay((maxOf(nextAttemptAt, serverRetryAt) - elapsedMillis()).coerceAtLeast(1L))
                }
                is ViewportSearchResult.Available -> {
                    pending = false
                    _state.update { it.copy(spots = result.spots.distinctBy(Spot::id), status = MapSearchStatus.READY) }
                    return
                }
                is ViewportSearchResult.RateLimited -> {
                    val now = elapsedMillis()
                    val seconds = result.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS
                    serverRetryAt = now + seconds.coerceIn(0, (Long.MAX_VALUE - now) / 1000) * 1000
                    pending = false
                    val knownSpots = result.cachedSpots?.takeIf { it.isNotEmpty() }?.distinctBy(Spot::id)
                    _state.update {
                        it.copy(spots = knownSpots ?: it.spots, status = MapSearchStatus.RATE_LIMITED)
                    }
                    // A returned 429 itself does not schedule a retry. A new gesture or
                    // the user's retry can queue exactly one attempt after the deadline.
                    return
                }
            }
        }
    }

    private fun tryStartNetwork(): Boolean {
        val now = elapsedMillis()
        if (!active || moving || now < maxOf(nextAttemptAt, serverRetryAt)) return false
        nextAttemptAt = now + NETWORK_INTERVAL_MILLIS
        _state.update { it.copy(status = MapSearchStatus.LOADING) }
        return true
    }

    private fun waitingStatus(): MapSearchStatus {
        val now = elapsedMillis()
        return when {
            now < serverRetryAt -> MapSearchStatus.RATE_LIMITED
            now < nextAttemptAt -> MapSearchStatus.WAITING
            else -> MapSearchStatus.LOADING
        }
    }

    private fun initialViewport(): MapViewport? {
        val center = _state.value.anchor?.center ?: return null
        if (!isMapCoordinate(center.latitude, center.longitude)) return null
        val radius = INITIAL_RADIUS_METERS
        return MapViewport(center, radius.toDouble(), MapSearchRequest(center, radius), limited = false)
    }

    private companion object {
        const val INITIAL_RADIUS_METERS = 1000
        const val DEBOUNCE_MILLIS = 550L
        const val NETWORK_INTERVAL_MILLIS = 20_000L
        const val DEFAULT_RETRY_SECONDS = 60L
    }
}
