package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthFailure
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.domain.result.NetworkIssue
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
            retryUnauthorized: Boolean = true,
            block: suspend (RequestSession) -> T,
        ): T =
            contentRequest {
                if (userId == null) {
                    network.execute { block(RequestSession(null)) }
                } else {
                    auth.withAccount(userId, retryUnauthorized) { session -> network.execute { block(session) } }
                }
            }
    }

internal suspend fun <T> contentRequest(block: suspend () -> T): T =
    try {
        block()
    } catch (failure: NetworkException) {
        throw DomainOperationException(failure.domainError)
    } catch (failure: AuthException) {
        throw DomainOperationException(
            failure.detail ?: when (failure.failure) {
                AuthFailure.SESSION_EXPIRED -> DomainError.Unauthorized
                AuthFailure.OFFLINE -> DomainError.Network(NetworkIssue.OFFLINE)
                AuthFailure.RATE_LIMITED -> DomainError.Network(NetworkIssue.RATE_LIMITED)
                else -> DomainError.Unexpected
            },
        )
    }

internal fun String.isCanonicalUuid(): Boolean = runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)

internal fun invalidContent(): Nothing = throw NetworkException(NetworkError.Parsing)

internal const val CONTENT_PAGE_SIZE = 100
