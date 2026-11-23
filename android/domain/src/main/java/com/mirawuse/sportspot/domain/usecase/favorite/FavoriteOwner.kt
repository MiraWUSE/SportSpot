package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository

internal suspend fun AuthRepository.favoriteOwner(expectedUserId: String? = null): String? {
    val state = getAuthState()
    if (AuthorizationPolicy.authenticatedError(state) != null) return null
    return (state as AuthState.Authenticated).profile.id.takeIf { expectedUserId == null || it == expectedUserId }
}
