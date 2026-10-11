package org.aimlds.mymilo.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Vault — every API key the app holds, under one ruleset
 * (SPEC-SOURCES-VAULT):
 *
 *  · Secrets are encrypted with a key that lives in the Android
 *    Keystore and never leaves it; only ciphertext is stored
 *    (in this file's private preferences — never Room, never
 *    synced, never exported, never logged).
 *  · Write-only by design: the app reads a secret only at the
 *    moment it makes a call. No screen ever displays one.
 *  · A lost key is replaced, never recovered.
 */
class Vault(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Store a secret under [name]. Overwrites any previous one. */
    fun put(name: String, secret: String): Boolean = try {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(name, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
        true
    } catch (e: Exception) {
        false
    }

    /** Read a secret back (call-time only). Null when absent or
     *  when the vault can't unlock (e.g. key invalidated). */
    fun get(name: String): String? {
        return try {
            val stored = prefs.getString(name, null) ?: return null
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            if (packed.size <= GCM_IV_BYTES) return null
            val iv = packed.copyOfRange(0, GCM_IV_BYTES)
            val ct = packed.copyOfRange(GCM_IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun contains(name: String): Boolean = prefs.contains(name)

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    companion object {
        private const val PREFS_NAME = "mymilo_vault"
        private const val KEY_ALIAS = "mymilo_vault_key_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12

        /** Vault slot for a source-token row's secret. */
        fun tokenSlot(tokenEntryId: String): String = "token:$tokenEntryId"

        /** Vault slot for the Aetheris device token (Vault entry #1). */
        const val AETHERIS_SLOT = "aetheris_token"
    }
}
