package com.mirawuse.sportspot.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkClient {
    fun createGson(): Gson = GsonBuilder().setStrictness(Strictness.STRICT).disableJdkUnsafe().create()

    fun createOkHttp(
        config: NetworkConfig,
        accessTokenProvider: AccessTokenProvider,
        logger: NetworkLogger = NetworkLogger {},
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(config.callTimeoutMillis, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addInterceptor(ApiKeyInterceptor(config))
            .addInterceptor(AuthInterceptor(accessTokenProvider))
            .addInterceptor(SafeLoggingInterceptor(config.debugLogging, logger))
            .build()

    fun createRetrofit(
        config: NetworkConfig,
        client: OkHttpClient,
        gson: Gson = createGson(),
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(JsonConverterFactory(gson))
            .build()
}
