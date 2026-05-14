package com.example.vizoeye.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vizoeye.AiServices
import com.example.vizoeye.CameraManager
import com.example.vizoeye.SoundManager
import com.example.vizoeye.TtsManager
import com.example.vizoeye.data.local.RequestQueueManager
import com.example.vizoeye.data.repository.AiServiceRepository
import com.example.vizoeye.domain.model.AnalysisResult
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import com.example.vizoeye.domain.usecase.VoiceAnalyzeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val analyzeImageUseCase: AnalyzeImageUseCase,
    private val voiceAnalyzeUseCase: VoiceAnalyzeUseCase,
    val ttsManager: TtsManager,
    private val soundManager: SoundManager,
    private val requestQueueManager: RequestQueueManager,
    private val aiServiceRepository: AiServiceRepository,
    private val cameraManager: CameraManager
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

    // Используем StateFlow из репозитория для реактивного обновления при смене провайдера
    val currentService: StateFlow<AiServices.AiService> = aiServiceRepository.currentProvider
        .stateIn(viewModelScope, SharingStarted.Lazily, aiServiceRepository.getCurrentProviderSync())

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    // История последних 3-х запросов (для контекста)
    data class ChatMessage(val role: String, val content: String)
    private val contextHistory = mutableListOf<ChatMessage>()

    private fun addToContext(question: String, answer: String) {
        if (question.isBlank() || answer.isBlank()) return
        
        contextHistory.add(ChatMessage("user", question))
        contextHistory.add(ChatMessage("assistant", answer))

        // Храним только последние 3 пары (6 сообщений)
        if (contextHistory.size > 6) {
            contextHistory.subList(0, contextHistory.size - 6).clear()
        }
    }

    private fun getContextPrompt(): String {
        if (contextHistory.isEmpty()) return ""
        
        val sb = StringBuilder("История нашего диалога:\n")
        contextHistory.forEach { msg ->
            val roleRu = if (msg.role == "user") "Я спросил" else "Ты ответил"
            sb.append("$roleRu: ${msg.content}\n")
        }
        sb.append("\nОсновываясь на этой истории, ответь на новый вопрос:")
        return sb.toString()
    }

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

    // Открытие/закрытие настроек
    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    // Переключение AI сервиса
    fun switchAiService() {
        viewModelScope.launch {
            aiServiceRepository.switchProvider()
            val newService = aiServiceRepository.getCurrentProviderSync()
            ttsManager.speak("Переключено на ${newService.displayName}")
            Log.d(TAG, "AI Service switched to: ${newService.name}")
        }
    }

    // --- Режимы работы ---

    /**
     * Режим 1: КРАТКО
     */
    fun analyzeBriefly(imageFile: File) {
        performImageAnalysis(imageFile, isDetailedMode = false)
    }

    /**
     * Режим 2: ПОДРОБНО
     */
    fun analyzeDetailed(imageFile: File) {
        performImageAnalysis(imageFile, isDetailedMode = true)
    }

    private fun performImageAnalysis(imageFile: File, isDetailedMode: Boolean) {
        if (_isAnalyzing.value) return

        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _status.value = "Анализирую изображение..."
                soundManager.playStartSound()

                val result = withContext(Dispatchers.IO) {
                    analyzeImageUseCase(imageFile, isDetailedMode)
                }

                handleAnalysisResult(result)
            } catch (e: Exception) {
                _status.value = "Ошибка: ${e.message}"
                soundManager.playErrorSound()
                Log.e(TAG, "Analysis error", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Режим 3: ВОПРОС (Голосовой + Фото)
     */
    fun startVoiceQuestion() {
        if (_isAnalyzing.value) {
            _isAnalyzing.value = false
            _status.value = "Отменено"
            return
        }

        viewModelScope.launch {
            try {
                _isAnalyzing.value = true
                _status.value = "Делаю снимок..."
                soundManager.playStartSound()

                // 1. Делаем снимок
                val imageFile = withContext(Dispatchers.IO) {
                    cameraManager.takePicture()
                }

                if (imageFile == null) {
                    _status.value = "Ошибка камеры"
                    soundManager.playErrorSound()
                    _isAnalyzing.value = false
                    return@launch
                }

                _status.value = "Слушаю ваш вопрос..."
                ttsManager.speak("Задайте свой вопрос")

                // 2. Запускаем распознавание и отправляем фото с вопросом в AI
                val contextPrompt = getContextPrompt()
                val result = voiceAnalyzeUseCase(imageFile, contextPrompt)

                result.onSuccess { analysisResult ->
                    if (analysisResult.answer.isNotBlank()) {
                        _description.value = analysisResult.answer
                        _status.value = "Ответ получен"
                        ttsManager.speak(analysisResult.answer)
                        
                        // Сохраняем в контекст для следующих вопросов
                        addToContext(analysisResult.question, analysisResult.answer)
                    } else {
                        _status.value = "Пустой ответ от AI"
                        soundManager.playErrorSound()
                    }
                }.onFailure { error ->
                    _status.value = "Ошибка: ${error.message}"
                    soundManager.playErrorSound()
                }
            } catch (e: Exception) {
                _status.value = "Ошибка: ${e.message}"
                soundManager.playErrorSound()
                Log.e(TAG, "Voice question error", e)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Принудительная остановка голосовой записи (например, по кнопке громкости)
     */
    fun stopVoiceQuestion() {
        _status.value = "Запись прервана"
        soundManager.playShutterSound()
        _isAnalyzing.value = false
    }

    private fun handleAnalysisResult(result: AnalysisResult) {
        if (result.isSuccess) {
            _description.value = result.description
            _status.value = "Анализ завершен"
            soundManager.playSuccessSound()
            Log.d(TAG, "Результат получен: ${result.description.take(50)}...")
            ttsManager.speak(result.description)
        } else {
            _status.value = "Ошибка: ${result.errorMessage}"
            _description.value = result.description
            soundManager.playErrorSound()
            Log.e(TAG, "Ошибка анализа: ${result.errorMessage}")
        }
    }
}
