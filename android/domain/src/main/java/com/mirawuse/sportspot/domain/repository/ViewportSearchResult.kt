package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.result.DomainError

sealed interface ViewportSearchResult {
    data class Available(
        val spots: List<Spot>,
        val fallback: DomainError? = null,
    ) : ViewportSearchResult

    data object Deferred : ViewportSearchResult

    data class RateLimited(
        val retryAfterSeconds: Long?,
        val cachedSpots: List<Spot>?,
    ) : ViewportSearchResult
}
