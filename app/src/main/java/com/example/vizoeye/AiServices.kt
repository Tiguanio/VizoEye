package com.example.vizoeye

object AiServices {
    // AI services - Gemini and OpenRouter
    enum class AiService(val displayName: String, val hasVision: Boolean) {
        GEMINI("Google Gemini", true),
        OPENROUTER("OpenRouter (Free)", true)
    }
    
    // Current selected service
    private var currentService = AiService.OPENROUTER
    
    // Get current service
    fun getCurrentService(): AiService = currentService
    
    // Switch to next service
    fun switchToNextService(): AiService {
        val services = AiService.values()
        val currentIndex = services.indexOf(currentService)
        val nextIndex = (currentIndex + 1) % services.size
        currentService = services[nextIndex]
        return currentService
    }
    
    // Set specific service
    fun setService(service: AiService) {
        currentService = service
    }
    
    // Get URL for current service
    fun getCurrentUrl(): String {
        return when (currentService) {
            AiService.GEMINI -> ApiConfig.GEMINI_URL
            AiService.OPENROUTER -> ApiConfig.OPENROUTER_URL
        }
    }
    
    // Get model for current service
    fun getCurrentModel(): String {
        return when (currentService) {
            AiService.GEMINI -> ApiConfig.GEMINI_MODEL
            AiService.OPENROUTER -> ApiConfig.OPENROUTER_MODEL
        }
    }

    // Get API Key for current service
    fun getCurrentApiKey(): String {
        return ""
    }
}
