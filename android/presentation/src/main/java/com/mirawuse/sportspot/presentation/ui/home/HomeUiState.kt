package com.mirawuse.sportspot.presentation.ui.home

import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.result.DomainError

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val spots: List<Spot>,
        val fallback: DomainError? = null,
    ) : HomeUiState {
        fun findSpot(id: SpotId): Spot? = spots.firstOrNull { it.id == id }
    }

    data object Empty : HomeUiState

    data class Error(
        val error: DomainError,
    ) : HomeUiState
}
