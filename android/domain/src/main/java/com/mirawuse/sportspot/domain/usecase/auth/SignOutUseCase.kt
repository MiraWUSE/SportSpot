package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.repository.AuthRepository

class SignOutUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() {
        val authState = repository.getAuthState()

        if (authState is AuthState.Guest) {
            return
        }

        repository.signOut()
    }
}
