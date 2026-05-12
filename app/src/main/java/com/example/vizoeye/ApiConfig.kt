package com.example.vizoeye

object ApiConfig {
    // URL для Gemini (v1beta актуален для 2026 года)
    const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
    const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    
    // Имена моделей
    const val GEMINI_MODEL = "gemini-flash-latest"
    const val OPENROUTER_MODEL = "openrouter/auto"

    // Заглушки для ключей (пользователь вводит их в приложении)
    const val GEMINI_API_KEY = ""
    const val OPENROUTER_API_KEY = ""
}
