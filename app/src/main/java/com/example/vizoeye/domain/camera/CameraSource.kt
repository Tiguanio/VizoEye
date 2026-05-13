package com.example.vizoeye.domain.camera

import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Абстракция источника изображения (камеры).
 * Позволяет легко подменять реализацию: CameraX, внешняя BLE/WiFi камера, файл и т.д.
 */
interface CameraSource {
    /**
     * Готовность камеры к работе.
     */
    val isReady: StateFlow<Boolean>

    /**
     * Сделать снимок.
     * @return Result с файлом изображения или ошибкой.
     */
    suspend fun capture(): Result<File>

    /**
     * Привязать камеру к lifecycle (для CameraX).
     * Для внешних камер может быть no-op.
     */
    fun bind(lifecycleOwner: LifecycleOwner)

    /**
     * Отвязать камеру и освободить ресурсы.
     */
    fun unbind()

    /**
     * Полное освобождение ресурсов (вызов при уничтожении Activity/Fragment).
     */
    fun shutdown()
}
