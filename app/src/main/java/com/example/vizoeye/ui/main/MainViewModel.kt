package com.example.vizoeye.ui.main

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val analyzeImageUseCase: AnalyzeImageUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    var status = mutableStateOf("Готово к работе")
        private set
    var description = mutableStateOf("")
        private set
    var isAnalyzing = mutableStateOf(false)
        private set

    fun analyzeImage(imageFile: File, isDetailedMode: Boolean) {
        if (isAnalyzing.value) return

        viewModelScope.launch {
            try {
                isAnalyzing.value = true
                status.value = "Анализирую изображение..."
                Log.d(TAG, "Начало анализа файла: ${imageFile.absolutePath}")

                val result = withContext(Dispatchers.IO) {
                    analyzeImageUseCase(imageFile, isDetailedMode)
                }

                if (result.isSuccess) {
                    description.value = result.description
                    status.value = "Анализ завершен"
                    Log.d(TAG, "Результат получен: ${result.description.take(50)}...")
                } else {
                    status.value = "Ошибка: ${result.errorMessage}"
                    description.value = result.description
                    Log.e(TAG, "Ошибка анализа: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                status.value = "Критическая ошибка: ${e.message}"
                Log.e(TAG, "Unexpected error", e)
            } finally {
                isAnalyzing.value = false
            }
        }
    }
}
