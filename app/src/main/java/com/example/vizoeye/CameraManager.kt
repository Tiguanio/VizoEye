package com.example.vizoeye

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.vizoeye.domain.camera.CameraSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraManager"

class CameraManager(private val context: Context) : CameraSource {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady

    override fun bind(lifecycleOwner: LifecycleOwner) {
        // Для CameraX требуется PreviewView, который мы получаем из UI.
        // Этот метод оставлен для реализации интерфейса, но реальная привязка
        // происходит через bindCamera(previewView, lifecycleOwner).
        // Если мы хотим полной абстракции, нужно передавать PreviewView в интерфейс.
        // Пока оставим как есть для совместимости с текущим UI.
    }

    fun bindCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Настраиваем превью
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Настраиваем захват изображения с ограничением разрешения для скорости
                // 1280x720 достаточно для распознавания объектов и текста крупным планом
                val capture = ImageCapture.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                // Выбираем заднюю камеру
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Отвязываем все предыдущие use cases перед привязкой новых
                provider.unbindAll()

                // Привязываем к lifecycle
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                _isReady.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                _isReady.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override suspend fun capture(): Result<File> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "[PERF] takePhoto called")

        val capture = imageCapture ?: run {
            Log.e(TAG, "Camera not ready")
            return Result.failure(IllegalStateException("Camera not ready"))
        }

        val photoFile = File(
            context.getExternalFilesDir("Pictures"),
            "vizoeye_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        return try {
            // Используем корутину-обертку для асинхронного вызова
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                capture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val saveTime = System.currentTimeMillis()
                            Log.d(TAG, "[PERF] Photo saved in ${saveTime - startTime}ms: ${photoFile.absolutePath}")
                            cont.resume(Result.success(photoFile)) {}
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exception.message}")
                            cont.resume(Result.failure(exception)) {}
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during capture", e)
            Result.failure(e)
        }
    }

    /**
     * Делает снимок и возвращает файл или null в случае ошибки.
     * Удобно для использования в ViewModel без обработки Result.
     */
    suspend fun takePicture(): File? {
        return capture().getOrNull()
    }

    override fun unbind() {
        cameraProvider?.unbindAll()
        _isReady.value = false
    }

    override fun shutdown() {
        unbind()
        cameraExecutor.shutdown()
    }
}
