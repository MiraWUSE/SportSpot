package com.mirawuse.sportspot.domain.model.auth

import com.mirawuse.sportspot.domain.result.DomainError

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
    val detail: DomainError? = null,
) : Exception(failure.name)
