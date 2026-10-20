package com.rob.aikeyboard.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption with a non-exportable key held in the Android Keystore.
 * Used for API keys, clipboard history, learned words and stored images.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_values", Context.MODE_PRIVATE)

    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, IV_SIZE))
        return cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    fun putSecret(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(name).apply()
        } else {
            prefs.edit().putString(name, Base64.encodeToString(encrypt(value.toByteArray()), Base64.NO_WRAP)).apply()
        }
    }

    fun getSecret(name: String): String? = prefs.getString(name, null)?.let {
        runCatching { String(decrypt(Base64.decode(it, Base64.NO_WRAP))) }.getOrNull()
    }

    fun hasSecret(name: String): Boolean = prefs.contains(name)

    fun writeFile(file: File, plain: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(encrypt(plain))
        tmp.renameTo(file)
    }

    fun readFile(file: File): ByteArray? =
        if (file.exists()) runCatching { decrypt(file.readBytes()) }.getOrNull() else null

    fun clearSecrets() = prefs.edit().clear().apply()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "rob_keyboard_master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12

        const val OPENAI_KEY = "openai_api_key"
        const val ANTHROPIC_KEY = "anthropic_api_key"
        const val GEMINI_KEY = "gemini_api_key"
    }
}
