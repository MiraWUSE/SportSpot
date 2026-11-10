package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.repository.SpotRepositoryImpl
import com.mirawuse.sportspot.domain.repository.SpotRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpotRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSpotRepository(repository: SpotRepositoryImpl): SpotRepository
}
