package com.mirawuse.sportspot.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ApiKeyInterceptor(
    private val config: NetworkConfig,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!config.owns(request.url)) throw IOException("Request origin is not allowed")
        val builder = request.newBuilder().removeHeader("apikey")
        if (config.publishableKey.isNotEmpty()) builder.header("apikey", config.publishableKey)
        return chain.proceed(builder.build())
    }
}
