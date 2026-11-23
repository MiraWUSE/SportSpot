package com.mirawuse.sportspot.domain.model.favorite

data class FavoriteList(
    val items: List<Favorite>,
    val fromCache: Boolean = false,
)

enum class FavoriteFailure {
    OFFLINE,
    RATE_LIMITED,
    SESSION_EXPIRED,
    UNAVAILABLE,
}

class FavoriteException(
    val failure: FavoriteFailure,
) : Exception(failure.name)
