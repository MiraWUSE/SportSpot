package com.mirawuse.sportspot.data.local.account

import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.api.FavoriteRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeystoreFavoriteCacheTest {
    @Test
    fun encryptedCacheOnlyRestoresTheAccountThatSavedIt() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val gson = NetworkClient.createGson()
        try {
            KeystoreFavoriteCache(context, gson).write("account-a", listOf(FavoriteRow("account-a", "osm:node:1")))
            val reopened = KeystoreFavoriteCache(context, gson)
            assertNull(reopened.read("account-b"))
            assertEquals("osm:node:1", reopened.read("account-a")?.single()?.spotId)
        } finally {
            EncryptedAccountFile(context, "sportspot-favorites-v1").clear()
        }
    }
}
