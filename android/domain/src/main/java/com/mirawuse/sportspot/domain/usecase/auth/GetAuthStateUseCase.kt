package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.repository.AuthRepository

class GetAuthStateUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthState = repository.getAuthState()
}
