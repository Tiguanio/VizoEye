package com.example.vizoeye

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraManager"

class CameraManager(private val context: Context) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private val _imageCapture = MutableStateFlow<ImageCapture?>(null)
    val imageCapture: StateFlow<ImageCapture?> = _imageCapture

    fun bindCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Настраиваем превью
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Настраиваем захват изображения
                val imageCapture = ImageCapture.Builder().build()
                _imageCapture.value = imageCapture

                // Выбираем заднюю камеру
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Отвязываем все предыдущие use cases перед привязкой новых
                cameraProvider.unbindAll()
                
                // Привязываем к lifecycle
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(onImageCaptured: (File) -> Unit, onError: (Exception) -> Unit) {
        val capture = _imageCapture.value ?: run {
            onError(IllegalStateException("Camera not ready"))
            return
        }

        val photoFile = File(
            context.getExternalFilesDir("Pictures"),
            "vizoeye_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo saved: ${photoFile.absolutePath}")
                    onImageCaptured(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}")
                    onError(exception)
                }
            }
        )
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
