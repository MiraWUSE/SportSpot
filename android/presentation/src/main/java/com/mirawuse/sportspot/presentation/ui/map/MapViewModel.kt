package com.mirawuse.sportspot.presentation.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
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

internal enum class MapSearchStatus { INITIAL, LOADING, READY, ERROR, ZOOM_IN }

internal data class MapViewportState(
    val anchor: MapAnchor? = null,
    val spots: List<Spot>? = null,
    val status: MapSearchStatus = MapSearchStatus.INITIAL,
    val limited: Boolean = false,
)

@HiltViewModel
internal class MapViewModel
    @Inject
    constructor(
        private val search: SearchNearbySpotsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MapViewportState())
        val state = _state.asStateFlow()
        private val _openedSpot = MutableStateFlow<Spot?>(null)
        val openedSpot = _openedSpot.asStateFlow()

        private var active = false
        private var moving = false
        private var target: MapViewport? = null
        private var job: Job? = null

        fun activate(anchor: MapAnchor) {
            if (_state.value.anchor != anchor) {
                job?.cancel()
                target = null
                _state.value = MapViewportState(anchor)
            }
            active = true
            if (_state.value.status == MapSearchStatus.LOADING && job?.isActive != true) {
                target?.let { load(it) }
            }
        }

        fun deactivate(anchor: MapAnchor) {
            if (_state.value.anchor != anchor) return
            active = false
            job?.cancel()
            if (moving && _state.value.status == MapSearchStatus.LOADING) clearInterruptedSearch()
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
            val pending = target
            if (pending != null && viewport.isSimilarTo(pending)) {
                if (_state.value.status == MapSearchStatus.LOADING && job?.isActive != true) load(pending)
                return
            }
            if (previous != null && viewport.isSimilarTo(previous)) {
                if (_state.value.status == MapSearchStatus.LOADING && job?.isActive != true) clearInterruptedSearch()
                return
            }
            load(viewport)
        }

        // False means the map is still showing Home's original search.
        fun retry(): Boolean {
            val viewport = target ?: return false
            if (active && !moving && job?.isActive != true) load(viewport, refresh = true)
            return true
        }

        fun openSpot(spot: Spot?) {
            _openedSpot.value = spot
        }

        private fun load(
            viewport: MapViewport,
            refresh: Boolean = false,
        ) {
            job?.cancel()
            target = viewport
            val request = viewport.request
            _state.update {
                it.copy(
                    status = if (request == null) MapSearchStatus.ZOOM_IN else MapSearchStatus.LOADING,
                    limited = viewport.limited,
                )
            }
            if (request == null) return

            job =
                viewModelScope.launch {
                    if (!refresh) delay(550)
                    try {
                        val spots = search(request.center, request.radiusMeters, refresh = refresh)
                        currentCoroutineContext().ensureActive()
                        _state.update { it.copy(spots = spots.distinctBy(Spot::id), status = MapSearchStatus.READY) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        _state.update { it.copy(status = MapSearchStatus.ERROR) }
                    }
                }
        }

        private fun clearInterruptedSearch() {
            target = null
            _state.update {
                it.copy(
                    status = if (it.spots == null) MapSearchStatus.INITIAL else MapSearchStatus.READY,
                    limited = false,
                )
            }
        }
    }
