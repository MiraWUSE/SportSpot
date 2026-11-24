package com.mirawuse.sportspot.data.local.account

import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.api.FavoriteRow
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.KeyStore

class EncryptedAccountFileTest {
    @Test
    fun clearRemovesCiphertextAndKey() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val name = "account-file-test"
        val stored = EncryptedAccountFile(context, name)
        val ciphertext = File(context.noBackupFilesDir, name)
        val plaintext = "private test session contents".encodeToByteArray()
        try {
            stored.write(plaintext)
            assertTrue(ciphertext.isFile)
            assertTrue(hasKey(name))
            assertFalse(ciphertext.readBytes().decodeToString().contains(plaintext.decodeToString()))
            assertArrayEquals(plaintext, EncryptedAccountFile(context, name).read())
            stored.clear()
            assertFalse(ciphertext.exists())
            assertFalse("clear must remove the named Keystore key", hasKey(name))
            assertNull(EncryptedAccountFile(context, name).read())
        } finally {
            stored.clear()
        }
    }

    @Test
    fun reopenedSessionAfterLogoutIsEmptyAndNewLoginKeepsFavoritesSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val gson = NetworkClient.createGson()
        val authFile = "sportspot-auth-v1"
        val favoritesFile = "sportspot-favorites-v1"
        val session =
            AuthSession("test-access", "test-refresh", 123_000L, UserProfile("test-account", "Имя", "test@example.test", UserRole.USER))
        val store = KeystoreAuthSessionStore(context, gson)
        try {
            store.write(session)
            val favorites = KeystoreFavoriteCache(context, gson)
            favorites.write(session.profile.id, listOf(FavoriteRow(session.profile.id, "osm:node:1")))
            assertTrue(hasKey(authFile))
            assertTrue(hasKey(favoritesFile))
            assertSession(session, KeystoreAuthSessionStore(context, gson).read())

            store.write(null)
            val reopened = KeystoreAuthSessionStore(context, gson)
            assertNull(reopened.read())
            assertFalse(File(context.noBackupFilesDir, authFile).exists())
            assertFalse(hasKey(authFile))
            assertTrue(hasKey(favoritesFile))

            val next =
                AuthSession(
                    "new-access",
                    "new-refresh",
                    456_000L,
                    UserProfile("other-account", "Другое имя", "other@example.test", UserRole.USER),
                )
            reopened.write(next)
            assertTrue(hasKey(authFile))
            assertSession(next, KeystoreAuthSessionStore(context, gson).read())
            assertNull(favorites.read(next.profile.id))
            assertEquals("osm:node:1", favorites.read(session.profile.id)?.single()?.spotId)
        } finally {
            store.write(null)
            EncryptedAccountFile(context, favoritesFile).clear()
        }
    }

    @Test
    fun clearRevokesKeyEvenWhenTheBasePathCannotBeDeleted() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val name = "blocked-account-file-test"
        val stored = EncryptedAccountFile(context, name)
        val path = File(context.noBackupFilesDir, name)
        val blocker = File(path, "blocker")
        try {
            stored.write("test session".encodeToByteArray())
            assertTrue(hasKey(name))
            assertTrue(path.delete())
            assertTrue(path.mkdir())
            blocker.writeText("keep the directory non-empty")
            assertTrue(runCatching { stored.clear() }.exceptionOrNull() is IOException)
            assertFalse(hasKey(name))
        } finally {
            blocker.delete()
            stored.clear()
        }
    }

    private fun hasKey(name: String): Boolean =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("SportSpot.$name")

    private fun assertSession(
        expected: AuthSession,
        restored: AuthSession?,
    ) {
        val session = requireNotNull(restored)
        assertEquals(expected.accessToken, session.accessToken)
        assertEquals(expected.refreshToken, session.refreshToken)
        assertEquals(expected.expiresAtMillis, session.expiresAtMillis)
        assertEquals(expected.profile, session.profile)
    }
}
