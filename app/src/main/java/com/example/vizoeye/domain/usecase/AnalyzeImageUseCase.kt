package com.example.vizoeye.domain.usecase

import android.util.Log
import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.utils.ImageOptimizer
import java.io.File

private const val TAG = "AnalyzeImageUseCase"

class AnalyzeImageUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(
        imageFile: File,
        isDetailedMode: Boolean
    ): AnalysisResult {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "[PERF] Starting image analysis for: ${imageFile.name}")

        // Оптимизируем изображение перед отправкой
        val optimizedFile = ImageOptimizer.optimizeImage(imageFile)
        val optimizeTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "[PERF] Optimization took: ${optimizeTime}ms")

        try {
            val result = aiRepository.analyzeImage(optimizedFile, isDetailedMode)

            // Удаляем временный оптимизированный файл, если он отличается от оригинала
            if (optimizedFile != imageFile && optimizedFile.exists()) {
                optimizedFile.delete()
            }

            return result
        } catch (e: Exception) {
            // В случае ошибки тоже удаляем временный файл
            if (optimizedFile != imageFile && optimizedFile.exists()) {
                optimizedFile.delete()
            }
            throw e
        }
    }
}
