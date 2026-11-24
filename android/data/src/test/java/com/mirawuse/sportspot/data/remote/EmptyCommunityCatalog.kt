package com.mirawuse.sportspot.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

// Keep the legacy single-endpoint fixtures independent of unrelated catalog contents.
internal fun emptyCommunityCatalog(enabled: () -> Boolean) =
    Interceptor { chain ->
        val request = chain.request()
        if (enabled() && request.url.queryParameter("source") == "eq.community") {
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("[]".toResponseBody("application/json".toMediaType()))
                .build()
        } else {
            chain.proceed(request)
        }
    }
