package com.mirawuse.sportspot.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val spotSearchCenter = GeoPoint(latitude = 56.3269, longitude = 44.0059)
internal const val SPOT_SEARCH_RADIUS_METERS = 1000

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val searchNearbySpots: SearchNearbySpotsUseCase,
        private val deviceLocation: DeviceLocationProvider,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        private val _location = MutableStateFlow(SearchLocationState())
        val location: StateFlow<SearchLocationState> = _location.asStateFlow()
        private var searchJob: Job? = null
        private var locationJob: Job? = null
        private var locationRequest = 0

        init {
            loadSpots()
        }

        fun retry() {
            if (_uiState.value == HomeUiState.Loading) return
            loadSpots()
        }

        fun useDeviceLocation(approximate: Boolean) {
            if (_location.value.locating) return
            val request = ++locationRequest
            _location.value = _location.value.copy(locating = true, message = null)
            locationJob =
                viewModelScope.launch {
                    try {
                        val point = deviceLocation.getCurrentLocation()
                        currentCoroutineContext().ensureActive()
                        if (point == null) {
                            _location.value = _location.value.copy(message = R.string.location_unavailable)
                        } else {
                            loadSpots(point, if (approximate) LocationSource.APPROXIMATE else LocationSource.DEVICE)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        _location.value = _location.value.copy(message = R.string.location_unavailable)
                    } finally {
                        if (request == locationRequest) _location.value = _location.value.copy(locating = false)
                    }
                }
        }

        fun cancelLocation() {
            locationRequest++
            locationJob?.cancel()
            _location.value = _location.value.copy(locating = false)
        }

        fun permissionDenied() {
            cancelLocation()
            _location.value = _location.value.copy(message = R.string.location_denied)
        }

        private fun loadSpots(
            center: GeoPoint = _location.value.center,
            source: LocationSource = _location.value.source,
        ) {
            searchJob?.cancel()
            _location.value = _location.value.copy(center = center, source = source)
            _uiState.value = HomeUiState.Loading
            searchJob =
                viewModelScope.launch {
                    try {
                        val spots = searchNearbySpots(center = center, radiusMeters = SPOT_SEARCH_RADIUS_METERS, sportType = null)
                        currentCoroutineContext().ensureActive()
                        _uiState.value = if (spots.isEmpty()) HomeUiState.Empty else HomeUiState.Content(spots)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        _uiState.value = HomeUiState.Error("Не удалось загрузить площадки. Проверьте соединение и попробуйте снова.")
                    }
                }
        }
    }
