package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val spots: List<Spot>,
    ) : HomeUiState {
        fun findSpot(id: SpotId): Spot? = spots.firstOrNull { it.id == id }
    }

    data object Empty : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}
