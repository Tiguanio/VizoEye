package com.example.vizoeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.util.Base64
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*
import androidx.exifinterface.media.ExifInterface
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var hasCameraPermission by mutableStateOf(false)
    private var status by mutableStateOf("Инициализация камеры...")
    private var description by mutableStateOf("")
    private var isAnalyzing by mutableStateOf(false)
    
    // TTS variables
    private var textToSpeech: TextToSpeech? = null
    private var isDetailedMode by mutableStateOf(false)
    private var speechRate by mutableStateOf(2.0f)
    private var isSpeaking by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)
    private lateinit var settingsManager: SettingsManager
    private var showSettings by mutableStateOf(false)
    
    // AI Analysis
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "VizoEyeAI"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Инициализация TTS
        initializeTTS()

        checkPermissions()

        setContent {
            MaterialTheme {
                CameraScreen(
                    hasPermission = hasCameraPermission,
                    onButtonClick = { triggerDescription() },
                    onPermissionRequest = { checkPermissions() },
                    description = description,
                    status = status,
                    onImageCaptureReady = { imageCapture = it },
                    onPauseSpeech = { pauseSpeech() },
                    onResumeSpeech = { resumeSpeech() },
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    isAnalyzing = isAnalyzing,
                    onSpeedUp = { changeSpeechRate(true) },
                    onSpeedDown = { changeSpeechRate(false) },
                    onToggleMode = { toggleDetailedMode() },
                    onSwitchService = { 
                        AiServices.switchToNextService()
                        status = "Сервис: ${AiServices.getCurrentService().displayName}"
                    },
                    currentService = AiServices.getCurrentService().displayName,
                    isDetailedMode = isDetailedMode,
                    currentSpeed = speechRate,
                    onOpenSettings = { showSettings = true }
                )

                if (showSettings) {
                    SettingsDialog(
                        settingsManager = settingsManager,
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }

    // Ловим нажатие Bluetooth-кнопки
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) &&
            event?.repeatCount == 0) {

            takePhoto()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkPermissions() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 101)
        } else {
            hasCameraPermission = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            hasCameraPermission = grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun triggerDescription() {
        if (isAnalyzing) {
            Log.d(TAG, "triggerDescription: анализ уже идет, игнорируем")
            return
        }
        Log.d(TAG, "triggerDescription: кнопка нажата")
        takePhoto()
    }
    
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("ru"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Русский язык не поддерживается")
                } else {
                    Log.d(TAG, "TTS инициализирован с русским языком")
                }
            } else {
                Log.e(TAG, "Ошибка инициализации TTS: $status")
            }
        }
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }
            
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }
            
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
    }
    
    private fun speakText(text: String) {
        if (isSpeaking && !isPaused) {
            textToSpeech?.stop()
        }
        
        // Убираем символы *, # и другие спецсимволы из речи
        val cleanText = text.replace(Regex("[*#]"), "").trim()
        
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vizoeye_utterance")
            // Используем настроенную скорость речи
            putFloat("speech_rate", speechRate)
            // Уменьшаем паузы между словами
            putFloat("pitch", 1.0f)
        }
        
        textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "vizoeye_utterance")
        isSpeaking = true
        isPaused = false
    }
    
    private fun pauseSpeech() {
        if (isSpeaking && !isPaused) {
            textToSpeech?.stop()
            isPaused = true
        }
    }
    
    private fun resumeSpeech() {
        if (isPaused && description.isNotEmpty()) {
            speakText(description)
        }
    }
    
    private fun changeSpeechRate(increase: Boolean) {
        speechRate = if (increase) {
            minOf(speechRate + 0.5f, 3.0f)  // Максимальная скорость 3.0
        } else {
            maxOf(speechRate - 0.5f, 0.5f)  // Минимальная скорость 0.5
        }
        Log.d(TAG, "Speech rate changed to: $speechRate")
    }
    
    private fun toggleDetailedMode() {
        isDetailedMode = !isDetailedMode
        Log.d(TAG, "Detailed mode changed to: $isDetailedMode")
        status = if (isDetailedMode) "Режим: ПОДРОБНЫЙ" else "Режим: КРАТКИЙ"
    }
    
    
    private fun analyzeImage(imageFile: File, onStatus: (String) -> Unit, onResult: (String) -> Unit) {
        if (isAnalyzing) return
        
        scope.launch {
            try {
                isAnalyzing = true
                onStatus("Анализирую изображение...")
                val currentService = AiServices.getCurrentService()
                
                when (currentService) {
                    AiServices.AiService.GEMINI -> analyzeWithGemini(imageFile, onStatus, onResult)
                    AiServices.AiService.OPENROUTER -> analyzeWithOpenRouter(imageFile, onStatus, onResult)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
                onStatus("Ошибка анализа: ${e.message}")
                onResult("Не удалось проанализировать изображение")
            } finally {
                isAnalyzing = false
            }
        }
    }
    
    private suspend fun analyzeWithOpenRouter(imageFile: File, onStatus: (String) -> Unit, onResult: (String) -> Unit) {
        Log.d(TAG, "analyzeWithOpenRouter: начало анализа")
        val model = AiServices.getCurrentModel()
        onStatus("Анализ через $model...")
        
        val base64Image = encodeImageToBase64(imageFile)
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
        
        withContext(Dispatchers.IO) {
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val apiKey = settingsManager.openRouterApiKey
            
            val request = Request.Builder()
                .url(AiServices.getCurrentUrl())
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                // Рекомендуется OpenRouter
                .addHeader("HTTP-Referer", "https://github.com/vizoeye/app")
                .addHeader("X-Title", "VizoEye AI")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "")
                
                try {
                    val description = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    val cleanDescription = description.replace(Regex("[*#]"), "").trim()
                    
                    withContext(Dispatchers.Main) {
                        onResult(cleanDescription)
                        speakText(cleanDescription)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OpenRouter parse error", e)
                    withContext(Dispatchers.Main) {
                        onResult("Ошибка парсинга OpenRouter: ${e.message}")
                    }
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "OpenRouter API Error: ${response.code}, $errorBody")
                withContext(Dispatchers.Main) {
                    onResult("Ошибка OpenRouter: ${response.code}")
                }
            }
        }
    }
    
    private suspend fun analyzeWithGemini(imageFile: File, onStatus: (String) -> Unit, onResult: (String) -> Unit) {
        Log.d(TAG, "analyzeWithGemini: начало анализа")
        onStatus("Анализ через Google Gemini...")
        
        Log.d(TAG, "analyzeWithGemini: кодирую изображение в Base64")
        val base64Image = encodeImageToBase64(imageFile)
        Log.d(TAG, "analyzeWithGemini: размер Base64: ${base64Image.length} символов")
        
        Log.d(TAG, "analyzeWithGemini: создаю JSON запрос")
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            val prompt = if (isDetailedMode) {
                                "Опиши подробно что изображено на фотографии. ВНИМАНИЕ: если на фото есть текст, сначала прочитай весь текст дословно, потом опиши остальное содержимое. Если это документ с несколькими страницами, опиши что видишь и перечисли основные разделы. Ответ на русском языке."
                            } else {
                                "Опиши кратко что на фото. Только главное объекты и люди. ВНИМАНИЕ: если на фото есть текст, прочитай только основные слова и фразы. Ответ на русском языке, максимально коротко."
                            }
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
        Log.d(TAG, "analyzeWithGemini: JSON запрос создан")
        
        withContext(Dispatchers.IO) {
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())
            
            val apiKey = settingsManager.geminiApiKey
            val requestUrl = "${AiServices.getCurrentUrl()}?key=$apiKey"
            Log.d(TAG, "analyzeWithGemini: URL запроса: $requestUrl")
            
            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            Log.d(TAG, "analyzeWithGemini: отправляю запрос к Gemini API")
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "analyzeWithGemini: получен ответ, код: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "analyzeWithGemini: ответ получен, длина: ${responseBody?.length}")
                Log.d(TAG, "analyzeWithGemini: полный ответ: $responseBody")
                val jsonResponse = JSONObject(responseBody ?: "")
                Log.d(TAG, "analyzeWithGemini: парсинг JSON ответа")
                
                try {
                    val description = jsonResponse
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    
                    Log.d(TAG, "analyzeWithGemini: описание получено: $description")
                    Log.d(TAG, "analyzeWithGemini: длина описания: ${description.length} символов")
                    
                    // Очищаем описание от спецсимволов перед озвучиванием
                    val cleanDescription = description.replace(Regex("[*#]"), "").trim()
                    
                    withContext(Dispatchers.Main) {
                        onResult(cleanDescription)
                        speakText(cleanDescription)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "analyzeWithGemini: ошибка парсинга JSON", e)
                    Log.e(TAG, "analyzeWithGemini: структура ответа: ${jsonResponse.toString()}")
                    withContext(Dispatchers.Main) {
                        onResult("Ошибка парсинга ответа: ${e.message}")
                    }
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Gemini API Error: код ${response.code}, тело: $errorBody")
                
                val errorMessage = when (response.code) {
                    400 -> "Ошибка запроса - проверьте промпт"
                    401 -> "Ошибка аутентификации - проверьте API ключ"
                    403 -> "Доступ запрещен - проверьте API ключ или лимиты"
                    404 -> "Модель не найдена - проверьте URL модели"
                    429 -> "Превышен лимит запросов - попробуйте позже"
                    500 -> "Ошибка сервера Gemini - попробуйте позже"
                    503 -> "Сервис недоступен - проверьте подключение к интернету"
                    else -> "Ошибка анализа: ${response.code}"
                }
                
                withContext(Dispatchers.Main) {
                    onResult(errorMessage)
                }
            }
        }
    }
    
    private fun encodeImageToBase64(imageFile: File): String {
        val inputStream = FileInputStream(imageFile)
        val bytes = inputStream.readBytes()
        inputStream.close()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun takePhoto() {
        Log.d(TAG, "takePhoto: начало захвата фото")
        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "takePhoto: imageCapture is null")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "takePhoto: создаю файл фото")
                val photoFile = createImageFile()
                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                
                Log.d(TAG, "takePhoto: начинаю захват фото")
                imageCapture.takePicture(
                    outputFileOptions,
                    ContextCompat.getMainExecutor(this@MainActivity),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "takePhoto: фото сохранено: ${photoFile.absolutePath}")
                            Log.d(TAG, "takePhoto: начинаю анализ изображения")
                            analyzeImage(photoFile, 
                            { newStatus -> 
                                Log.d(TAG, "takePhoto: статус обновлен: $newStatus")
                                status = newStatus 
                            },
                            { result ->
                                Log.d(TAG, "takePhoto: анализ завершен, результат: $result")
                                status = "Анализ завершен"
                                description = result
                                println("→ Описание: $result")
                                // Озвучиваем описание
                                speakText(result)
                            })
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "takePhoto: ошибка сохранения фото: ${exception.message}")
                            println("Ошибка сохранения фото: ${exception.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "takePhoto: ошибка захвата фото: ${e.message}")
                println("Ошибка захвата фото: ${e.message}")
            }
        }
    }

    private fun createImageFile(): java.io.File {
        val timestamp = System.currentTimeMillis()
        val storageDir = getExternalFilesDir("Pictures")
        return java.io.File.createTempFile("vizoeye_${timestamp}_", ".jpg", storageDir)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scope.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    var geminiKey by remember { mutableStateOf(settingsManager.geminiApiKey) }
    var openRouterKey by remember { mutableStateOf(settingsManager.openRouterApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки API ключей") },
        text = {
            Column {
                TextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = openRouterKey,
                    onValueChange = { openRouterKey = it },
                    label = { Text("OpenRouter API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                settingsManager.geminiApiKey = geminiKey
                settingsManager.openRouterApiKey = openRouterKey
                onDismiss()
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun CameraScreen(
    hasPermission: Boolean,
    onButtonClick: () -> Unit,
    onPermissionRequest: () -> Unit,
    description: String,
    status: String,
    onPauseSpeech: () -> Unit,
    onResumeSpeech: () -> Unit,
    isSpeaking: Boolean,
    isPaused: Boolean,
    isAnalyzing: Boolean,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onSpeedUp: () -> Unit,
    onSpeedDown: () -> Unit,
    onToggleMode: () -> Unit,
    onSwitchService: () -> Unit,
    onOpenSettings: () -> Unit,
    currentService: String,
    isDetailedMode: Boolean,
    currentSpeed: Float
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "VizoEye",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(80.dp))
            
            Text(
                text = "Нужно разрешение для использования камеры",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onPermissionRequest,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("Предоставить разрешение")
            }
        }
        return
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCameraInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    // Status обновляется через callback
                } catch (e: Exception) {
                    // Status обновляется через callback
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            // Status обновляется через callback
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            camera = null
            imageCapture = null
            isCameraInitialized = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Превью камеры
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { previewView ->
                cameraProvider?.let { provider ->
                    if (!isCameraInitialized) {
                        try {
                            // Проверяем доступность камер
                            if (provider.availableCameraInfos.isEmpty()) {
                                return@AndroidView
                            }

                            val preview = Preview.Builder()
                                .setTargetRotation(android.view.Surface.ROTATION_0)
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                            
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setTargetRotation(android.view.Surface.ROTATION_0)
                                .build()
                            
                            // Уведомляем MainActivity что imageCapture готов
                            onImageCaptureReady(imageCapture)
                            
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            
                            try {
                                // Отвязываем предыдущую камеру
                                provider.unbindAll()
                                
                                // Привязываем новую камеру
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                                
                                isCameraInitialized = true
                            } catch (exc: Exception) {
                                println("Camera error: ${exc.message}")
                            }
                        } catch (exc: Exception) {
                            println("Provider error: ${exc.message}")
                        }
                    }
                } ?: run {
                    // Ожидание инициализации камеры...
                }
            }
        }

        // Панель управления
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onButtonClick,
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(if (isAnalyzing) "Анализирую..." else "Описать окружение")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка паузы/возобновления речи
            if (isSpeaking || isPaused) {
                Button(
                    onClick = if (isPaused) onResumeSpeech else onPauseSpeech,
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text(if (isPaused) "Продолжить" else "Пауза")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Кнопки настроек скорости
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onSpeedDown,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Тише")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onSpeedUp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Быстрее")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Кнопка режима описания
            Button(
                onClick = onToggleMode,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(if (isDetailedMode) "Быстрый режим" else "Подробный режим")
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка переключения сервиса
            Button(
                onClick = onSwitchService,
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Сервис: $currentService")
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка настроек
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("Настройки API")
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Текущая скорость
            Text(
                text = "Скорость речи: ${String.format("%.1f", currentSpeed)}x",
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Отображение описания от AI
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = description,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bluetooth-кнопка: Громкость вверх/Воспроизведение",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "VizoEye - AI-помощник для незрячих",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}