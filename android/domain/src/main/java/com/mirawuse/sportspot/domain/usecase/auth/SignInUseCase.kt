package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository

class SignInUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): UserProfile {
        require(email.isNotBlank()) {
            "Электронная почта не может быть пустой"
        }

        require(password.isNotBlank()) {
            "Пароль не может быть пустым"
        }

        return repository.signIn(
            email = email.trim(),
            password = password,
        )
    }
}
