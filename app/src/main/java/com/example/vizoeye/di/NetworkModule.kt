package com.example.vizoeye.di

import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import com.example.vizoeye.data.repository.AiRepositoryImpl
import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOpenRouterApiService(
        settingsManager: com.example.vizoeye.SettingsManager
    ): OpenRouterApiService {
        return OpenRouterApiService(settingsManager)
    }

    @Provides
    @Singleton
    fun provideGeminiApiService(
        settingsManager: com.example.vizoeye.SettingsManager
    ): GeminiApiService {
        return GeminiApiService(settingsManager)
    }

    @Provides
    @Singleton
    fun provideAiRepository(
        openRouterService: OpenRouterApiService,
        geminiService: GeminiApiService
    ): AiRepository {
        return AiRepositoryImpl(openRouterService, geminiService)
    }
}
