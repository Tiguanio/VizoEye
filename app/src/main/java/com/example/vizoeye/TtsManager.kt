package com.example.vizoeye

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

private const val TAG = "TtsManager"

class TtsManager(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    
    // Состояние речи для реактивного обновления UI
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    var speechRate: Float = 2.0f
        set(value) {
            field = value.coerceIn(0.5f, 3.0f)
            Log.d(TAG, "Speech rate changed to: $field")
        }

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("ru"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Russian language not supported")
                } else {
                    Log.d(TAG, "TTS initialized with Russian language")
                }
            } else {
                Log.e(TAG, "TTS initialization error: $status")
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _isPaused.value = false
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _isPaused.value = false
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _isPaused.value = false
            }
        })
    }

    fun speak(text: String) {
        // Если уже говорит и не на паузе — останавливаем предыдущее
        if (_isSpeaking.value && !_isPaused.value) {
            textToSpeech?.stop()
        }

        // Очищаем текст от символов разметки ИИ
        val cleanText = text.replace(Regex("[*#]"), " ").replace(Regex("\\s+"), " ").trim()

        if (cleanText.isEmpty()) return

        textToSpeech?.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "vizoeye_utterance"
        )
    }

    fun pause() {
        if (_isSpeaking.value && !_isPaused.value) {
            textToSpeech?.stop()
            _isPaused.value = true
        }
    }

    fun resume(lastText: String) {
        if (_isPaused.value) {
            speak(lastText)
        }
    }

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
