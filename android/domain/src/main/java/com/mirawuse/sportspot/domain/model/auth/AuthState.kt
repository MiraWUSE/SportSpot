package com.mirawuse.sportspot.domain.model.auth

import com.mirawuse.sportspot.domain.model.user.UserProfile

sealed interface AuthState {
    data object Guest : AuthState

    data class Authenticated(
        val profile: UserProfile,
    ) : AuthState
}
