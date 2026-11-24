package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthFailure
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import java.util.UUID
import javax.inject.Inject

internal class ContentCall
    @Inject
    constructor(
        private val auth: AuthRepositoryImpl,
        private val network: NetworkCall,
    ) {
        suspend fun <T> execute(
            userId: String? = null,
            block: suspend (RequestSession) -> T,
        ): T =
            try {
                if (userId == null) {
                    network.execute { block(RequestSession(null)) }
                } else {
                    auth.withAccount(userId) { session -> network.execute { block(session) } }
                }
            } catch (failure: NetworkException) {
                throw DomainOperationException(failure.error.toDomainError())
            } catch (failure: AuthException) {
                throw DomainOperationException(
                    when (failure.failure) {
                        AuthFailure.SESSION_EXPIRED -> DomainError.Unauthorized
                        AuthFailure.OFFLINE, AuthFailure.RATE_LIMITED -> DomainError.Unavailable
                        else -> DomainError.Unexpected
                    },
                )
            }
    }

internal fun String.isCanonicalUuid(): Boolean = runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)

internal fun invalidContent(): Nothing = throw NetworkException(NetworkError.Parsing)

internal const val CONTENT_PAGE_SIZE = 100

private fun NetworkError.toDomainError(): DomainError =
    when (this) {
        is NetworkError.Http ->
            when (status) {
                400, 422 -> DomainError.Validation("Проверьте введённые данные")
                401 -> DomainError.Unauthorized
                403 -> DomainError.Forbidden
                404 -> DomainError.NotFound
                409 -> DomainError.Conflict
                408, 429, in 500..599 -> DomainError.Unavailable
                else -> DomainError.Unexpected
            }
        NetworkError.Offline, NetworkError.Timeout, NetworkError.Dns, NetworkError.Connection -> DomainError.Unavailable
        NetworkError.InvalidRequest -> DomainError.Validation("Проверьте введённые данные")
        else -> DomainError.Unexpected
    }
