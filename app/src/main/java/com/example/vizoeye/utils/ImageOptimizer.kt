package com.example.vizoeye.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val TAG = "ImageOptimizer"

object ImageOptimizer {
    // Целевое максимальное разрешение (сохраняем пропорции)
    private const val MAX_WIDTH = 1024
    private const val MAX_HEIGHT = 1024
    private const val COMPRESS_QUALITY = 70 // Качество JPEG от 0 до 100

    fun optimizeImage(inputFile: File): File {
        val startTime = System.currentTimeMillis()
        
        // Декодируем границы изображения, чтобы узнать его размеры
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(inputFile.absolutePath, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            Log.w(TAG, "Invalid image dimensions, returning original file")
            return inputFile
        }

        // Вычисляем коэффициент масштабирования
        var inSampleSize = 1
        var width = originalWidth
        var height = originalHeight

        while (width > MAX_WIDTH || height > MAX_HEIGHT) {
            width /= 2
            height /= 2
            inSampleSize *= 2
        }

        // Декодируем изображение с уменьшением размера
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
            ?: return inputFile

        // Создаем временный файл для оптимизированного изображения
        val optimizedFile = File.createTempFile("optimized_vizoeye", ".jpg", inputFile.parentFile)

        // Сжимаем и сохраняем
        FileOutputStream(optimizedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
        }

        bitmap.recycle()

        val endTime = System.currentTimeMillis()
        val originalSize = inputFile.length() / 1024
        val newSize = optimizedFile.length() / 1024
        Log.d(TAG, "[PERF] Image optimized in ${endTime - startTime}ms: ${originalSize}KB -> ${newSize}KB (${width}x${height})")

        return optimizedFile
    }
}
