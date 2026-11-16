package com.example.altyazi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** DeepL API key never appears in the APK, logs or unencrypted preferences. */
object DeepLCredentials {
    private const val ALIAS = "gamelin_deepl_key_v1"
    private const val FILE = "gamelin_deepl_private"
    private const val ENTRY = "key_gcm"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    fun read(context: Context): String {
        return try {
        val blob = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(ENTRY, null) ?: return ""
        val data = Base64.decode(blob, Base64.NO_WRAP)
        if (data.size < 13) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    /** False means the key could not be stored securely. Never use plaintext fallback. */
    fun save(context: Context, raw: String): Boolean {
        val value = raw.trim()
        if (value.isBlank()) { clear(context); return true }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(ENTRY, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        } catch (_: Exception) { false }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(ENTRY).commit()
    }
}
