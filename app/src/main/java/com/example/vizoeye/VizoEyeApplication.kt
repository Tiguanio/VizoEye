package com.example.vizoeye

import android.app.Application

class VizoEyeApplication : Application() {
    // Глобальный доступ к контейнеру зависимостей
    val container by lazy { AppContainer(this) }
}
