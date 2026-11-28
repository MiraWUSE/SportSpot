package com.mirawuse.sportspot.presentation.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.repository.BackgroundSpotRefresh
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import com.mirawuse.sportspot.domain.repository.SpotRefreshArea
import com.mirawuse.sportspot.domain.repository.SpotRefreshPhase
import com.mirawuse.sportspot.domain.repository.SpotRefreshStatus
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.forms.readError
import com.mirawuse.sportspot.presentation.ui.search.LightingFilter
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.SpotSort
import com.mirawuse.sportspot.presentation.ui.search.SurfaceFilter
import com.mirawuse.sportspot.presentation.ui.search.validSearchRadius
import com.mirawuse.sportspot.presentation.ui.search.withoutConflicts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val spotSearchCenter = GeoPoint(latitude = 56.3269, longitude = 44.0059)
private const val SEARCH_QUERY_KEY = "spotSearchQuery"
private const val SEARCH_SPORT_KEY = "spotSearchSport"
private const val SEARCH_SORT_KEY = "spotSearchSort"
private const val SEARCH_LIGHTING_KEY = "spotSearchLighting"
private const val SEARCH_SURFACE_KEY = "spotSearchSurface"
private const val SEARCH_RADIUS_KEY = "spotSearchRadius"
private const val SEARCH_LATITUDE_KEY = "spotSearchLatitude"
private const val SEARCH_LONGITUDE_KEY = "spotSearchLongitude"
private const val SEARCH_LOCATION_SOURCE_KEY = "spotSearchLocationSource"

