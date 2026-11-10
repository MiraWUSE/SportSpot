
package com.mirawuse.sportspot.domain.result

sealed interface DomainResult<out T> {
    data class Success<out T>(
        val value: T,
    ) : DomainResult<T>

    data class Failure(
        val error: DomainError,
    ) : DomainResult<Nothing>
}
