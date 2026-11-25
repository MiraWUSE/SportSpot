package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.user.UserProfile

interface UserRepository {
    suspend fun getMyProfile(): UserProfile

    suspend fun updateMyProfile(
        userId: String,
        name: String,
    ): UserProfile
}
