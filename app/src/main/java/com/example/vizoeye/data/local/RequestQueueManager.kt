package com.example.vizoeye.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RequestQueueManager"

data class QueuedRequest(
    val id: String,
    val imagePath: String,
    val isDetailedMode: Boolean,
    val timestamp: Long
)

@Singleton
class RequestQueueManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val queuedRequests = mutableListOf<QueuedRequest>()

    /**
     * Проверяет наличие активного интернет-соединения
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied when checking network status", e)
            // В случае отсутствия прав считаем, что сеть есть, чтобы не блокировать работу
            // Или можно вернуть false, если мы хотим строгий оффлайн-режим при ошибках
            true 
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network status", e)
            true
        }
    }

    /**
     * Добавляет запрос в очередь
     */
    fun addToQueue(imageFile: File, isDetailedMode: Boolean): QueuedRequest {
        val request = QueuedRequest(
            id = System.currentTimeMillis().toString(),
            imagePath = imageFile.absolutePath,
            isDetailedMode = isDetailedMode,
            timestamp = System.currentTimeMillis()
        )
        synchronized(queuedRequests) {
            queuedRequests.add(request)
            _queueSize.value = queuedRequests.size
        }
        Log.d(TAG, "Request added to queue: ${request.id}, total: ${queuedRequests.size}")
        return request
    }

    /**
     * Получает следующий запрос из очереди
     */
    fun getNextRequest(): QueuedRequest? {
        return synchronized(queuedRequests) {
            if (queuedRequests.isNotEmpty()) {
                val request = queuedRequests.removeAt(0)
                _queueSize.value = queuedRequests.size
                Log.d(TAG, "Request retrieved from queue: ${request.id}, remaining: ${queuedRequests.size}")
                request
            } else {
                null
            }
        }
    }

    /**
     * Очищает очередь
     */
    fun clearQueue() {
        synchronized(queuedRequests) {
            queuedRequests.clear()
            _queueSize.value = 0
            Log.d(TAG, "Queue cleared")
        }
    }

    /**
     * Возвращает все текущие запросы из очереди (для отображения в UI)
     */
    fun getQueuedRequests(): List<QueuedRequest> {
        return synchronized(queuedRequests) {
            queuedRequests.toList()
        }
    }

    /**
     * Удаляет конкретный запрос из очереди по ID
     */
    fun removeFromQueue(requestId: String) {
        synchronized(queuedRequests) {
            queuedRequests.removeAll { it.id == requestId }
            _queueSize.value = queuedRequests.size
            Log.d(TAG, "Request removed from queue: $requestId, remaining: ${queuedRequests.size}")
        }
    }
}
