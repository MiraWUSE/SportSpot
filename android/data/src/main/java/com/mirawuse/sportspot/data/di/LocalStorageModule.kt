package com.mirawuse.sportspot.data.di

import android.content.Context
import androidx.room.Room
import com.mirawuse.sportspot.data.local.db.MIGRATION_1_2
import com.mirawuse.sportspot.data.local.db.SportSpotDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalStorageModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): SportSpotDatabase =
        Room
            .databaseBuilder(context, SportSpotDatabase::class.java, "sportspot.db")
            .addMigrations(MIGRATION_1_2)
            .build()
}
