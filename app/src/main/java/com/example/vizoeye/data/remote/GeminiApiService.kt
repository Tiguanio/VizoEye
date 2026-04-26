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

class GeminiApiService(
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "GeminiApi"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeImage(imageFile: File, isDetailedMode: Boolean): String? {
        val base64Image = encodeImageToBase64(imageFile)
        val prompt = if (isDetailedMode) {
            "Опиши подробно что изображено на фотографии. ВНИМАНИЕ: если на фото есть текст, сначала прочитай весь текст дословно, потом опиши остальное содержимое. Если это документ с несколькими страницами, опиши что видишь и перечисли основные разделы. Ответ на русском языке."
        } else {
            "Опиши кратко что на фото. Только главные объекты и люди. ВНИМАНИЕ: если на фото есть текст, прочитай только основные слова и фразы. Ответ на русском языке, максимально коротко."
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
        }

        return try {
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val apiKey = settingsManager.geminiApiKey
            val requestUrl = "${ApiConfig.GEMINI_URL}?key=$apiKey"

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "")
                jsonResponse
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            } else {
                Log.e(TAG, "Gemini API Error: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini analysis error", e)
            null
        }
    }

    private fun encodeImageToBase64(imageFile: File): String {
        val inputStream = FileInputStream(imageFile)
        val bytes = inputStream.readBytes()
        inputStream.close()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
