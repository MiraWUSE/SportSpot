package com.mirawuse.sportspot.data.local.account

import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class EncryptedAccountFileTest {
    @Test
    fun ciphertextSurvivesReopeningAndLogoutRemovesIt() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val name = "account-file-test"
        val stored = EncryptedAccountFile(context, name)
        val plaintext = "private test session contents".encodeToByteArray()
        try {
            stored.write(plaintext)
            assertFalse(File(context.noBackupFilesDir, name).readBytes().decodeToString().contains(plaintext.decodeToString()))
            assertArrayEquals(plaintext, EncryptedAccountFile(context, name).read())
        } finally {
            stored.clear()
        }
        assertNull(stored.read())
    }

    @Test
    fun sessionStorePersistsProfileAndRotatedTokensAcrossRestarts() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val gson = NetworkClient.createGson()
        val session =
            AuthSession("test-access", "test-refresh", 123_000L, UserProfile("test-account", "Имя", "test@example.test", UserRole.USER))
        val store = KeystoreAuthSessionStore(context, gson)
        try {
            store.write(session)
            val restored = requireNotNull(KeystoreAuthSessionStore(context, gson).read())
            assertEquals(session.accessToken, restored.accessToken)
            assertEquals(session.refreshToken, restored.refreshToken)
            assertEquals(session.expiresAtMillis, restored.expiresAtMillis)
            assertEquals(session.profile, restored.profile)
        } finally {
            store.write(null)
        }
        assertNull(KeystoreAuthSessionStore(context, gson).read())
    }
}
