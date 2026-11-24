package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.remote.api.UserPhotoApi
import com.mirawuse.sportspot.data.repository.UserPhotoRepositoryImpl
import com.mirawuse.sportspot.domain.repository.UserPhotoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UserPhotoModule {
    @Binds
    abstract fun bindUserPhotos(implementation: UserPhotoRepositoryImpl): UserPhotoRepository

    companion object {
        @Provides
        fun provideUserPhotoApi(retrofit: Retrofit): UserPhotoApi = retrofit.create(UserPhotoApi::class.java)
    }
}
