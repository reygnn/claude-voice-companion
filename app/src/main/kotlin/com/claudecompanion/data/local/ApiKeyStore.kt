package com.claudecompanion.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(apiKey: String) {
        prefs.edit().putString(KEY_API, apiKey).apply()
    }

    fun get(): String? = prefs.getString(KEY_API, null)

    fun clear() = prefs.edit().remove(KEY_API).apply()

    companion object {
        private const val PREFS_NAME = "claude_secure_prefs"
        private const val KEY_API = "anthropic_api_key"
    }
}
