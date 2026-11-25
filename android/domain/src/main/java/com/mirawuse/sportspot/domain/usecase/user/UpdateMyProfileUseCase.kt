
package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.contentResult
import com.mirawuse.sportspot.domain.validation.FormError
import com.mirawuse.sportspot.domain.validation.FormValidation

class UpdateMyProfileUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(
        name: String,
        expectedUserId: String? = null,
    ): DomainResult<UserProfile> {
        val current = (authRepository.getAuthState() as? AuthState.Authenticated)?.profile
        if (current == null || (expectedUserId != null && expectedUserId != current.id)) {
            return DomainResult.Failure(DomainError.Unauthorized)
        }

        val normalized = name.trim()
        val error = FormValidation.profileName(normalized)
        if (error != null) {
            val message =
                if (error == FormError.REQUIRED) {
                    "Имя пользователя не может быть пустым"
                } else {
                    "Имя должно содержать не больше ${FormValidation.MAX_PROFILE_NAME_LENGTH} символов"
                }
            return DomainResult.Failure(DomainError.Validation(message))
        }
        if (normalized == current.name) return DomainResult.Success(current)
        return contentResult { userRepository.updateMyProfile(current.id, normalized) }
    }
}
