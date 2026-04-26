package com.example.vizoeye.data.local

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraRepository(
    private val context: Context
) {
    suspend fun takePhoto(imageCapture: ImageCapture): File = suspendCancellableCoroutine { cont ->
        val photoFile = createImageFile()
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cont.resume(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

    private fun createImageFile(): File {
        val timestamp = System.currentTimeMillis()
        val storageDir = context.getExternalFilesDir("Pictures")
        return File.createTempFile("vizoeye_${timestamp}_", ".jpg", storageDir)
    }
}
