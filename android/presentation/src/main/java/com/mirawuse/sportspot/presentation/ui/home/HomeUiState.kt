package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.spot.Spot

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val spots: List<Spot>,
    ) : HomeUiState

    data object Empty : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}
