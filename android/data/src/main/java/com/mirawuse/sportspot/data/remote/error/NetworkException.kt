package com.mirawuse.sportspot.data.remote.error

import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainFailure
import com.mirawuse.sportspot.domain.result.NetworkIssue
import java.io.IOException

/** The original cause is retained for diagnosis, but must not be written to network logs. */
class NetworkException(
    val error: NetworkError,
    cause: Throwable? = null,
) : IOException("Network request failed", cause),
    DomainFailure {
    override val domainError = error.toDomainError()
}

private fun NetworkError.toDomainError(): DomainError =
    when (this) {
        NetworkError.Offline -> DomainError.Network(NetworkIssue.OFFLINE)
        NetworkError.Dns, NetworkError.Connection -> DomainError.Network(NetworkIssue.CONNECTION)
        NetworkError.Timeout -> DomainError.Network(NetworkIssue.TIMEOUT)
        NetworkError.Parsing -> DomainError.Network(NetworkIssue.INVALID_RESPONSE)
        NetworkError.InvalidRequest -> DomainError.Validation("")
        NetworkError.Unexpected -> DomainError.Unexpected
        is NetworkError.Http ->
            when (status) {
                400, 422 -> DomainError.Validation("")
                401 -> DomainError.Unauthorized
                403 -> DomainError.Forbidden
                404 -> DomainError.NotFound
                409 -> DomainError.Conflict
                408 -> DomainError.Network(NetworkIssue.TIMEOUT)
                429 -> {
                    val now = System.currentTimeMillis()
                    val seconds = (retryAfterSeconds ?: 60L).coerceIn(0, (Long.MAX_VALUE - now) / 1000)
                    DomainError.Network(NetworkIssue.RATE_LIMITED, now + seconds * 1000)
                }
                in 500..599 -> DomainError.Network(NetworkIssue.SERVER)
                else -> DomainError.Unexpected
            }
    }
