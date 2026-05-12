package com.example.vizoeye.domain.repository

import com.example.vizoeye.AiServices
import com.example.vizoeye.domain.model.AnalysisResult
import java.io.File

interface AiRepository {
    suspend fun analyzeImage(
        imageFile: File,
        isDetailedMode: Boolean,
        service: AiServices.AiService
    ): AnalysisResult
}
