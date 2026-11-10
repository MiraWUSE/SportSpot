
package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult

class UpdateMyProfileUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(name: String): DomainResult<UserProfile> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.authenticatedError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        if (name.isBlank()) {
            return DomainResult.Failure(
                DomainError.Validation(
                    "Имя пользователя не может быть пустым",
                ),
            )
        }

        val profile = userRepository.updateMyProfile(name.trim())

        return DomainResult.Success(profile)
    }
}
