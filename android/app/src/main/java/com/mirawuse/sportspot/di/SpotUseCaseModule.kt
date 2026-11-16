package com.mirawuse.sportspot.di

import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SpotUseCaseModule {
    @Provides
    fun provideSearchNearbySpotsUseCase(repository: SpotRepository): SearchNearbySpotsUseCase = SearchNearbySpotsUseCase(repository)
}
