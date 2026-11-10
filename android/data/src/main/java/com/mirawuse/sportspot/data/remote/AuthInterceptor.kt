package com.mirawuse.sportspot.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(
    private val accessTokenProvider: AccessTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder().removeHeader("Authorization")
        val token = accessTokenProvider.getAccessToken()?.takeIf { it.isNotBlank() }
        if (token != null) {
            if (token.startsWith("sb_") || token.any { it.code !in 33..126 }) {
                throw IOException("Invalid session token configuration")
            }
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
