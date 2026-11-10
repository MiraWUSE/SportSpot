
package com.mirawuse.sportspot.domain.policy

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.result.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorizationPolicyTest {
    @Test
    fun `гость не проходит проверку авторизации`() {
        val result = AuthorizationPolicy.authenticatedError(AuthState.Guest)

        assertEquals(DomainError.Unauthorized, result)
    }

    @Test
    fun `пользователь проходит проверку авторизации`() {
        val result =
            AuthorizationPolicy.authenticatedError(
                authenticated(UserRole.USER),
            )

        assertNull(result)
    }

    @Test
    fun `модератор проходит проверку авторизации`() {
        val result =
            AuthorizationPolicy.authenticatedError(
                authenticated(UserRole.MODERATOR),
            )

        assertNull(result)
    }

    @Test
    fun `гость не проходит проверку прав модератора`() {
        val result = AuthorizationPolicy.moderatorError(AuthState.Guest)

        assertEquals(DomainError.Unauthorized, result)
    }

    @Test
    fun `обычный пользователь получает Forbidden`() {
        val result =
            AuthorizationPolicy.moderatorError(
                authenticated(UserRole.USER),
            )

        assertEquals(DomainError.Forbidden, result)
    }

    @Test
    fun `модератор проходит проверку своих прав`() {
        val result =
            AuthorizationPolicy.moderatorError(
                authenticated(UserRole.MODERATOR),
            )

        assertNull(result)
    }

    private fun authenticated(role: UserRole): AuthState =
        AuthState.Authenticated(
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = role,
            ),
        )
}
