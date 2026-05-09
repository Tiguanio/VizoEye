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
    private var speakStartTime: Long = 0L

    // Состояние речи для реактивного обновления UI
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate

    init {
        initializeTTS()
    }

    fun changeSpeed(increase: Boolean) {
        val current = _speechRate.value
        val newRate = if (increase) current + 0.5f else current - 0.5f
        _speechRate.value = newRate.coerceIn(0.5f, 3.0f)
        textToSpeech?.setSpeechRate(_speechRate.value)
        Log.d(TAG, "Speech rate changed to: ${_speechRate.value}")
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
                Log.d(TAG, "[PERF] TTS started speaking after ${System.currentTimeMillis() - speakStartTime}ms")
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _isPaused.value = false
                Log.d(TAG, "[PERF] TTS finished speaking")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _isPaused.value = false
                Log.e(TAG, "TTS Error")
            }
        })
    }

    fun speak(text: String) {
        speakStartTime = System.currentTimeMillis()
        Log.d(TAG, "[PERF] TTS speak() called for text length: ${text.length}")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
    }

    fun pause() {
        textToSpeech?.stop()
        _isPaused.value = true
        _isSpeaking.value = false
    }

    fun resume(text: String) {
        _isPaused.value = false
        speak(text)
    }

    fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
