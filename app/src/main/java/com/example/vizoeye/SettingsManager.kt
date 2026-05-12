package com.example.vizoeye

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsManager(private val context: Context) {
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val prefs = EncryptedSharedPreferences.create(
        "vizoeye_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_OPENROUTER_API = "openrouter_api_key"
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API, ApiConfig.GEMINI_API_KEY) ?: ApiConfig.GEMINI_API_KEY
        set(value) = prefs.edit().putString(KEY_GEMINI_API, value).apply()

    var openRouterApiKey: String
        get() = prefs.getString(KEY_OPENROUTER_API, ApiConfig.OPENROUTER_API_KEY) ?: ApiConfig.OPENROUTER_API_KEY
        set(value) = prefs.edit().putString(KEY_OPENROUTER_API, value).apply()
}
