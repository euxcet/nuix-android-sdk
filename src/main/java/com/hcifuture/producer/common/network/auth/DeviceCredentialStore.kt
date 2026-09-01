package com.hcifuture.producer.common.network.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DeviceCredentials(
    val deviceInstallationId: String,
    val token: String,
) {
    val tokenSha256: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}

/**
 * Creates one credential per app installation. The bearer token is encrypted with an
 * Android Keystore key before being stored in SharedPreferences.
 */
object DeviceCredentialStore {
    private const val PREFERENCES_NAME = "cogring_device_credentials_v1"
    private const val DEVICE_ID_KEY = "device_installation_id"
    private const val TOKEN_CIPHERTEXT_KEY = "token_ciphertext"
    private const val TOKEN_IV_KEY = "token_iv"
    private const val KEY_ALIAS = "cogring_device_credential_key_v1"
    private const val TOKEN_BYTES = 32

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var cachedCredentials: DeviceCredentials? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    @Synchronized
    fun get(): DeviceCredentials {
        cachedCredentials?.let { return it }
        val context = checkNotNull(applicationContext) {
            "DeviceCredentialStore.initialize must be called from Application.onCreate"
        }
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val deviceId = preferences.getString(DEVICE_ID_KEY, null)
        val ciphertext = preferences.getString(TOKEN_CIPHERTEXT_KEY, null)
        val iv = preferences.getString(TOKEN_IV_KEY, null)

        if (!deviceId.isNullOrBlank() && !ciphertext.isNullOrBlank() && !iv.isNullOrBlank()) {
            runCatching {
                DeviceCredentials(deviceId, decrypt(ciphertext, iv))
            }.getOrNull()?.let { restored ->
                cachedCredentials = restored
                return restored
            }
        }

        return createAndPersist(preferences)
    }

    @Synchronized
    fun rotate(): DeviceCredentials {
        val context = checkNotNull(applicationContext) {
            "DeviceCredentialStore.initialize must be called from Application.onCreate"
        }
        cachedCredentials = null
        return createAndPersist(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }

    fun newOpaqueToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private fun createAndPersist(
        preferences: android.content.SharedPreferences,
    ): DeviceCredentials {
        val credentials = DeviceCredentials(
            deviceInstallationId = UUID.randomUUID().toString(),
            token = newOpaqueToken(),
        )
        val encrypted = encrypt(credentials.token)
        val saved = preferences.edit()
            .putString(DEVICE_ID_KEY, credentials.deviceInstallationId)
            .putString(TOKEN_CIPHERTEXT_KEY, encrypted.first)
            .putString(TOKEN_IV_KEY, encrypted.second)
            .commit()
        check(saved) { "Unable to persist device credentials" }
        cachedCredentials = credentials
        return credentials
    }

    private fun encrypt(token: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        return String(
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
            StandardCharsets.UTF_8,
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }
}
