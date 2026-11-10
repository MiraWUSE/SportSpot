package com.mirawuse.sportspot.data.remote.error

import java.io.IOException

/** The original cause is retained for diagnosis, but must not be written to network logs. */
class NetworkException(
    val error: NetworkError,
    cause: Throwable? = null,
) : IOException("Network request failed", cause)
