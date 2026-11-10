
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
}
