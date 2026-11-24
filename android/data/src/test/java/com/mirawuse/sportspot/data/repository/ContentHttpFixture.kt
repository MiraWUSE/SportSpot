package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.local.account.AuthSession
import com.mirawuse.sportspot.data.local.account.AuthSessionStore
import com.mirawuse.sportspot.data.remote.AccessTokenProvider
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.NetworkConfig
import com.mirawuse.sportspot.data.remote.api.AuthApi
import com.mirawuse.sportspot.data.remote.connectivity.NetworkMonitor
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.result.DomainOperationException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert
import java.util.concurrent.TimeUnit

internal const val ALICE_ID = "11111111-1111-4111-8111-111111111111"
internal const val BOB_ID = "22222222-2222-4222-8222-222222222222"
internal const val ITEM_ID = "33333333-3333-4333-8333-333333333333"

internal class ContentHttpFixture(
    private val server: MockWebServer,
    user: String? = ALICE_ID,
) {
    var online = true
    private val gson = NetworkClient.createGson()
    val network = NetworkCall(NetworkMonitor { online }, gson)
    val retrofit by lazy {
        val config = NetworkConfig(server.url("/"), "fixture-publishable-key")
        NetworkClient.createRetrofit(config, NetworkClient.createOkHttp(config, AccessTokenProvider { "unrelated-token" }), gson)
    }
    val auth by lazy { AuthRepositoryImpl(retrofit.create(AuthApi::class.java), network, store) }
    val call by lazy { ContentCall(auth, network) }
    private val store =
        object : AuthSessionStore {
            var value =
                user?.let {
                    AuthSession("access-$it", "refresh-$it", 3_600_000L, UserProfile(it, "Fixture", "a@example.test", UserRole.USER))
                }

            override fun read() = value

            override fun write(session: AuthSession?) {
                value = session
            }

            override fun nowMillis() = 1000L
        }

    fun enqueue(
        json: String,
        status: Int = 200,
    ) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
    }

    fun next() = checkNotNull(server.takeRequest(3, TimeUnit.SECONDS))
}

internal suspend fun contentFailure(block: suspend () -> Any?) =
    try {
        block()
        Assert.fail("Expected content operation to fail")
        error("Unreachable")
    } catch (failure: DomainOperationException) {
        failure.error
    }
