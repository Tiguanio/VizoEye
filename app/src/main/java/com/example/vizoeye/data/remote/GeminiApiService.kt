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

class GeminiApiService(
    private val settingsManager: SettingsManager,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GeminiApi"
    }

    suspend fun analyzeImage(imageFile: File, isDetailedMode: Boolean): String {
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

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val apiKey = settingsManager.geminiApiKey
        val requestUrl = "${ApiConfig.GEMINI_URL}?key=$apiKey"

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            Log.e(TAG, "Gemini API Error: ${response.code}, Body: $errorBody")
            throw Exception("API Error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
        val jsonResponse = JSONObject(responseBody ?: "")

        if (jsonResponse.has("error")) {
            val error = jsonResponse.getJSONObject("error")
            throw Exception("Gemini Error: ${error.getString("message")}")
        }

        return jsonResponse
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun encodeImageToBase64(imageFile: File): String {
        val inputStream = FileInputStream(imageFile)
        val bytes = inputStream.readBytes()
        inputStream.close()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
