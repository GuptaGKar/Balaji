package com.balaji.smsgateway

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)
        ?.takeIf(GatewayPolicy::isValidApiKey)

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        require(GatewayPolicy.isValidApiKey(trimmed)) {
            "API key must be at least ${GatewayPolicy.MIN_API_KEY_LENGTH} characters"
        }
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
    }

    fun ensureApiKey(): String {
        val existing = getApiKey()
        if (existing != null) return existing

        val generated = generateApiKey()
        setApiKey(generated)
        return generated
    }

    fun regenerateApiKey(): String {
        val generated = generateApiKey()
        setApiKey(generated)
        return generated
    }

    private fun generateApiKey(): String {
        val bytes = ByteArray(API_KEY_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    private companion object {
        const val PREFS_NAME = "gateway_secure"
        const val KEY_API_KEY = "api_key"
        const val API_KEY_BYTES = 32
    }
}
