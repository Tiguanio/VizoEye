package com.example.vizoeye.domain.usecase

import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.repository.AiRepository
import java.io.File

class AnalyzeImageUseCase(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(imageFile: File, isDetailedMode: Boolean): AnalysisResult {
        return aiRepository.analyzeImage(imageFile, isDetailedMode)
    }
}
