package com.mirawuse.sportspot.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.EOFException
import java.lang.reflect.Type

/** Marks decoder failures at their boundary, without reclassifying socket IO as invalid JSON. */
class JsonConverterFactory(
    gson: Gson,
) : Converter.Factory() {
    private val delegate = GsonConverterFactory.create(gson)

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        val converter = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any> { body ->
            try {
                converter.convert(body)
            } catch (failure: Exception) {
                throw when (failure) {
                    is CancellationException -> failure
                    is MalformedJsonException, is EOFException, is IllegalStateException, is NumberFormatException ->
                        JsonParseException("Invalid JSON response", failure)
                    else -> failure
                }
            }
        }
    }
}
