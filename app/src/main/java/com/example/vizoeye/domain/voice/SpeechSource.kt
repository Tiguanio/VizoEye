package com.example.vizoeye.domain.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Абстракция источника голоса (распознавание речи).
 */
interface SpeechSource {
    /**
     * Поток состояния: готовность к записи.
     */
    val isReady: StateFlow<Boolean>

    /**
     * Поток распознанного текста.
     */
    val recognizedText: StateFlow<String?>

    /**
     * Начать прослушивание.
     * @param onResult Callback при успешном распознавании.
     * @param onError Callback при ошибке.
     */
    fun startListening(onResult: (String) -> Unit, onError: (Exception) -> Unit)

    /**
     * Остановить прослушивание.
     */
    fun stopListening()

    /**
     * Освободить ресурсы.
     */
    fun release()
}
