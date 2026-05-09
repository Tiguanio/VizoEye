package com.example.vizoeye

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.activity.viewModels
import com.example.vizoeye.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "VizoEyeAI"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var cameraManager: CameraManager

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var soundManager: SoundManager

    private val viewModel: MainViewModel by viewModels()

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
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            MaterialTheme {
                val hasCameraPermission by viewModel.hasCameraPermission.collectAsStateWithLifecycle()
                val isSpeaking by ttsManager.isSpeaking.collectAsStateWithLifecycle()
                val isPaused by ttsManager.isPaused.collectAsStateWithLifecycle()
                val currentSpeed by ttsManager.speechRate.collectAsStateWithLifecycle()
                val status by viewModel.status.collectAsStateWithLifecycle()
                val description by viewModel.description.collectAsStateWithLifecycle()
                val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
                val isDetailedMode by viewModel.isDetailedMode.collectAsStateWithLifecycle()
                val currentService by viewModel.currentService.collectAsStateWithLifecycle()
                val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

                // Подписка на результаты анализа для озвучки уже внутри ViewModel

                CameraScreen(
                    hasPermission = hasCameraPermission,
                    onButtonClick = { triggerDescription() },
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
                    onToggleMode = { viewModel.toggleDetailedMode() },
                    onSwitchService = { viewModel.switchService() },
                    currentService = currentService.displayName,
                    isDetailedMode = isDetailedMode,
                    currentSpeed = currentSpeed,
                    onOpenSettings = { viewModel.setShowSettings(true) },
                    cameraManager = cameraManager,
                    viewModel = viewModel
                )

                if (showSettings) {
                    SettingsDialog(
                        settingsManager = settingsManager,
                        onDismiss = { viewModel.setShowSettings(false) }
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
        viewModel.checkPermissions(this)
        if (!viewModel.hasCameraPermission.value) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun triggerDescription() {
        if (viewModel.isAnalyzing.value) {
            Log.d(TAG, "triggerDescription: анализ уже идет, игнорируем")
            return
        }
        Log.d(TAG, "triggerDescription: кнопка нажата")
        soundManager.playShutterSound()

        cameraManager.takePhoto(
            onImageCaptured = { file ->
                Log.d(TAG, "Photo captured, starting analysis")
                viewModel.analyzeImage(file)
            },
            onError = { exception ->
                Log.e(TAG, "Camera error: ${exception.message}")
                soundManager.playErrorSound()
            }
        )
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
