
package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class SignUpUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
    ): DomainResult<Unit> {
        if (name.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Имя пользователя не может быть пустым",
                ),
            )
        }

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

        repository.signUp(
            name = name.trim(),
            email = email.trim(),
            password = password,
        )

        return DomainResult.Success(Unit)
    }
}
