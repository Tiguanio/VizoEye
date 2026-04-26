package com.example.vizoeye.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase

class MainViewModelFactory(
    private val analyzeImageUseCase: AnalyzeImageUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(analyzeImageUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
