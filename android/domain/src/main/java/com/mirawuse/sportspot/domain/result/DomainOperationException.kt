package com.mirawuse.sportspot.domain.result

class DomainOperationException(
    val error: DomainError,
) : RuntimeException(),
    DomainFailure {
    override val domainError: DomainError get() = error
}

internal suspend fun <T> contentResult(block: suspend () -> T): DomainResult<T> =
    try {
        DomainResult.Success(block())
    } catch (failure: DomainOperationException) {
        DomainResult.Failure(failure.error)
    }
