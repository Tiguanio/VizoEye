package com.example.vizoeye

object ApiConfig {
    // Точное имя модели из примера, которое должно работать
    const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
    const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    
    // Имена моделей
    const val GEMINI_MODEL = "gemini-flash-latest"
    const val OPENROUTER_MODEL = "openrouter/auto"

    // Заглушки для ключей
    const val GEMINI_API_KEY = ""
    const val OPENROUTER_API_KEY = ""
}
