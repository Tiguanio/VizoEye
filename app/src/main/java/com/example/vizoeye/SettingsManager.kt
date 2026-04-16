package com.example.vizoeye

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    // Используем vizoeye_prefs для консистентности брендинга
    private val prefs: SharedPreferences = context.getSharedPreferences("vizoeye_prefs", Context.MODE_PRIVATE)

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
