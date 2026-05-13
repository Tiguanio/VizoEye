package com.example.vizoeye.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.vizoeye.domain.voice.SpeechSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "AndroidSpeechRec"

class AndroidSpeechRecognizer(private val context: Context) : SpeechSource {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady

    private val _recognizedText = MutableStateFlow<String?>(null)
    override val recognizedText: StateFlow<String?> = _recognizedText

    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((Exception) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var sessionTimeoutRunnable: Runnable? = null
    private val SESSION_TIMEOUT_MS = 10000L // 10 секунд макс.

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            _isReady.value = true
            initializeRecognizer()
        } else {
            Log.e(TAG, "Recognition not available")
            _isReady.value = false
        }
    }

    private fun initializeRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    cancelSessionTimer()
                    if (error == SpeechRecognizer.ERROR_NO_MATCH && _recognizedText.value != null) return

                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешений"
                        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
                        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Время вышло (10с)"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Ничего не понятно"
                        else -> "Ошибка: $error"
                    }
                    Log.e(TAG, "Error: $message")
                    onErrorCallback?.invoke(Exception(message))
                }

                override fun onResults(results: Bundle?) {
                    cancelSessionTimer()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "Recognized: $text")
                        _recognizedText.value = text
                        onResultCallback?.invoke(text)
                    } else {
                        onResultCallback?.invoke("")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun cancelSessionTimer() {
        sessionTimeoutRunnable?.let { handler.removeCallbacks(it) }
        sessionTimeoutRunnable = null
    }

    override fun startListening(onResult: (String) -> Unit, onError: (Exception) -> Unit) {
        if (!_isReady.value) {
            onError(Exception("Speech recognizer not ready"))
            return
        }

        onResultCallback = onResult
        onErrorCallback = onError
        cancelSessionTimer()

        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Cancel failed", e)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }

        try {
            speechRecognizer?.startListening(intent)
            
            sessionTimeoutRunnable = Runnable {
                Log.d(TAG, "Session timeout (10s). Stopping.")
                speechRecognizer?.stopListening()
            }
            handler.postDelayed(sessionTimeoutRunnable!!, SESSION_TIMEOUT_MS)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            onError(e)
        }
    }

    override fun stopListening() {
        cancelSessionTimer()
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop listening", e)
        }
    }

    override fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _isReady.value = false
    }
}
