package com.mirawuse.sportspot.data.repository

import com.google.gson.JsonParser
import com.mirawuse.sportspot.data.local.account.AuthSession
import com.mirawuse.sportspot.data.local.account.AuthSessionStore
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.NetworkConfig
import com.mirawuse.sportspot.data.remote.NetworkLogger
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthFailure
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.auth.SignUpUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class AuthRepositoryTest {
    @get:Rule val server = MockWebServer()
    private val gson = NetworkClient.createGson()
    private val store = MemorySessionStore()
    private var online = true
    private val logs = mutableListOf<String>()
    private val config by lazy { NetworkConfig(server.url("/"), "test-publishable-key", debugLogging = true) }
    private val client by lazy { NetworkClient.createOkHttp(config, AccessTokenProvider { "old-account-token" }, NetworkLogger(logs::add)) }
    private val api by lazy { NetworkClient.createRetrofit(config, client, gson).create(AuthApi::class.java) }
    private val repository by lazy { newRepository() }

    @Test
    fun registrationSendsOnlyNameAndWaitsForConfirmation() =
        runBlocking {
            val useCase = SignUpUseCase(repository)
            assertTrue(useCase(" ", EMAIL, PASSWORD) is DomainResult.Failure)
            assertEquals(0, server.requestCount)
            enqueue("""{"id":"$UID","email":"$EMAIL"}""")
            assertTrue(useCase(" Имя ", " $EMAIL ", PASSWORD) is DomainResult.Success)
            assertEquals(AuthState.Guest, repository.getAuthState())
            assertNull(store.value)

            val request = next()
            assertEquals("/auth/v1/signup", request.path)
            assertNull(request.getHeader("Authorization"))
            assertEquals("test-publishable-key", request.getHeader("apikey"))
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(PASSWORD, body["password"].asString)
            assertEquals(setOf("name"), body.getAsJsonObject("data").keySet())
            assertEquals("Имя", body.getAsJsonObject("data")["name"].asString)
            assertTrue(logs.all { "signup" !in it && PASSWORD !in it && EMAIL !in it })
        }

    @Test
    fun signInAndImmediateSignupUseAuthEmailAndOwnProfile() =
        runBlocking {
            enqueueSession()
            enqueueProfile()
            val signedIn = repository.signIn(EMAIL, PASSWORD)
            assertEquals(EMAIL, signedIn.email)
            assertEquals("Имя из profiles", signedIn.name)
            assertEquals(UserRole.USER, signedIn.role)
            assertEquals("password", next().requestUrl?.queryParameter("grant_type"))
            val profile = next()
            assertEquals("eq.$UID", profile.requestUrl?.queryParameter("id"))
            assertEquals("Bearer access-one", profile.getHeader("Authorization"))
            assertEquals(AuthState.Authenticated(signedIn), newRepository().getAuthState())
            assertEquals(2, server.requestCount)

            enqueueSession()
            enqueueProfile()
            repository.signUp("Имя", EMAIL, PASSWORD)
            assertEquals(AuthState.Authenticated(signedIn), repository.getAuthState())
            assertTrue(logs.none { "access-one" in it || "refresh-one" in it || EMAIL in it || PASSWORD in it })
        }

    @Test
    fun credentialsAndRateLimitsAreReportedWithoutServerMessages() =
        runBlocking {
            val errors =
                listOf(
                    Triple(400, "invalid_credentials", AuthFailure.INVALID_CREDENTIALS),
                    Triple(400, "email_not_confirmed", AuthFailure.EMAIL_UNCONFIRMED),
                    Triple(422, "weak_password", AuthFailure.WEAK_PASSWORD),
                    Triple(429, "over_request_rate_limit", AuthFailure.RATE_LIMITED),
                    Triple(503, "unexpected_failure", AuthFailure.UNAVAILABLE),
                )
            for ((status, code, expected) in errors) {
                enqueue("""{"error_code":"$code","msg":"private response contents"}""", status)
                assertEquals(expected, failureOf { repository.signIn(EMAIL, PASSWORD) })
            }
            online = false
            assertEquals(AuthFailure.OFFLINE, failureOf { repository.signIn(EMAIL, PASSWORD) })
            assertEquals(5, server.requestCount)
            assertNull(store.value)
        }

    @Test
    fun concurrentRefreshRotatesTokensAndPersistsBeforeReusingTheSession() =
        runBlocking {
            signInAndDrain()
            store.clock += 3600_000L
            enqueueSession(access = "access-two", refresh = "refresh-two")
            val repo = newRepository()
            val states = List(8) { async { repo.getAuthState() } }.awaitAll()
            assertTrue(states.all { it is AuthState.Authenticated && it.profile.id == UID })
            val request = next()
            assertEquals("refresh_token", request.requestUrl?.queryParameter("grant_type"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("refresh-one", JsonParser.parseString(request.body.readUtf8()).asJsonObject["refresh_token"].asString)
            assertEquals(3, server.requestCount)
            assertEquals("refresh-two", store.value?.refreshToken)
            assertEquals("access-two", repo.currentAccessToken())
        }

    @Test
    fun temporaryRefreshFailureKeepsOfflineAccountButRevocationClearsIt() =
        runBlocking {
            signInAndDrain()
            store.clock += 3600_000L
            online = false
            assertTrue(repository.getAuthState() is AuthState.Authenticated)
            assertNull(repository.currentAccessToken())
            assertEquals("refresh-one", store.value?.refreshToken)
            online = true
            store.clock += 2_000L
            enqueue("""{"error_code":"refresh_token_not_found"}""", 400)
            assertEquals(AuthState.Guest, repository.getAuthState())
            assertNull(store.value)
            assertEquals(AuthState.Guest, newRepository().getAuthState())
        }

    @Test
    fun logoutIsLocalEvenOfflineAndAnotherAccountRestoresOnlyItsProfile() =
        runBlocking {
            signInAndDrain()
            online = false
            repository.signOut()
            assertNull(repository.currentAccessToken())
            assertEquals(AuthState.Guest, newRepository().getAuthState())
            assertEquals(2, server.requestCount)
            online = true
            enqueueSession(uid = OTHER_UID, email = "another@example.test")
            enqueueProfile(OTHER_UID)
            assertEquals(OTHER_UID, repository.signIn("another@example.test", PASSWORD).id)
            server.enqueue(MockResponse().setResponseCode(204))
            repository.signOut()
            assertEquals("/auth/v1/logout?scope=local", List(3) { next() }.last().path)
            assertEquals(AuthState.Guest, repository.getAuthState())
        }

    @Test
    fun logoutClearsTheActiveAccountEvenWhenStorageReportsAnError() =
        runBlocking {
            signInAndDrain()
            store.failOnClear = true

            assertEquals(AuthFailure.UNAVAILABLE, failureOf { repository.signOut() })
            assertEquals(AuthState.Guest, repository.getAuthState())
            assertNull(repository.currentAccessToken())
        }

    private fun newRepository() = AuthRepositoryImpl(api, NetworkCall(NetworkMonitor { online }, gson), store)

    private suspend fun signInAndDrain() {
        enqueueSession()
        enqueueProfile()
        repository.signIn(EMAIL, PASSWORD)
        repeat(2) { next() }
    }

    private suspend fun failureOf(block: suspend () -> Any?): AuthFailure {
        try {
            block()
        } catch (failure: AuthException) {
            return failure.failure
        }
        error("Expected a controlled auth error")
    }

    private fun enqueueSession(
        access: String = "access-one",
        refresh: String = "refresh-one",
        uid: String = UID,
        email: String = EMAIL,
    ) = enqueue("""{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"user":{"id":"$uid","email":"$email"}}""")

    private fun enqueueProfile(uid: String = UID) = enqueue("""[{"id":"$uid","name":"Имя из profiles","role":"user"}]""")

    private fun enqueue(
        json: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(json))
    }

    private fun next(): RecordedRequest = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private class MemorySessionStore : AuthSessionStore {
        var value: AuthSession? = null
        var clock: Long = 100_000
        var failOnClear = false

        override fun read() = value

        override fun write(session: AuthSession?) {
            if (session == null && failOnClear) throw IOException("Test cleanup failure")
            value = session
        }

        override fun nowMillis() = clock
    }

    private companion object {
        const val UID = "11111111-1111-4111-8111-111111111111"
        const val OTHER_UID = "22222222-2222-4222-8222-222222222222"
        const val EMAIL = "person@example.test"
        const val PASSWORD = "test-password"
    }
}
