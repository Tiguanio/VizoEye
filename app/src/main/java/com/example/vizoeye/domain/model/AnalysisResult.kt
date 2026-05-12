package com.example.vizoeye.domain.model

data class AnalysisResult(
    val description: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
