package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonObject
import com.mirawuse.sportspot.data.local.account.AuthSession
import com.mirawuse.sportspot.data.local.account.AuthSessionStore
import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.remote.api.AuthResponse
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthFailure
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AuthRepositoryImpl
    @Inject
    constructor(
        private val api: AuthApi,
        private val network: NetworkCall,
        private val store: AuthSessionStore,
    ) : AuthRepository {
        private val mutex = Mutex()
        private val mutableState = MutableStateFlow<AuthState>(AuthState.Guest)
        override val authState = mutableState.asStateFlow()

        @Volatile
        private var session: AuthSession? = null
        private var restored = false

        fun currentAccessToken(): String? = session?.takeIf { it.expiresAtMillis > store.nowMillis() }?.accessToken

        override suspend fun getAuthState(): AuthState =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    restore()
                    try {
                        session?.let { refreshIfNeeded(it) }
                    } catch (_: AuthException) {
                        // Temporary failure still allows the account's own offline data.
                    }
                    mutableState.value
                }
            }

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    restore()
                    val response =
                        authCall {
                            network.body(api.token("password", emailCredentials(email, password), RequestSession(null)))
                        }
                    establishSession(response)
                }
            }

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    restore()
                    val body =
                        emailCredentials(email, password).apply {
                            add("data", JsonObject().apply { addProperty("name", name) })
                        }
                    val response = authCall { network.body(api.signUp(body, RequestSession(null))) }
                    if (response.accessToken == null && response.refreshToken == null && canonicalUid(response.id) != null) {
                        return@withLock
                    }
                    establishSession(response)
                }
            }
        }

        override suspend fun signOut() {
            withContext(Dispatchers.IO) {
                val oldSession =
                    mutex.withLock {
                        restore()
                        val current = session
                        withContext(NonCancellable) { save(null) }
                        current
                    }
                if (oldSession != null) {
                    try {
                        withTimeoutOrNull(2500) {
                            network.execute { api.signOut(RequestSession(oldSession.accessToken)).errorBody()?.close() }
                        }
                    } catch (_: NetworkException) {
                        // The local session has already been cleared.
                    }
                }
            }
        }

        private fun restore() {
            if (!restored) {
                publish(store.read())
                restored = true
            }
        }

        private suspend fun establishSession(response: AuthResponse): UserProfile {
            val uid = canonicalUid(response.user?.id) ?: unavailable()
            val email = response.user?.email?.takeIf { it.isNotBlank() } ?: unavailable()
            val expiresAt = expiresAt(response)
            val accessToken = validToken(response.accessToken)
            val refreshToken = validToken(response.refreshToken)
            val rows = authCall { network.body(api.profile("eq.$uid", RequestSession(accessToken))) }
            val row = rows.singleOrNull()?.takeIf { it.id == uid } ?: unavailable()
            val name = row.name?.takeIf { it.isNotBlank() } ?: unavailable()
            val role =
                when (row.role) {
                    "user" -> UserRole.USER
                    "moderator" -> UserRole.MODERATOR
                    else -> unavailable()
                }
            val profile = UserProfile(uid, name, email, role)
            save(AuthSession(accessToken, refreshToken, expiresAt, profile))
            return profile
        }

        private suspend fun refreshIfNeeded(current: AuthSession): AuthSession =
            if (current.expiresAtMillis - store.nowMillis() > REFRESH_MARGIN_MILLIS) current else refresh(current)

        private suspend fun refresh(current: AuthSession): AuthSession {
            val response =
                try {
                    network.execute {
                        val body = JsonObject().apply { addProperty("refresh_token", current.refreshToken) }
                        network.body(api.token("refresh_token", body, RequestSession(null)))
                    }
                } catch (error: NetworkException) {
                    val http = error.error as? NetworkError.Http
                    if (http != null && (http.status == 401 || http.serverCode in INVALID_SESSIONS)) {
                        save(null)
                        throw AuthException(AuthFailure.SESSION_EXPIRED)
                    }
                    throw AuthException(error.error.authFailure())
                }
            val uid = canonicalUid(response.user?.id)
            if (uid != current.profile.id) unavailable()
            val profile = current.profile.copy(email = response.user?.email?.takeIf { it.isNotBlank() } ?: unavailable())
            return AuthSession(validToken(response.accessToken), validToken(response.refreshToken), expiresAt(response), profile)
                .also(::save)
        }

        private fun publish(session: AuthSession?) {
            this.session = session
            mutableState.value = session?.let { AuthState.Authenticated(it.profile) } ?: AuthState.Guest
        }

        private fun save(session: AuthSession?) {
            try {
                store.write(session)
            } catch (_: Exception) {
                if (session == null) publish(null)
                unavailable()
            }
            publish(session)
        }

        private suspend fun <T> authCall(block: suspend () -> T): T =
            try {
                network.execute(block)
            } catch (error: NetworkException) {
                throw AuthException(error.error.authFailure())
            }

        private fun expiresAt(response: AuthResponse): Long {
            val seconds = response.expiresIn?.takeIf { it in 1L..604800L } ?: unavailable()
            return store.nowMillis() + seconds * 1000
        }

        private fun validToken(value: String?): String =
            value?.takeIf { it.isNotEmpty() && it.length <= 8192 && !it.startsWith("sb_") && it.all { char -> char.code in 33..126 } }
                ?: unavailable()

        private fun canonicalUid(value: String?): String? =
            try {
                value?.takeIf { UUID.fromString(it).toString() == it }
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun emailCredentials(
            email: String,
            password: String,
        ): JsonObject =
            JsonObject().apply {
                addProperty("email", email)
                addProperty("password", password)
            }

        private fun unavailable(): Nothing = throw AuthException(AuthFailure.UNAVAILABLE)

        private companion object {
            const val REFRESH_MARGIN_MILLIS = 30_000
            val INVALID_SESSIONS = setOf("REFRESH_TOKEN_NOT_FOUND", "REFRESH_TOKEN_ALREADY_USED", "SESSION_NOT_FOUND", "SESSION_EXPIRED")
        }
    }

private fun NetworkError.authFailure(): AuthFailure =
    when (this) {
        NetworkError.Offline, NetworkError.Timeout, NetworkError.Dns, NetworkError.Connection -> AuthFailure.OFFLINE
        is NetworkError.Http ->
            when {
                serverCode == "INVALID_CREDENTIALS" -> AuthFailure.INVALID_CREDENTIALS
                serverCode == "EMAIL_NOT_CONFIRMED" -> AuthFailure.EMAIL_UNCONFIRMED
                serverCode == "WEAK_PASSWORD" -> AuthFailure.WEAK_PASSWORD
                status == 429 -> AuthFailure.RATE_LIMITED
                else -> AuthFailure.UNAVAILABLE
            }
        else -> AuthFailure.UNAVAILABLE
    }
