package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.spot.Spot

sealed interface ViewportSearchResult {
    data class Available(
        val spots: List<Spot>,
    ) : ViewportSearchResult

    data object Deferred : ViewportSearchResult

    data class RateLimited(
        val retryAfterSeconds: Long?,
        val cachedSpots: List<Spot>?,
    ) : ViewportSearchResult
}
