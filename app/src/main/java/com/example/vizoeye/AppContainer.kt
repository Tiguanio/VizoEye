package com.example.vizoeye

import android.content.Context
import com.example.vizoeye.data.local.RequestQueueManager
import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import com.example.vizoeye.data.repository.AiRepositoryImpl
import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {

    // Единый оптимизированный HTTP клиент
    val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    // Managers
    val settingsManager by lazy { SettingsManager(context) }
    val soundManager by lazy { SoundManager(context) }
    val ttsManager by lazy { TtsManager(context) }
    val cameraManager by lazy { CameraManager(context) }
    val requestQueueManager by lazy { RequestQueueManager(context) }

    // Network
    private val openRouterApiService by lazy { OpenRouterApiService(settingsManager, httpClient) }
    private val geminiApiService by lazy { GeminiApiService(settingsManager, httpClient) }

    // Repository
    private val aiRepository: AiRepository by lazy {
        AiRepositoryImpl(openRouterApiService, geminiApiService)
    }

    // UseCase
    val analyzeImageUseCase by lazy { AnalyzeImageUseCase(aiRepository) }
}
