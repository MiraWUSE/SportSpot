package com.mirawuse.sportspot.data.di

import android.util.Log
import com.google.gson.Gson
import com.mirawuse.sportspot.data.BuildConfig
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.NetworkConfig
import com.mirawuse.sportspot.data.remote.NetworkLogger
import com.mirawuse.sportspot.data.remote.api.SpotApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideConfig(): NetworkConfig =
        NetworkConfig(
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            debugLogging = BuildConfig.DEBUG,
        )

    @Provides
    @Singleton
    fun provideAccessTokenProvider(): AccessTokenProvider = AccessTokenProvider { null }

    @Provides
    @Singleton
    fun provideGson(): Gson = NetworkClient.createGson()

    @Provides
    @Singleton
    fun provideClient(
        config: NetworkConfig,
        accessTokenProvider: AccessTokenProvider,
    ): OkHttpClient = NetworkClient.createOkHttp(config, accessTokenProvider, NetworkLogger { Log.d("SportSpotHttp", it) })

    @Provides
    @Singleton
    fun provideRetrofit(
        config: NetworkConfig,
        client: OkHttpClient,
        gson: Gson,
    ): Retrofit = NetworkClient.createRetrofit(config, client, gson)

    @Provides
    @Singleton
    fun provideSpotApi(retrofit: Retrofit): SpotApi = retrofit.create(SpotApi::class.java)
}
