package com.mirawuse.sportspot.data.local.account

import android.content.Context
import com.google.gson.Gson
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// A regular class prevents accidental token exposure via data-class toString.
internal class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val profile: UserProfile,
)

internal interface AuthSessionStore {
    fun read(): AuthSession?

    fun write(session: AuthSession?)

    fun nowMillis(): Long = System.currentTimeMillis()
}

internal class KeystoreAuthSessionStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val gson: Gson,
    ) : AuthSessionStore {
        private val file = EncryptedAccountFile(context, "sportspot-auth-v1")

        override fun read(): AuthSession? =
            try {
                file.read()?.let { gson.fromJson(it.decodeToString(), Record::class.java)?.toSession() }
            } catch (_: Exception) {
                null
            }

        override fun write(session: AuthSession?) {
            if (session == null) {
                file.clear()
            } else {
                val record =
                    Record(
                        session.accessToken,
                        session.refreshToken,
                        session.expiresAtMillis,
                        session.profile.id,
                        session.profile.name,
                        session.profile.email,
                        session.profile.role,
                    )
                file.write(gson.toJson(record).encodeToByteArray())
            }
        }

        private class Record(
            val accessToken: String? = null,
            val refreshToken: String? = null,
            val expiresAtMillis: Long? = null,
            val userId: String? = null,
            val name: String? = null,
            val email: String? = null,
            val role: UserRole? = null,
        ) {
            fun toSession(): AuthSession =
                AuthSession(
                    requireNotNull(accessToken),
                    requireNotNull(refreshToken),
                    requireNotNull(expiresAtMillis),
                    UserProfile(requireNotNull(userId), requireNotNull(name), requireNotNull(email), requireNotNull(role)),
                )
        }
    }
