package com.example.vizoeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vizoeye.data.remote.GeminiApiService
import com.example.vizoeye.data.remote.OpenRouterApiService
import com.example.vizoeye.data.repository.AiRepositoryImpl
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import com.example.vizoeye.ui.main.MainViewModel
import com.example.vizoeye.ui.main.MainViewModelFactory

private const val TAG = "VizoEyeAI"

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var isDetailedMode by mutableStateOf(false)
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var ttsManager: TtsManager
    private lateinit var cameraManager: CameraManager
    private var showSettings by mutableStateOf(false)

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        ttsManager = TtsManager(this)
        cameraManager = CameraManager(this)

        // Инициализация зависимостей (DI)
        val openRouterService = OpenRouterApiService(settingsManager)
        val geminiService = GeminiApiService(settingsManager)
        val aiRepository = AiRepositoryImpl(openRouterService, geminiService)
        val analyzeImageUseCase = AnalyzeImageUseCase(aiRepository)

        checkPermissions()

        setContent {
            MaterialTheme {
                viewModel = viewModel(
                    factory = MainViewModelFactory(analyzeImageUseCase)
                )

                val isSpeaking by ttsManager.isSpeaking.collectAsStateWithLifecycle()
                val isPaused by ttsManager.isPaused.collectAsStateWithLifecycle()
                val currentSpeed by ttsManager.speechRate.collectAsStateWithLifecycle()
                val imageCapture by cameraManager.imageCapture.collectAsStateWithLifecycle()

                // Подписка на результаты анализа для озвучки
                LaunchedEffect(viewModel.description.value) {
                    if (viewModel.description.value.isNotEmpty()) {
                        ttsManager.speak(viewModel.description.value)
                    }
                }

                CameraScreen(
                    hasPermission = hasCameraPermission,
                    onButtonClick = { triggerDescription() },
                    onPermissionRequest = { checkPermissions() },
                    description = viewModel.description.value,
                    status = viewModel.status.value,
                    onPauseSpeech = { ttsManager.pause() },
                    onResumeSpeech = { ttsManager.resume(viewModel.description.value) },
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    isAnalyzing = viewModel.isAnalyzing.value,
                    onSpeedUp = { ttsManager.changeSpeed(true) },
                    onSpeedDown = { ttsManager.changeSpeed(false) },
                    onToggleMode = { toggleDetailedMode() },
                    onSwitchService = {
                        AiServices.switchToNextService()
                    },
                    currentService = AiServices.getCurrentService().displayName,
                    isDetailedMode = isDetailedMode,
                    currentSpeed = currentSpeed,
                    onOpenSettings = { showSettings = true },
                    cameraManager = cameraManager,
                    viewModel = viewModel
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

            triggerDescription()
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
        
        cameraManager.takePhoto(
            onImageCaptured = { file ->
                Log.d(TAG, "Photo captured, starting analysis")
                viewModel.analyzeImage(file, isDetailedMode)
            },
            onError = { exception ->
                Log.e(TAG, "Camera error: ${exception.message}")
            }
        )
    }

    private fun toggleDetailedMode() {
        isDetailedMode = !isDetailedMode
        Log.d(TAG, "Detailed mode changed to: $isDetailedMode")
        viewModel.status.value = if (isDetailedMode) "Режим: ПОДРОБНЫЙ" else "Режим: КРАТКИЙ"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
        ttsManager.release()
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
    onSpeedUp: () -> Unit,
    onSpeedDown: () -> Unit,
    onToggleMode: () -> Unit,
    onSwitchService: () -> Unit,
    onOpenSettings: () -> Unit,
    currentService: String,
    isDetailedMode: Boolean,
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
        // Интеграция CameraX через AndroidView
        AndroidView(
            factory = { ctx ->
                val previewView = androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }
                // Делегируем настройку камеры менеджеру
                cameraManager.bindCamera(previewView, lifecycleOwner)
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
