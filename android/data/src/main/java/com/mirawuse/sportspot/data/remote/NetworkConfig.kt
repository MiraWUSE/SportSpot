package com.mirawuse.sportspot.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class NetworkConfig(
    val baseUrl: HttpUrl = BASE_URL.toHttpUrl(),
    val publishableKey: String = "",
    val debugLogging: Boolean = false,
    val connectTimeoutMillis: Long = 10_000,
    val readTimeoutMillis: Long = 45_000,
    val writeTimeoutMillis: Long = 15_000,
    val callTimeoutMillis: Long = 60_000,
) {
    init {
        require(baseUrl.isHttps || (baseUrl.scheme == "http" && baseUrl.host in LOOPBACK_HOSTS)) {
            "HTTPS is required outside loopback tests"
        }
        require(baseUrl.encodedPath == "/" && baseUrl.query == null && baseUrl.fragment == null)
        require(baseUrl.username.isEmpty() && baseUrl.password.isEmpty())
        require(publishableKey.all { it.code in 33..126 }) { "Invalid API key configuration" }
        require(!publishableKey.startsWith("sb_secret_")) { "Secret keys are not supported" }
        require(listOf(connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis, callTimeoutMillis).all { it > 0 })
    }

    internal fun owns(url: HttpUrl): Boolean = url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port

    companion object {
        const val BASE_URL = "https://tnqkaamvvxwsimfydurg.supabase.co/"
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")
    }
}
