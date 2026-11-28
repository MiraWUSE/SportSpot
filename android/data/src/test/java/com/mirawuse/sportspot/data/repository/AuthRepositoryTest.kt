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
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.domain.usecase.auth.SignUpUseCase
import com.mirawuse.sportspot.domain.usecase.user.UpdateMyProfileUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
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
    fun savesOnlyTheNameAndKeepsModeratorSessionAcrossRestoreAndLogin() =
        runBlocking {
            enqueueSession()
            enqueueProfile(role = "moderator")
            val previous = repository.signIn(EMAIL, PASSWORD)
            repeat(2) { next() }
            val session = checkNotNull(store.value)
            val editedName = "Имя после изменения"
            enqueue(profileNameResponse(editedName))

            val outcome = UpdateMyProfileUseCase(repository, repository)("  $editedName  ", UID)
            val updated = previous.copy(name = editedName)
            assertEquals(DomainResult.Success(updated), outcome)
            val patch = next()
            assertEquals("PATCH", patch.method)
            assertEquals("/rest/v1/profiles", patch.requestUrl?.encodedPath)
            assertEquals("id,name", patch.requestUrl?.queryParameter("select"))
            assertEquals("eq.$UID", patch.requestUrl?.queryParameter("id"))
            assertEquals("return=representation", patch.getHeader("Prefer"))
            assertEquals("Bearer access-one", patch.getHeader("Authorization"))
            val payload = JsonParser.parseString(patch.body.readUtf8()).asJsonObject
            assertEquals(setOf("name"), payload.keySet())
            assertEquals(editedName, payload["name"].asString)
            val stored = checkNotNull(store.value)
            assertEquals(updated, stored.profile)
            assertEquals(session.accessToken, stored.accessToken)
            assertEquals(session.refreshToken, stored.refreshToken)
            assertEquals(session.expiresAtMillis, stored.expiresAtMillis)
            assertEquals(UserRole.MODERATOR, updated.role)
            assertEquals(AuthState.Authenticated(updated), repository.authState.value)
            assertEquals(AuthState.Authenticated(updated), newRepository().getAuthState())
            assertTrue(logs.none { editedName in it || UID in it || EMAIL in it || "access-one" in it })

            enqueue("{}", 204)
            repository.signOut()
            enqueueSession()
            enqueueProfile(name = editedName, role = "moderator")
            assertEquals(updated, repository.signIn(EMAIL, PASSWORD))
        }

    @Test
    fun rejectsWrongOwnerAndFailedOrUnverifiedPatchWithoutChangingTheSession() =
        runBlocking {
            signInAndDrain()
            val session = checkNotNull(store.value)
            assertEquals(DomainError.Unauthorized, contentFailure { repository.updateMyProfile(OTHER_UID, "Новое имя") })
            assertEquals(2, server.requestCount)
            for ((body, expected) in listOf(
                "[]" to DomainError.NotFound,
                profileNameResponse("Новое имя", OTHER_UID) to DomainError.Network(NetworkIssue.INVALID_RESPONSE),
                profileNameResponse("Другое имя") to DomainError.Network(NetworkIssue.INVALID_RESPONSE),
                profileNameResponse(" ") to DomainError.Network(NetworkIssue.INVALID_RESPONSE),
            )) {
                enqueue(body)
                assertEquals(expected, contentFailure { repository.updateMyProfile(UID, "Новое имя") })
                assertEquals(session, store.value)
            }
            enqueue("{}", 403)
            assertEquals(DomainError.Forbidden, contentFailure { repository.updateMyProfile(UID, "Новое имя") })
            enqueue("{}", 503)
            assertEquals(
                DomainError.Network(NetworkIssue.SERVER),
                contentFailure { repository.updateMyProfile(UID, "Новое имя") },
            )
            online = false
            assertEquals(
                DomainError.Network(NetworkIssue.OFFLINE),
                contentFailure { repository.updateMyProfile(UID, "Новое имя") },
            )
            assertEquals(session, store.value)
            assertEquals(AuthState.Authenticated(session.profile), repository.authState.value)
        }

    @Test
    fun expiredAccessTokenRefreshesTheSessionWithoutReplayingThePatch() =
        runBlocking {
            signInAndDrain()
            enqueue("{}", 401)
            enqueueSession(access = "access-two", refresh = "refresh-two")
            enqueue(profileNameResponse("Новое имя"))

            assertEquals(DomainError.Unauthorized, contentFailure { repository.updateMyProfile(UID, "Новое имя") })
            assertEquals("Bearer access-one", next().getHeader("Authorization"))
            assertEquals("refresh_token", next().requestUrl?.queryParameter("grant_type"))
            assertEquals(4, server.requestCount)
            assertEquals("Новое имя", repository.updateMyProfile(UID, "Новое имя").name)
            assertEquals("Bearer access-two", next().getHeader("Authorization"))
            assertEquals("refresh-two", store.value?.refreshToken)
            assertEquals("access-two", store.value?.accessToken)
        }

    @Test
    fun aPatchCompletingAfterLogoutAndAnotherLoginCannotOverwriteTheNewSession() =
        runBlocking {
            signInAndDrain()
            val resumePatch = CountDownLatch(1)
            val otherEmail = "another@example.test"
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when {
                            request.method == "PATCH" -> {
                                if (resumePatch.await(8, TimeUnit.SECONDS)) {
                                    jsonResponse(profileNameResponse("Новое имя"))
                                } else {
                                    MockResponse().setResponseCode(500)
                                }
                            }
                            request.path?.startsWith("/auth/v1/logout") == true -> MockResponse().setResponseCode(204)
                            request.path?.startsWith("/auth/v1/token") == true ->
                                jsonResponse(
                                    """{"access_token":"bob-access","refresh_token":"bob-refresh","expires_in":3600,
                                      "user":{"id":"$OTHER_UID","email":"$otherEmail"}}""",
                                )
                            request.path?.startsWith("/rest/v1/profiles") == true ->
                                jsonResponse("""[{"id":"$OTHER_UID","name":"Боб","role":"user"}]""")
                            else -> MockResponse().setResponseCode(404)
                        }
                }
            try {
                val pending =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        contentFailure { repository.updateMyProfile(UID, "Новое имя") }
                    }
                assertEquals("PATCH", next().method)
                repository.signOut()
                assertEquals(AuthState.Guest, repository.authState.value)
                val bob = repository.signIn(otherEmail, PASSWORD)
                val newSession = store.value
                resumePatch.countDown()
                assertEquals(DomainError.Unauthorized, pending.await())
                assertEquals("Боб", bob.name)
                assertEquals(AuthState.Authenticated(bob), repository.authState.value)
                assertEquals(newSession, store.value)
            } finally {
                resumePatch.countDown()
            }
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

    private fun enqueueProfile(
        uid: String = UID,
        name: String = "Имя из profiles",
        role: String = "user",
    ) = enqueue("""[{"id":"$uid","name":"$name","role":"$role"}]""")

    private fun profileNameResponse(
        name: String,
        uid: String = UID,
    ) = """[{"id":"$uid","name":"$name"}]"""

    private fun enqueue(
        json: String,
        code: Int = 200,
    ) {
        server.enqueue(jsonResponse(json, code))
    }

    private fun jsonResponse(
        json: String,
        code: Int = 200,
    ) = MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(json)

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
