package com.example.vizoeye.domain.usecase

import android.util.Log
import com.example.vizoeye.TtsManager
import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.domain.voice.SpeechSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "VoiceAnalyzeUseCase"

class VoiceAnalyzeUseCase(
    private val speechSource: SpeechSource,
    private val aiRepository: AiRepository,
    private val ttsManager: TtsManager
) {
    /**
     * Запускает голосовой диалог с контекстом изображения.
     * @param contextHistory История диалога для формирования контекста
     */
    suspend operator fun invoke(
        imageFile: File? = null,
        contextHistory: String = ""
    ): Result<VoiceAnalysisResult> {
        return try {
            Log.d(TAG, "Starting voice recognition...")

            ttsManager.stop()
            delay(300)

            val recognizedText = withTimeoutOrNull(15_000L) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<String> { cont ->
                        speechSource.startListening(
                            onResult = { text ->
                                if (!cont.isCompleted) cont.resume(text)
                            },
                            onError = { error ->
                                if (!cont.isCompleted) cont.resumeWithException(error)
                            }
                        )
                        cont.invokeOnCancellation { speechSource.stopListening() }
                    }
                }
            } ?: throw Exception("Voice recognition timed out")

            if (recognizedText.isBlank()) {
                throw Exception("Empty speech input")
            }

            Log.d(TAG, "Recognized: $recognizedText")

            // Формируем финальный промпт с учетом истории
            val finalPrompt = if (contextHistory.isNotBlank()) {
                "$contextHistory $recognizedText"
            } else {
                recognizedText
            }

            val aiResult = withContext(Dispatchers.IO) {
                if (imageFile != null) {
                    // Для картинок пока не добавляем историю в промпт, так как API могут не поддерживать
                    // длинный текст вместе с картинкой эффективно. Но можно попробовать.
                    aiRepository.analyzeImageWithText(imageFile, finalPrompt)
                } else {
                    aiRepository.analyzeText(finalPrompt)
                }
            }

            return aiResult.map { answer ->
                VoiceAnalysisResult(question = recognizedText, answer = answer)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Voice analysis failed", e)
            Result.failure(e)
        }
    }
}
