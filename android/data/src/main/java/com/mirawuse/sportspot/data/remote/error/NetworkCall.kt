package com.mirawuse.sportspot.data.remote.error

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.mirawuse.sportspot.data.mapper.SpotMappingException
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.inject.Inject

class NetworkCall
    @Inject
    constructor(
        private val monitor: NetworkMonitor,
        private val gson: Gson,
    ) {
        suspend fun <T> execute(block: suspend () -> T): T {
            currentCoroutineContext().ensureActive()
            try {
                if (!monitor.isOnline()) throw NetworkException(NetworkError.Offline)
                val result = block()
                currentCoroutineContext().ensureActive()
                return result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                throw when (failure) {
                    is NetworkException -> failure
                    is SpotMappingException, is JsonParseException -> NetworkException(NetworkError.Parsing, failure)
                    is HttpException -> failure.response()?.let(::httpFailure) ?: NetworkException(NetworkError.Unexpected, failure)
                    is InterruptedIOException -> NetworkException(NetworkError.Timeout, failure)
                    is UnknownHostException -> NetworkException(NetworkError.Dns, failure)
                    is IOException -> NetworkException(NetworkError.Connection, failure)
                    is IllegalArgumentException -> NetworkException(NetworkError.InvalidRequest, failure)
                    else -> NetworkException(NetworkError.Unexpected, failure)
                }
            }
        }

        fun <T : Any> body(response: Response<T>): T {
            if (!response.isSuccessful) throw httpFailure(response)
            return response.body() ?: throw NetworkException(NetworkError.Parsing)
        }

        private fun httpFailure(response: Response<*>): NetworkException {
            val code =
                try {
                    response.errorBody()?.use { body ->
                        val reader = body.charStream()
                        val buffer = CharArray(8193)
                        var length = 0
                        while (length < buffer.size) {
                            val count = reader.read(buffer, length, buffer.size - length)
                            if (count < 0) break
                            length += count
                        }
                        if (length == buffer.size) return@use null
                        val text = String(buffer, 0, length)
                        val json = gson.fromJson(text, JsonObject::class.java)
                        val nested = json?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
                        val raw = (nested ?: json)?.get("code")
                        raw
                            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                            ?.asString
                            ?.takeIf { it.matches(SAFE_CODE) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            val retryAfter = response.headers()["Retry-After"]?.toLongOrNull()?.takeIf { it >= 0 }
            return NetworkException(NetworkError.Http(response.code(), code, retryAfter))
        }

        companion object {
            private val SAFE_CODE = Regex("[A-Z0-9_]{1,64}")
        }
    }
