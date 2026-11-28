
package com.mirawuse.sportspot.domain.result

sealed interface DomainError {
    data object Unauthorized : DomainError

    data object Forbidden : DomainError

    data class Validation(
        val message: String,
    ) : DomainError

    data object NotFound : DomainError

    data object Conflict : DomainError

    data object Unavailable : DomainError

    data object Unexpected : DomainError

    data class Network(
        val issue: NetworkIssue,
        val retryAtMillis: Long = 0,
    ) : DomainError
}

enum class NetworkIssue { OFFLINE, CONNECTION, TIMEOUT, SERVER, RATE_LIMITED, INVALID_RESPONSE }

interface DomainFailure {
    val domainError: DomainError
}
