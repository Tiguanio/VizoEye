package com.example.vizoeye.data.repository

import com.example.vizoeye.AiServices
import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import java.io.File

class AiRepositoryImpl(
    private val aiServiceRepository: AiServiceRepository,
    private val openRouterService: OpenRouterApiService,
    private val geminiService: GeminiApiService
) : AiRepository {

    override suspend fun analyzeImage(
        imageFile: File,
        isDetailedMode: Boolean
    ): AnalysisResult {
        return try {
            val provider = aiServiceRepository.getCurrentProviderSync()
            val result = when (provider) {
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

    override suspend fun analyzeText(prompt: String): Result<String> {
        return try {
            val provider = aiServiceRepository.getCurrentProviderSync()
            val result = when (provider) {
                AiServices.AiService.GEMINI -> geminiService.analyzeText(prompt)
                AiServices.AiService.OPENROUTER -> openRouterService.analyzeText(prompt)
            }

            if (result != null) {
                Result.success(result.trim())
            } else {
                Result.failure(Exception("Пустой ответ от API"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun analyzeImageWithText(imageFile: File, userQuestion: String): Result<String> {
        return try {
            val provider = aiServiceRepository.getCurrentProviderSync()
            val prompt = "Ответь на вопрос пользователя об изображении кратко и по делу на русском языке. Вопрос: $userQuestion"
            
            val result = when (provider) {
                AiServices.AiService.GEMINI -> geminiService.analyzeImageWithPrompt(imageFile, prompt)
                AiServices.AiService.OPENROUTER -> openRouterService.analyzeImageWithPrompt(imageFile, prompt)
            }

            if (result != null) {
                Result.success(result.replace(Regex("[*#]"), "").trim())
            } else {
                Result.failure(Exception("Пустой ответ от API"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
