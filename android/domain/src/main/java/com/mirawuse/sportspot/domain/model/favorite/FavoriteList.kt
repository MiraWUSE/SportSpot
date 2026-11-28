package com.mirawuse.sportspot.domain.model.favorite

import com.mirawuse.sportspot.domain.result.DomainError

data class FavoriteList(
    val items: List<Favorite>,
    val fromCache: Boolean = false,
    val fallback: DomainError? = null,
)

enum class FavoriteFailure {
    OFFLINE,
    RATE_LIMITED,
    SESSION_EXPIRED,
    UNAVAILABLE,
}

class FavoriteException(
    val failure: FavoriteFailure,
    val detail: DomainError? = null,
) : Exception(failure.name)
