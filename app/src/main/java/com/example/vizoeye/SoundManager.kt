package com.example.vizoeye

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    // ToneGenerator для генерации простых тональных сигналов
    private val toneGenerator by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    /**
     * Проигрывает звук затвора (короткий высокий сигнал)
     */
    fun playShutterSound() {
        requestAudioFocus()
        handler.post {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    /**
     * Проигрывает звук успеха (двойной положительный сигнал)
     */
    fun playSuccessSound() {
        requestAudioFocus()
        handler.post {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            handler.postDelayed({
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            }, 250)
        }
    }

    /**
     * Проигрывает звук ошибки (низкий предупреждающий сигнал)
     */
    fun playErrorSound() {
        requestAudioFocus()
        handler.post {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_NETWORK_BUSY, 300)
        }
    }

    /**
     * Проигрывает звук начала анализа (короткий клик)
     */
    fun playStartSound() {
        requestAudioFocus()
        handler.post {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
        }
    }

    private fun requestAudioFocus() {
        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        audioManager.requestAudioFocus(audioFocusRequest)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        toneGenerator.release()
    }
}
