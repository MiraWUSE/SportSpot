package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.local.account.FavoriteCache
import com.mirawuse.sportspot.data.local.account.KeystoreFavoriteCache
import com.mirawuse.sportspot.data.remote.api.FavoriteApi
import com.mirawuse.sportspot.data.repository.FavoriteRepositoryImpl
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FavoriteModule {
    @Binds
    abstract fun bindFavorites(implementation: FavoriteRepositoryImpl): FavoriteRepository

    @Binds
    @Singleton
    abstract fun bindCache(implementation: KeystoreFavoriteCache): FavoriteCache

    companion object {
        @Provides
        @Singleton
        fun provideFavoriteApi(retrofit: Retrofit): FavoriteApi = retrofit.create(FavoriteApi::class.java)
    }
}
