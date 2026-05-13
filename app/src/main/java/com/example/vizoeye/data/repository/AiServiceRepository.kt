package com.example.vizoeye.data.repository

import com.example.vizoeye.AiServices
import com.example.vizoeye.SettingsManager
import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiServiceRepository(
    private val settingsManager: SettingsManager,
    private val openRouterService: OpenRouterApiService,
    private val geminiService: GeminiApiService
) {
    /**
     * Поток текущего выбранного провайдера.
     */
    val currentProvider: Flow<AiServices.AiService> = settingsManager.selectedProvider

    /**
     * Поток активного API-клиента в зависимости от выбранного провайдера.
     */
    val activeClient: Flow<Any> = settingsManager.selectedProvider.map { provider ->
        when (provider) {
            AiServices.AiService.GEMINI -> geminiService
            AiServices.AiService.OPENROUTER -> openRouterService
        }
    }

    /**
     * Поток текущего API-ключа. Возвращает пустую строку, если ключ не установлен,
     * но гарантирует актуальность данных из SettingsManager.
     */
    val currentApiKey: Flow<String> = settingsManager.selectedProvider.map { provider ->
        when (provider) {
            AiServices.AiService.GEMINI -> settingsManager.geminiApiKey
            AiServices.AiService.OPENROUTER -> settingsManager.openRouterApiKey
        }
    }

    /**
     * Синхронный метод для получения текущего провайдера (для совместимости с legacy кодом).
     */
    fun getCurrentProviderSync(): AiServices.AiService {
        return settingsManager.selectedProvider.value
    }

    /**
     * Установка выбранного провайдера.
     */
    fun setSelectedProvider(provider: AiServices.AiService) {
        settingsManager.setSelectedProvider(provider)
    }

    /**
     * Переключение на следующий доступный провайдер.
     */
    suspend fun switchProvider() {
        val current = getCurrentProviderSync()
        val next = if (current == AiServices.AiService.GEMINI) {
            AiServices.AiService.OPENROUTER
        } else {
            AiServices.AiService.GEMINI
        }
        setSelectedProvider(next)
    }
}
