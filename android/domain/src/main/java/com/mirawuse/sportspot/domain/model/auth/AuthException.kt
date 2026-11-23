package com.mirawuse.sportspot.domain.model.auth

enum class AuthFailure {
    INVALID_CREDENTIALS,
    EMAIL_UNCONFIRMED,
    WEAK_PASSWORD,
    RATE_LIMITED,
    OFFLINE,
    SESSION_EXPIRED,
    UNAVAILABLE,
}

class AuthException(
    val failure: AuthFailure,
) : Exception(failure.name)
