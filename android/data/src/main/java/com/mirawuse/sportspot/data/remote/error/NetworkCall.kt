package com.mirawuse.sportspot.data.remote.error

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.mirawuse.sportspot.data.mapper.SpotMappingException
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.io.Reader
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
            val serverCode = readServerCode(response.errorBody())
            val retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()?.takeIf { it >= 0 }
            return NetworkException(NetworkError.Http(response.code(), serverCode, retryAfterSeconds))
        }

        private fun readServerCode(body: ResponseBody?): String? {
            if (body == null) return null
            return try {
                body.use {
                    val text = readErrorText(it.charStream()) ?: return@use null
                    parseServerCode(text)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

        private fun readErrorText(reader: Reader): String? {
            // One extra character distinguishes an oversized body from one exactly at the limit.
            val buffer = CharArray(MAX_ERROR_BODY_CHARS + 1)
            var length = 0
            while (length < buffer.size) {
                val count = reader.read(buffer, length, buffer.size - length)
                if (count < 0) return String(buffer, 0, length)
                length += count
            }
            return null
        }

        private fun parseServerCode(text: String): String? {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return null
            val error = json["error"] as? JsonObject ?: json
            val code = error["code"] as? JsonPrimitive ?: return null
            return code.takeIf { it.isString }?.asString?.takeIf(SAFE_CODE::matches)
        }

        companion object {
            private const val MAX_ERROR_BODY_CHARS = 8192
            private val SAFE_CODE = Regex("[A-Z0-9_]{1,64}")
        }
    }
