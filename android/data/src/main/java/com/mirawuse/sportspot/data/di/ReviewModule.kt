package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.remote.api.ReviewApi
import com.mirawuse.sportspot.data.repository.ReviewRepositoryImpl
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReviewModule {
    @Binds
    abstract fun bindReviews(implementation: ReviewRepositoryImpl): ReviewRepository

    companion object {
        @Provides
        fun provideReviewApi(retrofit: Retrofit): ReviewApi = retrofit.create(ReviewApi::class.java)
    }
}
