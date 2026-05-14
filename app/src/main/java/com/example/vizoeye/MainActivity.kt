package com.example.vizoeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.vizoeye.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "VizoEyeAI"

class MainActivity : ComponentActivity() {

    private val appContainer by lazy {
        val start = System.currentTimeMillis()
        val container = (application as VizoEyeApplication).container
        Log.d(TAG, "[PERF] AppContainer init: ${System.currentTimeMillis() - start} ms")
        container
    }

    private lateinit var viewModel: MainViewModel

    private val cameraManager get() = appContainer.cameraManager
    private val ttsManager get() = appContainer.ttsManager
    private val settingsManager get() = appContainer.settingsManager
    private val soundManager get() = appContainer.soundManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        viewModel.requestPermissionsResult(
            if (allGranted) IntArray(permissions.size) { PackageManager.PERMISSION_GRANTED }
            else IntArray(permissions.size) { PackageManager.PERMISSION_DENIED }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityStart = System.currentTimeMillis()
        Log.d(TAG, "[PERF] Activity onCreate START")
        super.onCreate(savedInstanceState)

        // Инициализируем ViewModel вручную
        val vmStart = System.currentTimeMillis()
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    analyzeImageUseCase = appContainer.analyzeImageUseCase,
                    voiceAnalyzeUseCase = appContainer.voiceAnalyzeUseCase,
                    ttsManager = appContainer.ttsManager,
                    soundManager = appContainer.soundManager,
                    requestQueueManager = appContainer.requestQueueManager,
                    aiServiceRepository = appContainer.aiServiceRepository,
                    cameraManager = appContainer.cameraManager
                ) as T
            }
        })[MainViewModel::class.java]
        Log.d(TAG, "[PERF] ViewModel init: ${System.currentTimeMillis() - vmStart} ms")

        checkPermissions()
        Log.d(TAG, "[PERF] Activity onCreate END (total): ${System.currentTimeMillis() - activityStart} ms")

        setContent {
            MaterialTheme {
                val hasCameraPermission by viewModel.hasCameraPermission.collectAsStateWithLifecycle()
                val isSpeaking by ttsManager.isSpeaking.collectAsStateWithLifecycle()
                val isPaused by ttsManager.isPaused.collectAsStateWithLifecycle()
                val currentSpeed by ttsManager.speechRate.collectAsStateWithLifecycle()
                val status by viewModel.status.collectAsStateWithLifecycle()
                val description by viewModel.description.collectAsStateWithLifecycle()
                val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
                val currentService by viewModel.currentService.collectAsStateWithLifecycle()
                val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

                CameraScreen(
                    hasPermission = hasCameraPermission,
                    onBriefClick = { triggerAnalysis(isDetailed = false) },
                    onDetailedClick = { triggerAnalysis(isDetailed = true) },
                    onVoiceClick = { triggerVoiceQuestion() },
                    onPermissionRequest = { viewModel.checkPermissions(this@MainActivity) },
                    description = description,
                    status = status,
                    onPauseSpeech = { ttsManager.pause() },
                    onResumeSpeech = { ttsManager.resume(description) },
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    isAnalyzing = isAnalyzing,
                    onSpeedUp = { ttsManager.changeSpeed(true) },
                    onSpeedDown = { ttsManager.changeSpeed(false) },
                    currentService = currentService.displayName,
                    currentSpeed = currentSpeed,
                    onOpenSettings = { viewModel.setShowSettings(true) },
                    cameraManager = cameraManager,
                    viewModel = viewModel
                )

                if (showSettings) {
                    SettingsDialog(
                        settingsManager = settingsManager,
                        aiServiceRepository = appContainer.aiServiceRepository,
                        onDismiss = { viewModel.setShowSettings(false) }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        viewModel.checkPermissions(this)
        if (!viewModel.hasCameraPermission.value) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun triggerAnalysis(isDetailed: Boolean) {
        if (viewModel.isAnalyzing.value) {
            Log.d(TAG, "triggerAnalysis: анализ уже идет, игнорируем")
            return
        }
        Log.d(TAG, "triggerAnalysis: кнопка нажата (detailed=$isDetailed)")
        soundManager.playShutterSound()

        CoroutineScope(Dispatchers.Main).launch {
            val result = cameraManager.capture()
            result.onSuccess { file ->
                Log.d(TAG, "Photo captured, starting analysis")
                if (isDetailed) {
                    viewModel.analyzeDetailed(file)
                } else {
                    viewModel.analyzeBriefly(file)
                }
            }.onFailure { exception ->
                Log.e(TAG, "Camera error: ${exception.message}")
                soundManager.playErrorSound()
            }
        }
    }

    private fun triggerVoiceQuestion() {
        if (viewModel.isAnalyzing.value) {
            Log.d(TAG, "triggerVoiceQuestion: анализ уже идет, игнорируем")
            return
        }
        Log.d(TAG, "triggerVoiceQuestion: голосовой режим")
        viewModel.startVoiceQuestion()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (viewModel.isAnalyzing.value && viewModel.status.value.contains("Слушаю")) {
                Log.d(TAG, "Volume Down pressed: stopping voice recording")
                viewModel.stopVoiceQuestion()
                return true // Перехватываем событие, чтобы не менять громкость
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
        ttsManager.release()
        soundManager.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settingsManager: SettingsManager,
    aiServiceRepository: com.example.vizoeye.data.repository.AiServiceRepository,
    onDismiss: () -> Unit
) {
    var geminiKey by remember { mutableStateOf(settingsManager.geminiApiKey) }
    var openRouterKey by remember { mutableStateOf(settingsManager.openRouterApiKey) }
    val currentProvider by aiServiceRepository.currentProvider.collectAsStateWithLifecycle(
        initialValue = aiServiceRepository.getCurrentProviderSync()
    )

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
                Spacer(modifier = Modifier.height(16.dp))
                Text("Активный провайдер: ${currentProvider.displayName}")
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
    onBriefClick: () -> Unit,
    onDetailedClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onPermissionRequest: () -> Unit,
    description: String,
    status: String,
    onPauseSpeech: () -> Unit,
    onResumeSpeech: () -> Unit,
    isSpeaking: Boolean,
    isPaused: Boolean,
    isAnalyzing: Boolean,
    onSpeedUp: () -> Unit,
    onSpeedDown: () -> Unit,
    onOpenSettings: () -> Unit,
    currentService: String,
    currentSpeed: Float,
    cameraManager: CameraManager,
    viewModel: MainViewModel
) {
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
                text = "Нужно разрешение для использования камеры и микрофона",
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
        // Камера на фоне
        AndroidView(
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }
                cameraManager.bindCamera(previewView, lifecycleOwner)
                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (viewModel.isAnalyzing.value && viewModel.status.value.contains("Слушаю")) {
                            Log.d(TAG, "Double tap detected: stopping voice recording")
                            viewModel.stopVoiceQuestion()
                        }
                    })
                }
        )

        // Статус поверх камеры
        Text(
            text = status,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Основной контент снизу
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Регулятор скорости TTS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Регулировка скорости речи. Текущая скорость: %.1fx".format(currentSpeed) },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSpeedDown,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = "Уменьшить скорость речи" }
                ) {
                    Text("-", fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                }
                Text(
                    text = "Скорость: %.1fx".format(currentSpeed),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.semantics { contentDescription = "Текущая скорость речи: %.1fx".format(currentSpeed) }
                )
                Button(
                    onClick = onSpeedUp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = "Увеличить скорость речи" }
                ) {
                    Text("+", fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Три большие кнопки
            ElevatedButton(
                onClick = onBriefClick,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { contentDescription = "Краткий режим. Двойной тап для съёмки и быстрого описания" }
            ) {
                Text("КРАТКО", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            ElevatedButton(
                onClick = onDetailedClick,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { contentDescription = "Подробный режим. Двойной тап для съёмки и детального описания" }
            ) {
                Text("ПОДРОБНО", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onVoiceClick,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { contentDescription = "Голосовой режим. Двойной тап для записи вопроса" }
            ) {
                Text("ВОПРОС", fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Панель управления сервисами
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.switchAiService() },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Сервис: $currentService")
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Настройки API")
                }
            }

            // Карточка с описанием
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
