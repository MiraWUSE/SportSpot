package com.mirawuse.sportspot.data.remote.error

sealed interface NetworkError {
    data class Http(
        val status: Int,
        val serverCode: String? = null,
        val retryAfterSeconds: Long? = null,
    ) : NetworkError

    data object Timeout : NetworkError

    data object Offline : NetworkError

    data object Dns : NetworkError

    data object Connection : NetworkError

    data object Parsing : NetworkError

    data object InvalidRequest : NetworkError

    data object Unexpected : NetworkError
}
