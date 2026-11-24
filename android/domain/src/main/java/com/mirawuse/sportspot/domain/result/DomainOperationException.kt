package com.mirawuse.sportspot.domain.result

class DomainOperationException(
    val error: DomainError,
) : RuntimeException()

internal suspend fun <T> contentResult(block: suspend () -> T): DomainResult<T> =
    try {
        DomainResult.Success(block())
    } catch (failure: DomainOperationException) {
        DomainResult.Failure(failure.error)
    }
