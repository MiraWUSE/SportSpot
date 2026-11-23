package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AuthRepository {
    val authState: Flow<AuthState>
        get() = flow { emit(getAuthState()) }

    suspend fun getAuthState(): AuthState

    suspend fun signIn(
        email: String,
        password: String,
    ): UserProfile

    suspend fun signUp(
        name: String,
        email: String,
        password: String,
    )

    suspend fun signOut()
}
