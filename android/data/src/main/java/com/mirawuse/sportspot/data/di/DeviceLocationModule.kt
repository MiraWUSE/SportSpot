package com.mirawuse.sportspot.data.di

import com.mirawuse.sportspot.data.hardware.AndroidDeviceLocationProvider
import com.mirawuse.sportspot.domain.repository.DeviceLocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceLocationModule {
    @Binds
    @Singleton
    abstract fun bindDeviceLocationProvider(provider: AndroidDeviceLocationProvider): DeviceLocationProvider
}
