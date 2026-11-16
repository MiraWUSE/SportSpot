package com.mirawuse.sportspot.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val searchNearbySpots: SearchNearbySpotsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            loadSpots()
        }

        fun retry() {
            if (_uiState.value == HomeUiState.Loading) return
            loadSpots()
        }

        private fun loadSpots() {
            _uiState.value = HomeUiState.Loading
            viewModelScope.launch {
                try {
                    val spots = searchNearbySpots(center = DEMO_CENTER, radiusMeters = DEMO_RADIUS_METERS, sportType = null)
                    _uiState.value = if (spots.isEmpty()) HomeUiState.Empty else HomeUiState.Content(spots)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = HomeUiState.Error("Не удалось загрузить площадки. Проверьте соединение и попробуйте снова.")
                }
            }
        }

        private companion object {
            val DEMO_CENTER = GeoPoint(latitude = 56.3269, longitude = 44.0059)
            const val DEMO_RADIUS_METERS = 1000
        }
    }
