package com.example.vizoeye.data.repository

import com.example.vizoeye.AiServices
import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import java.io.File

class AiRepositoryImpl(
    private val openRouterService: OpenRouterApiService,
    private val geminiService: GeminiApiService
) : AiRepository {

    override suspend fun analyzeImage(imageFile: File, isDetailedMode: Boolean): AnalysisResult {
        return try {
            val result = when (AiServices.getCurrentService()) {
                AiServices.AiService.GEMINI -> geminiService.analyzeImage(imageFile, isDetailedMode)
                AiServices.AiService.OPENROUTER -> openRouterService.analyzeImage(imageFile, isDetailedMode)
            }

            if (result != null) {
                AnalysisResult(
                    description = result.replace(Regex("[*#]"), "").trim(),
                    isSuccess = true
                )
            } else {
                AnalysisResult(
                    description = "Не удалось получить описание",
                    isSuccess = false,
                    errorMessage = "Пустой ответ от API"
                )
            }
        } catch (e: Exception) {
            AnalysisResult(
                description = "Ошибка анализа: ${e.message}",
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }
}
