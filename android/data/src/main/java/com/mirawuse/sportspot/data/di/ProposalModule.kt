package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.remote.api.ProposalApi
import com.mirawuse.sportspot.data.repository.SpotProposalRepositoryImpl
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProposalModule {
    @Binds
    abstract fun bindProposals(implementation: SpotProposalRepositoryImpl): SpotProposalRepository

    companion object {
        @Provides
        fun provideProposalApi(retrofit: Retrofit): ProposalApi = retrofit.create(ProposalApi::class.java)
    }
}
