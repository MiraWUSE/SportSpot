package com.mirawuse.sportspot.data.local.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small private account files, always encrypted and outside Android backups. */
internal class EncryptedAccountFile(
    context: Context,
    name: String,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, name))
    private val keyAlias = "SportSpot.$name"
    private val keyStore: KeyStore
        get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun read(): ByteArray? {
        val encrypted =
            try {
                file.readFully()
            } catch (_: FileNotFoundException) {
                return null
            }
        val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: return null
        if (encrypted.size < IV_LENGTH + 16) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted, 0, IV_LENGTH))
        return cipher.doFinal(encrypted, IV_LENGTH, encrypted.size - IV_LENGTH)
    }

    fun write(plaintext: ByteArray) {
        val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: createKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        check(cipher.iv.size == IV_LENGTH)
        val out = file.startWrite()
        try {
            out.write(cipher.iv)
            out.write(cipher.doFinal(plaintext))
            file.finishWrite(out)
        } catch (failure: Exception) {
            file.failWrite(out)
            throw failure
        }
    }

    fun clear() {
        try {
            // Revoke this key first so a leftover file cannot restore the account data.
            val keys = keyStore
            keys.deleteEntry(keyAlias)
            if (keys.containsAlias(keyAlias)) throw IOException("Could not remove the account storage key")
        } finally {
            file.delete()
        }
        val base = file.baseFile
        if (base.exists() || File("${base.path}.new").exists() || File("${base.path}.bak").exists()) {
            throw IOException("Could not delete encrypted account data")
        }
    }

    private fun createKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
