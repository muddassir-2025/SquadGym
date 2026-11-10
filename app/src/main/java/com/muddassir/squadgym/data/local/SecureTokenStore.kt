package com.muddassir.squadgym.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Session secrets at rest, encrypted with a hardware-backed Keystore key.
 *
 * The refresh token (and the access token for warm starts) never touches plain
 * preferences. If the Keystore key is invalidated (lock-screen change, restore),
 * the encrypted blob simply decrypts to null and the user signs in again.
 */
class SecureTokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val key: SecretKey? by lazy { loadOrCreateKey() }

    var accessToken: String?
        get() = read(KEY_ACCESS)
        set(value) = write(KEY_ACCESS, value)

    var refreshToken: String?
        get() = read(KEY_REFRESH)
        set(value) = write(KEY_REFRESH, value)

    /** Cached profile so a warm start can render before the network answers. */
    var cachedUserJson: String?
        get() = read(KEY_USER)
        set(value) = write(KEY_USER, value)

    var cachedGymJson: String?
        get() = read(KEY_GYM)
        set(value) = write(KEY_GYM, value)

    var cachedShiftsJson: String?
        get() = read(KEY_SHIFTS)
        set(value) = write(KEY_SHIFTS, value)

    fun saveTokens(access: String?, refresh: String?) {
        accessToken = access
        refreshToken = refresh
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USER)
            .remove(KEY_GYM)
            .remove(KEY_SHIFTS)
            .apply()
    }

    private fun write(name: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(name).apply()
            return
        }
        val encrypted = encrypt(value)
        if (encrypted == null) editor.remove(name) else editor.putString(name, encrypted)
        editor.apply()
    }

    private fun read(name: String): String? {
        val raw = prefs.getString(name, null) ?: return null
        return decrypt(raw)
    }

    private fun encrypt(plain: String): String? {
        val secret = key ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secret)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(value: String): String? {
        val secret = key ?: return null
        return try {
            val parts = value.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val payload = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadOrCreateKey(): SecretKey? = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "squadgym_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFS_NAME = "squadgym_secure_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER = "cached_user"
        const val KEY_GYM = "cached_gym"
        const val KEY_SHIFTS = "cached_shifts"
    }
}
