
package com.mirawuse.sportspot.domain.policy

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.result.DomainError

object AuthorizationPolicy {
    fun authenticatedError(state: AuthState): DomainError? =
        when (state) {
            AuthState.Guest -> DomainError.Unauthorized
            is AuthState.Authenticated -> null
        }

    fun moderatorError(state: AuthState): DomainError? =
        when (state) {
            AuthState.Guest -> DomainError.Unauthorized
            is AuthState.Authenticated ->
                if (state.profile.isModerator()) {
                    null
                } else {
                    DomainError.Forbidden
                }
        }
}
