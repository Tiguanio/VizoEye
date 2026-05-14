package com.example.vizoeye

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_TTS_SPEED = "tts_speed"
    }

    // --- TTS Settings ---
    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    // --- API Keys ---
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API, ApiConfig.GEMINI_API_KEY) ?: ApiConfig.GEMINI_API_KEY
        set(value) = prefs.edit().putString(KEY_GEMINI_API, value).apply()

    var openRouterApiKey: String
        get() = prefs.getString(KEY_OPENROUTER_API, ApiConfig.OPENROUTER_API_KEY) ?: ApiConfig.OPENROUTER_API_KEY
        set(value) = prefs.edit().putString(KEY_OPENROUTER_API, value).apply()

    // --- AI Provider Selection ---
    private val _selectedProvider = MutableStateFlow(getStoredProvider())
    val selectedProvider: StateFlow<AiServices.AiService> = _selectedProvider.asStateFlow()

    fun setSelectedProvider(provider: AiServices.AiService) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider.name).apply()
        _selectedProvider.value = provider
    }

    private fun getStoredProvider(): AiServices.AiService {
        val name = prefs.getString(KEY_AI_PROVIDER, AiServices.AiService.OPENROUTER.name)
        return try {
            AiServices.AiService.valueOf(name ?: AiServices.AiService.OPENROUTER.name)
        } catch (e: IllegalArgumentException) {
            AiServices.AiService.OPENROUTER
        }
    }
}
