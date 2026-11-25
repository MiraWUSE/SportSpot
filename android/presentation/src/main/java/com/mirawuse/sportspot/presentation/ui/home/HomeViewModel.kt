package com.mirawuse.sportspot.presentation.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.search.LightingFilter
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.SpotSort
import com.mirawuse.sportspot.presentation.ui.search.SurfaceFilter
import com.mirawuse.sportspot.presentation.ui.search.withoutConflicts
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
private const val SEARCH_SORT_KEY = "spotSearchSort"
private const val SEARCH_LIGHTING_KEY = "spotSearchLighting"
private const val SEARCH_SURFACE_KEY = "spotSearchSurface"

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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        private val _location = MutableStateFlow(SearchLocationState())
        val location: StateFlow<SearchLocationState> = _location.asStateFlow()
        private val mutableSearchFilter =
            MutableStateFlow(
                SearchFilterState(
                    query = (savedState.get<Any>(SEARCH_QUERY_KEY) as? String).orEmpty(),
                    sport = savedState.readEnum<SportType>(SEARCH_SPORT_KEY),
                    sort = savedState.readEnum<SpotSort>(SEARCH_SORT_KEY) ?: SpotSort.DEFAULT,
                    lighting = savedState.readEnum<LightingFilter>(SEARCH_LIGHTING_KEY) ?: LightingFilter.ALL,
                    surface = savedState.readEnum<SurfaceFilter>(SEARCH_SURFACE_KEY) ?: SurfaceFilter.ALL,
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

        internal fun applySearchFilter(filter: SearchFilterState) {
            if (filter == mutableSearchFilter.value) return
            savedState[SEARCH_QUERY_KEY] = filter.query
            savedState[SEARCH_SPORT_KEY] = filter.sport?.name
            savedState[SEARCH_SORT_KEY] = filter.sort.name
            savedState[SEARCH_LIGHTING_KEY] = filter.lighting.name
            savedState[SEARCH_SURFACE_KEY] = filter.surface.name
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
