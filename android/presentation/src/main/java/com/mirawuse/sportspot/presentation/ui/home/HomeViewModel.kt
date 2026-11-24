package com.mirawuse.sportspot.presentation.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
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
private const val SEARCH_QUERY_KEY = "spotSearchQuery"
private const val SEARCH_SPORT_KEY = "spotSearchSport"

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val searchNearbySpots: SearchNearbySpotsUseCase,
        private val deviceLocation: DeviceLocationProvider,
        private val savedState: SavedStateHandle = SavedStateHandle(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        private val _location = MutableStateFlow(SearchLocationState())
        val location: StateFlow<SearchLocationState> = _location.asStateFlow()
        private val mutableSearchFilter =
            MutableStateFlow(
                SearchFilterState(
                    query = savedState.get<String>(SEARCH_QUERY_KEY).orEmpty(),
                    sport = savedState.get<String>(SEARCH_SPORT_KEY)?.let { name -> SportType.entries.find { it.name == name } },
                ),
            )
        internal val searchFilter = mutableSearchFilter.asStateFlow()
        private var searchJob: Job? = null
        private var locationJob: Job? = null
        private var locationRequest = 0

        init {
            loadSpots()
        }

        internal fun setSearchQuery(query: String) {
            updateSearchFilter(mutableSearchFilter.value.copy(query = query))
        }

        internal fun setSearchSport(sport: SportType?) {
            updateSearchFilter(mutableSearchFilter.value.copy(sport = sport))
        }

        internal fun resetSearchFilter() {
            updateSearchFilter(SearchFilterState())
        }

        private fun updateSearchFilter(filter: SearchFilterState) {
            savedState[SEARCH_QUERY_KEY] = filter.query
            savedState[SEARCH_SPORT_KEY] = filter.sport?.name
            mutableSearchFilter.value = filter
        }

        fun retry() {
            if (_uiState.value == HomeUiState.Loading) return
            refresh()
        }

        fun refresh() {
            loadSpots(refresh = true)
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
            refresh: Boolean = false,
        ) {
            searchJob?.cancel()
            _location.value = _location.value.copy(center = center, source = source)
            _uiState.value = HomeUiState.Loading
            searchJob =
                viewModelScope.launch {
                    try {
                        val spots = searchNearbySpots(center = center, radiusMeters = SPOT_SEARCH_RADIUS_METERS, refresh = refresh)
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
