package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.remote.connectivity.AndroidNetworkMonitor
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(monitor: AndroidNetworkMonitor): NetworkMonitor
}
