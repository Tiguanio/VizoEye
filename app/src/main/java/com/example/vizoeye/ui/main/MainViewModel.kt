package com.example.vizoeye.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vizoeye.AiServices
import com.example.vizoeye.SoundManager
import com.example.vizoeye.TtsManager
import com.example.vizoeye.data.local.RequestQueueManager
import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val analyzeImageUseCase: AnalyzeImageUseCase,
    val ttsManager: TtsManager,
    private val soundManager: SoundManager,
    private val requestQueueManager: RequestQueueManager
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // Состояние UI
    private val _status = MutableStateFlow("Готово к работе")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isDetailedMode = MutableStateFlow(false)
    val isDetailedMode: StateFlow<Boolean> = _isDetailedMode.asStateFlow()

    private val _currentService = MutableStateFlow(AiServices.getCurrentService())
    val currentService: StateFlow<AiServices.AiService> = _currentService.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    val queueSize: StateFlow<Int> = requestQueueManager.queueSize
    val isProcessingQueue: StateFlow<Boolean> = requestQueueManager.isProcessing

    private val _isProcessingQueueInternal = MutableStateFlow(false)

    // Проверка разрешений
    fun checkPermissions(context: Context) {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        _hasCameraPermission.value = allGranted
    }

    fun requestPermissionsResult(grantResults: IntArray) {
        _hasCameraPermission.value = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }

    // Переключение режима детализации
    fun toggleDetailedMode() {
        _isDetailedMode.value = !_isDetailedMode.value
        _status.value = if (_isDetailedMode.value) "Режим: ПОДРОБНЫЙ" else "Режим: КРАТКИЙ"
        Log.d(TAG, "Detailed mode changed to: ${_isDetailedMode.value}")
    }

    // Переключение ИИ-сервиса
    fun switchService() {
        val newService = AiServices.switchToNextService()
        _currentService.value = newService
        Log.d(TAG, "AI Service switched to: ${newService.displayName}")
    }

    // Открытие/закрытие настроек
    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    // Проверка сети и обновление статуса оффлайн-режима
    fun checkNetworkStatus() {
        _isOfflineMode.value = !requestQueueManager.isNetworkAvailable()
        if (!_isOfflineMode.value && requestQueueManager.queueSize.value > 0) {
            processQueuedRequests()
        }
    }

    // Обработка queued запросов
    private fun processQueuedRequests() {
        if (_isProcessingQueueInternal.value || !requestQueueManager.isNetworkAvailable()) return

        viewModelScope.launch {
            _isProcessingQueueInternal.value = true
            while (requestQueueManager.queueSize.value > 0 && requestQueueManager.isNetworkAvailable()) {
                val request = requestQueueManager.getNextRequest()
                if (request == null) break

                try {
                    val imageFile = File(request.imagePath)
                    if (!imageFile.exists()) {
                        Log.w(TAG, "Queued image file not found: ${request.imagePath}")
                        continue
                    }

                    _status.value = "Обработка отложенного запроса..."
                    soundManager.playStartSound()

                    val result = withContext(Dispatchers.IO) {
                        analyzeImageUseCase(imageFile, request.isDetailedMode, _currentService.value)
                    }

                    handleAnalysisResult(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process queued request: ${request.id}", e)
                    soundManager.playErrorSound()
                }
            }
            _isProcessingQueueInternal.value = false
        }
    }

    // Анализ изображения
    fun analyzeImage(imageFile: File) {
        if (_isAnalyzing.value) {
            Log.d(TAG, "analyzeImage: анализ уже идет, игнорируем")
            return
        }

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "[PERF] Start analyzeImage")

        // Проверяем сеть перед началом
        checkNetworkStatus()

        if (_isOfflineMode.value) {
            // Нет сети — добавляем в очередь
            requestQueueManager.addToQueue(imageFile, _isDetailedMode.value)
            _status.value = "Нет сети. Запрос добавлен в очередь (${requestQueueManager.queueSize.value})"
            soundManager.playErrorSound()
            Log.d(TAG, "Added to offline queue: ${imageFile.absolutePath}")
            return
        }

        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _status.value = "Анализирую изображение..."
                soundManager.playStartSound()
                Log.d(TAG, "Начало анализа файла: ${imageFile.absolutePath}")

                val beforeApiCall = System.currentTimeMillis()
                Log.d(TAG, "[PERF] Time to prepare (permissions, UI): ${beforeApiCall - startTime}ms")

                val result = withContext(Dispatchers.IO) {
                    analyzeImageUseCase(imageFile, _isDetailedMode.value, _currentService.value)
                }

                val afterApiCall = System.currentTimeMillis()
                Log.d(TAG, "[PERF] API/Model execution time: ${afterApiCall - beforeApiCall}ms")

                handleAnalysisResult(result)
                Log.d(TAG, "[PERF] Total time: ${System.currentTimeMillis() - startTime}ms")
            } catch (e: Exception) {
                _status.value = "Критическая ошибка: ${e.message}"
                soundManager.playErrorSound()
                Log.e(TAG, "Unexpected error", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun handleAnalysisResult(result: AnalysisResult) {
        if (result.isSuccess) {
            _description.value = result.description
            _status.value = "Анализ завершен"
            soundManager.playSuccessSound()
            Log.d(TAG, "Результат получен: ${result.description.take(50)}...")
            // Автоматическая озвучка результата
            ttsManager.speak(result.description)
        } else {
            _status.value = "Ошибка: ${result.errorMessage}"
            _description.value = result.description
            soundManager.playErrorSound()
            Log.e(TAG, "Ошибка анализа: ${result.errorMessage}")
        }
    }
}
