package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.local.account.AuthSessionStore
import com.mirawuse.sportspot.data.local.account.KeystoreAuthSessionStore
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.repository.AuthRepositoryImpl
import com.mirawuse.sportspot.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(implementation: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAuthSessionStore(implementation: KeystoreAuthSessionStore): AuthSessionStore

    companion object {
        @Provides
        @Singleton
        fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

        @Provides
        @Singleton
        fun provideAccessTokenProvider(repository: Provider<AuthRepositoryImpl>): AccessTokenProvider =
            AccessTokenProvider { repository.get().currentAccessToken() }
    }
}
