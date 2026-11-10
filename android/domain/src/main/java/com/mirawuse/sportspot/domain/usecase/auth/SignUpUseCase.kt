package com.mirawuse.sportspot.domain.usecase.auth

import com.mirawuse.sportspot.domain.repository.AuthRepository

class SignUpUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        password: String,
    ) {
        require(name.isNotBlank()) {
            "Имя пользователя не может быть пустым"
        }

        require(email.isNotBlank()) {
            "Электронная почта не может быть пустой"
        }

        require(password.isNotBlank()) {
            "Пароль не может быть пустым"
        }

        repository.signUp(
            name = name.trim(),
            email = email.trim(),
            password = password,
        )
    }
}
