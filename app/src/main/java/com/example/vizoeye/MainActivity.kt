package com.example.vizoeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import com.example.vizoeye.data.repository.AiRepositoryImpl
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import com.example.vizoeye.ui.main.MainViewModel
import com.example.vizoeye.ui.main.MainViewModelFactory
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "VizoEyeAI"

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var hasCameraPermission by mutableStateOf(false)
    
    // TTS variables
    private var textToSpeech: TextToSpeech? = null
    private var isDetailedMode by mutableStateOf(false)
    private var speechRate by mutableStateOf(2.0f)
    private var isSpeaking by mutableStateOf(false)
    private var isPaused by mutableStateOf(false)
    private lateinit var settingsManager: SettingsManager
    private var showSettings by mutableStateOf(false)

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Инициализация зависимостей (DI)
        val openRouterService = OpenRouterApiService(settingsManager)
        val geminiService = GeminiApiService(settingsManager)
        val aiRepository = AiRepositoryImpl(openRouterService, geminiService)
        val analyzeImageUseCase = AnalyzeImageUseCase(aiRepository)
        
        // Инициализация TTS
        initializeTTS()

        checkPermissions()

        setContent {
            MaterialTheme {
                viewModel = viewModel(
                    factory = MainViewModelFactory(analyzeImageUseCase)
                )

                // Подписка на результаты анализа для озвучки
                LaunchedEffect(viewModel.description.value) {
                    if (viewModel.description.value.isNotEmpty()) {
                        speakText(viewModel.description.value)
                    }
                }

                CameraScreen(
                    hasPermission = hasCameraPermission,
                    onButtonClick = { triggerDescription() },
                    onPermissionRequest = { checkPermissions() },
                    description = viewModel.description.value,
                    status = viewModel.status.value,
                    onImageCaptureReady = { imageCapture = it },
                    onPauseSpeech = { pauseSpeech() },
                    onResumeSpeech = { resumeSpeech() },
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    isAnalyzing = viewModel.isAnalyzing.value,
                    onSpeedUp = { changeSpeechRate(true) },
                    onSpeedDown = { changeSpeechRate(false) },
                    onToggleMode = { toggleDetailedMode() },
                    onSwitchService = {
                        AiServices.switchToNextService()
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
        if (viewModel.isAnalyzing.value) {
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
        if (isPaused && viewModel.description.value.isNotEmpty()) {
            speakText(viewModel.description.value)
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
        viewModel.status.value = if (isDetailedMode) "Режим: ПОДРОБНЫЙ" else "Режим: КРАТКИЙ"
    }

    private fun takePhoto() {
        Log.d(TAG, "takePhoto: начало захвата фото")
        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "takePhoto: imageCapture is null")
            return
        }

        // Используем cameraExecutor для обратного вызова, но логику передаем в ViewModel
        try {
            Log.d(TAG, "takePhoto: создаю файл фото")
            val photoFile = createImageFile()
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            Log.d(TAG, "takePhoto: начинаю захват фото")
            imageCapture.takePicture(
                outputFileOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "takePhoto: фото сохранено: ${photoFile.absolutePath}")
                        Log.d(TAG, "takePhoto: начинаю анализ изображения")
                        // Передаем файл в ViewModel для анализа
                        viewModel.analyzeImage(photoFile, isDetailedMode)
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

    private fun createImageFile(): File {
        val timestamp = System.currentTimeMillis()
        val storageDir = getExternalFilesDir("Pictures")
        return File.createTempFile("vizoeye_${timestamp}_", ".jpg", storageDir)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    val imageCapture = ImageCapture.Builder().build()
                    onImageCaptureReady(imageCapture)
                    
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = status,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onSpeedDown) {
                    Text("-")
                }
                Text(
                    text = "Скорость: %.1fx".format(currentSpeed),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Button(onClick = onSpeedUp) {
                    Text("+")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onButtonClick,
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(if (isAnalyzing) "Анализ..." else "Описать окружение")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onToggleMode) {
                    Text(if (isDetailedMode) "Подробно" else "Кратко")
                }
                Button(onClick = onSwitchService) {
                    Text(currentService)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onOpenSettings) {
                Text("Настройки API")
            }

            if (description.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = description)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isSpeaking && !isPaused) {
                                IconButton(onClick = onPauseSpeech) {
                                    Text("⏸")
                                }
                            } else if (isPaused) {
                                IconButton(onClick = onResumeSpeech) {
                                    Text("▶")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
