package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository

class UpdateMyProfileUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(name: String): UserProfile {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для изменения профиля необходимо авторизоваться"
        }

        require(name.isNotBlank()) {
            "Имя пользователя не может быть пустым"
        }

        return userRepository.updateMyProfile(name.trim())
    }
}
