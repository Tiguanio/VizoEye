package com.example.vizoeye.data.remote

import android.util.Base64
import android.util.Log
import com.example.vizoeye.ApiConfig
import com.example.vizoeye.SettingsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class OpenRouterApiService(
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "OpenRouterApi"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeImage(imageFile: File, isDetailedMode: Boolean): String? {
        val base64Image = encodeImageToBase64(imageFile)
        val model = ApiConfig.OPENROUTER_MODEL
        val prompt = if (isDetailedMode) {
            "Опиши подробно что изображено на фотографии. ВНИМАНИЕ: если на фото есть текст, сначала прочитай весь текст дословно, потом опиши остальное содержимое. Если это документ с несколькими страницами, опиши что видишь и перечисли основные разделы. Ответ на русском языке."
        } else {
            "Опиши кратко что на фото. Только главное объекты и люди. ВНИМАНИЕ: если на фото есть текст, прочитай только основные слова и фразы. Ответ на русском языке, максимально коротко."
        }

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    })
                })
            })
        }

        return try {
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val apiKey = settingsManager.openRouterApiKey

            val request = Request.Builder()
                .url(ApiConfig.OPENROUTER_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/vizoeye/app")
                .addHeader("X-Title", "VizoEye AI")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "OpenRouter API Error: ${response.code}, Body: $errorBody")
                throw Exception("API Error ${response.code}: $errorBody")
            }

            val responseBody = response.body?.string()
            Log.d(TAG, "OpenRouter Response: $responseBody")
            
            val jsonResponse = JSONObject(responseBody ?: "")
            jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter analysis failed", e)
            throw e
        }
    }

    private fun encodeImageToBase64(imageFile: File): String {
        val inputStream = FileInputStream(imageFile)
        val bytes = inputStream.readBytes()
        inputStream.close()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
