package com.mirawuse.sportspot.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

fun interface NetworkLogger {
    fun log(message: String)
}

class SafeLoggingInterceptor(
    private val enabled: Boolean,
    private val logger: NetworkLogger,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())
        val request = chain.request()
        val path = request.url.encodedPath.takeIf { it in PUBLIC_PATHS } ?: "/[redacted]"
        val start = System.nanoTime()
        var status = "failed"
        try {
            return chain.proceed(request).also { status = it.code.toString() }
        } finally {
            val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            logger.log("${request.method} $path -> $status (${duration}ms)")
        }
    }

    companion object {
        private val PUBLIC_PATHS = setOf("/rest/v1/spots", "/functions/v1/search-sports-spots-v1")
    }
}
