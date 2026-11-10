package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository

class GetMyProfileUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(): UserProfile {
        check(authRepository.getAuthState() is AuthState.Authenticated) {
            "Для просмотра профиля необходимо авторизоваться"
        }

        return userRepository.getMyProfile()
    }
}
