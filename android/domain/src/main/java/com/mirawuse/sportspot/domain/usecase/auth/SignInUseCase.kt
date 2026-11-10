
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class SignInUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): DomainResult<UserProfile> {
        if (email.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Электронная почта не может быть пустой",
                ),
            )
        }

        if (password.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Пароль не может быть пустым",
                ),
            )
        }

        val profile =
            repository.signIn(
                email = email.trim(),
                password = password,
            )

        return DomainResult.Success(profile)
    }
}