private inline fun <reified T : Enum<T>> SavedStateHandle.readEnum(key: String): T? {
    val name = get<Any>(key) as? String ?: return null
    return enumValues<T>().firstOrNull { it.name == name }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val searchNearbySpots: SearchNearbySpotsUseCase,
        private val deviceLocation: DeviceLocationProvider,
        private val savedState: SavedStateHandle = SavedStateHandle(),
        private val backgroundRefresh: BackgroundSpotRefresh? = null,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        private val _location = MutableStateFlow(restoredLocation())
        val location: StateFlow<SearchLocationState> = _location.asStateFlow()
        private val mutableSearchFilter =
            MutableStateFlow(
                SearchFilterState(
                    query = (savedState.get<Any>(SEARCH_QUERY_KEY) as? String).orEmpty(),
                    sport = savedState.readEnum<SportType>(SEARCH_SPORT_KEY),
                    sort = savedState.readEnum<SpotSort>(SEARCH_SORT_KEY) ?: SpotSort.DEFAULT,
                    lighting = savedState.readEnum<LightingFilter>(SEARCH_LIGHTING_KEY) ?: LightingFilter.ALL,
                    surface = savedState.readEnum<SurfaceFilter>(SEARCH_SURFACE_KEY) ?: SurfaceFilter.ALL,
                    radiusMeters = validSearchRadius(savedState.get<Any>(SEARCH_RADIUS_KEY) as? Int),
                ),
            )
        internal val searchFilter = mutableSearchFilter.asStateFlow()
        private val mutableBackgroundStatus = MutableStateFlow<SpotRefreshStatus?>(null)
        internal val backgroundStatus = mutableBackgroundStatus.asStateFlow()
        private var refreshCommand: Job? = null
        private var searchJob: Job? = null
        private var locationJob: Job? = null
        private var locationRequest = 0

        init {
            loadSpots()
            backgroundRefresh?.let { scheduler ->
                viewModelScope.launch {
                    scheduler.status.collect { status ->
                        mutableBackgroundStatus.value = status
                        if (status?.phase == SpotRefreshPhase.SUCCEEDED) {
                            status.area?.let { applyBackgroundSnapshot(it) }
                        }
                    }
                }
            }
        }

        internal fun scheduleBackgroundRefresh() = changeBackgroundWork { it.enqueue(currentArea()) }

        internal fun cancelBackgroundRefresh() = changeBackgroundWork { it.cancel() }

        private fun changeBackgroundWork(operation: suspend (BackgroundSpotRefresh) -> Unit) {
            val scheduler = backgroundRefresh ?: return
            if (refreshCommand?.isActive == true) return
            refreshCommand =
                viewModelScope.launch {
                    try {
                        operation(scheduler)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutableBackgroundStatus.value = SpotRefreshStatus("", SpotRefreshPhase.FAILED, error = failure.readError())
                    }
                }
        }

        private fun currentArea() = SpotRefreshArea(_location.value.center, searchFilter.value.radiusMeters)

        private suspend fun applyBackgroundSnapshot(area: SpotRefreshArea) {
            if (area != currentArea()) return
            val foreground = searchJob
            foreground?.join()
            val spots =
                try {
                    searchNearbySpots.readStoredSearch(area)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } ?: return
            if (area == currentArea() && searchJob === foreground) {
                _uiState.value = if (spots.isEmpty()) HomeUiState.Empty else HomeUiState.Content(spots)
            }
        }

        private fun restoredLocation(): SearchLocationState {
            val latitude = savedState.get<Any>(SEARCH_LATITUDE_KEY) as? Double
            val longitude = savedState.get<Any>(SEARCH_LONGITUDE_KEY) as? Double
            val source = savedState.readEnum<LocationSource>(SEARCH_LOCATION_SOURCE_KEY)
            if (latitude == null || longitude == null || source == null) return SearchLocationState()
            val center = runCatching { GeoPoint(latitude, longitude) }.getOrNull() ?: return SearchLocationState()
            return SearchLocationState(center = center, source = source)
        }

        internal fun setSearchQuery(query: String) {
            applySearchFilter(mutableSearchFilter.value.copy(query = query))
        }

        internal fun setSearchSport(sport: SportType?) {
            applySearchFilter(mutableSearchFilter.value.copy(sport = sport))
        }

        internal fun resetSearchFilter() {
            applySearchFilter(SearchFilterState())
        }

        internal fun clearConflictingSearchFilter(
            spot: Spot,
            sportLabel: (Spot) -> String,
        ) {
            applySearchFilter(searchFilter.value.withoutConflicts(spot, sportLabel))
        }

        internal fun applySearchFilter(requested: SearchFilterState) {
            val filter = requested.copy(radiusMeters = validSearchRadius(requested.radiusMeters))
            if (filter == mutableSearchFilter.value) return
            val radiusChanged = filter.radiusMeters != mutableSearchFilter.value.radiusMeters
            savedState[SEARCH_QUERY_KEY] = filter.query
            savedState[SEARCH_SPORT_KEY] = filter.sport?.name
            savedState[SEARCH_SORT_KEY] = filter.sort.name
            savedState[SEARCH_LIGHTING_KEY] = filter.lighting.name
            savedState[SEARCH_SURFACE_KEY] = filter.surface.name
            savedState[SEARCH_RADIUS_KEY] = filter.radiusMeters
            mutableSearchFilter.value = filter
            if (radiusChanged) loadSpots()
        }

        fun retry() {
            if (_uiState.value == HomeUiState.Loading) return
            val result = _uiState.value
            val error = (result as? HomeUiState.Error)?.error ?: (result as? HomeUiState.Content)?.fallback
            if (error is DomainError.Network && error.retryAtMillis > System.currentTimeMillis()) return
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
            val radius = mutableSearchFilter.value.radiusMeters
            savedState[SEARCH_LATITUDE_KEY] = center.latitude
            savedState[SEARCH_LONGITUDE_KEY] = center.longitude
            savedState[SEARCH_LOCATION_SOURCE_KEY] = source.name
            _location.value = _location.value.copy(center = center, source = source)
            val previous = _uiState.value
            _uiState.value = HomeUiState.Loading
            searchJob =
                viewModelScope.launch {
                    try {
                        val result = searchNearbySpots.withStatus(center = center, radiusMeters = radius, refresh = refresh)
                        currentCoroutineContext().ensureActive()
                        _uiState.value =
                            if (result.spots.isEmpty() && result.fallback == null) {
                                HomeUiState.Empty
                            } else {
                                HomeUiState.Content(result.spots, result.fallback)
                            }
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        _uiState.value =
                            when {
                                cancelled is TimeoutCancellationException -> HomeUiState.Error(cancelled.readError())
                                refresh && previous != HomeUiState.Loading -> previous
                                else -> HomeUiState.Empty
                            }
                    } catch (failure: Exception) {
                        currentCoroutineContext().ensureActive()
                        _uiState.value = HomeUiState.Error(failure.readError())
                    }
                }
        }
    }
