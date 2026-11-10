
package com.mirawuse.sportspot.domain.usecase.user

import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import com.mirawuse.sportspot.domain.result.DomainResult

class GetMyProfileUseCase(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(): DomainResult<UserProfile> {
        val authState = authRepository.getAuthState()
        val accessError = AuthorizationPolicy.authenticatedError(authState)

        if (accessError != null) {
            return DomainResult.Failure(accessError)
        }

        val profile = userRepository.getMyProfile()

        return DomainResult.Success(profile)
    }
}
