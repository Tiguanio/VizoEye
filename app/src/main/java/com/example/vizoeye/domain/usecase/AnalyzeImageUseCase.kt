package com.example.vizoeye.domain.usecase

import com.example.vizoeye.AiServices
import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.repository.AiRepository
import java.io.File

class AnalyzeImageUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(
        imageFile: File,
        isDetailedMode: Boolean,
        service: AiServices.AiService = AiServices.getCurrentService()
    ): AnalysisResult {
        return aiRepository.analyzeImage(imageFile, isDetailedMode, service)
    }
}
