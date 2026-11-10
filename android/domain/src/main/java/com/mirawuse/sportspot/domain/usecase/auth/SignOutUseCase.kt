
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainResult

class SignOutUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): DomainResult<Unit> {
        val authState = repository.getAuthState()

        if (authState is AuthState.Guest) {
            return DomainResult.Success(Unit)
        }

        repository.signOut()

        return DomainResult.Success(Unit)
    }
}
