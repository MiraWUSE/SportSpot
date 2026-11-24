package com.mirawuse.sportspot.data.local.account

import android.content.Context
import com.google.gson.Gson
import com.mirawuse.sportspot.data.remote.api.FavoriteRow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal interface FavoriteCache {
    fun read(userId: String): List<FavoriteRow>?

    fun write(
        userId: String,
        rows: List<FavoriteRow>,
    )
}

internal class KeystoreFavoriteCache
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val gson: Gson,
    ) : FavoriteCache {
        private val file = EncryptedAccountFile(context, "sportspot-favorites-v1")

        override fun read(userId: String): List<FavoriteRow>? =
            try {
                file.read()?.let {
                    gson.fromJson(it.decodeToString(), Record::class.java)?.takeIf { record -> record.userId == userId }?.rows
                }
            } catch (_: Exception) {
                null
            }

        override fun write(
            userId: String,
            rows: List<FavoriteRow>,
        ) {
            file.write(gson.toJson(Record(userId, rows)).encodeToByteArray())
        }

        private class Record(
            val userId: String? = null,
            val rows: List<FavoriteRow>? = null,
        )
    }
