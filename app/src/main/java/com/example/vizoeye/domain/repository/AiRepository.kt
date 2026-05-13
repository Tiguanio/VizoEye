package com.example.vizoeye.domain.repository

import com.example.vizoeye.domain.model.AnalysisResult
import java.io.File

interface AiRepository {
    suspend fun analyzeImage(
        imageFile: File,
        isDetailedMode: Boolean
    ): AnalysisResult

    suspend fun analyzeText(
        prompt: String
    ): Result<String>

    suspend fun analyzeImageWithText(
        imageFile: File,
        userQuestion: String
    ): Result<String>
}
