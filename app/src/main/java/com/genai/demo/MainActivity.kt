package com.genai.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GenAIDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SpeechScreen()
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SpeechScreen(vm: SpeechViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) vm.startRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    LanguageMenu(
                        currentTag = state.localeTag,
                        enabled = !state.isRecording,
                        onSelect = vm::setLocale,
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (state.engine) {
                EngineState.CHECKING -> CenteredStatus(
                    modifier = Modifier.padding(padding),
                    spinner = true,
                    text = stringResource(R.string.checking),
                )

                EngineState.DOWNLOADING -> DownloadingScreen(
                    modifier = Modifier.padding(padding),
                    downloaded = state.downloadedBytes,
                    total = state.totalBytes,
                    onRetry = vm::prepare,
                )

                EngineState.UNSUPPORTED -> UnsupportedScreen(
                    modifier = Modifier.padding(padding),
                    detail = state.statusDetail,
                    onRetry = vm::prepare,
                )

                EngineState.READY -> RecognizerScreen(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onMicClick = {
                        if (state.isRecording) {
                            vm.stopRecording()
                        } else if (hasAudioPermission) {
                            vm.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onClear = vm::clearText,
                )
            }

            // Version info, bottom-right corner.
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(padding)
                    .padding(end = 12.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun LanguageMenu(
    currentTag: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = SUPPORTED_LOCALES.firstOrNull { it.tag == currentTag }?.label ?: currentTag
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                Icons.Filled.Language,
                contentDescription = stringResource(R.string.lang_select),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            Text(currentLabel, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUPPORTED_LOCALES.forEach { loc ->
                DropdownMenuItem(
                    text = { Text(loc.label) },
                    onClick = {
                        expanded = false
                        onSelect(loc.tag)
                    },
                    trailingIcon = if (loc.tag == currentTag) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun RecognizerScreen(
    modifier: Modifier = Modifier,
    state: SpeechUiState,
    onMicClick: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Transcript area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.large,
                )
                .padding(16.dp),
        ) {
            val scroll = rememberScrollState()
            if (state.finalText.isEmpty() && state.partialText.isEmpty()) {
                Text(
                    stringResource(R.string.transcript_placeholder),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Text(
                        state.finalText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.partialText.isNotEmpty()) {
                        Text(
                            state.partialText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (state.finalText.isNotEmpty() && !state.isRecording) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
        } else {
            Spacer(Modifier.height(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        MicButton(recording = state.isRecording, onClick = onMicClick)

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(if (state.isRecording) R.string.listening else R.string.tap_to_speak),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MicButton(recording: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (recording) 1.12f else 1f,
        animationSpec = tween(250),
        label = "micScale",
    )
    val bg = if (recording) Color(0xFFE0463C) else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(96.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = stringResource(if (recording) R.string.mic_stop else R.string.mic_start),
            tint = Color.White,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun CenteredStatus(
    modifier: Modifier = Modifier,
    spinner: Boolean = false,
    text: String,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (spinner) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(text, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DownloadingScreen(
    modifier: Modifier = Modifier,
    downloaded: Long,
    total: Long,
    onRetry: () -> Unit,
) {
    fun mb(bytes: Long) = String.format("%.1f MB", bytes / 1_048_576.0)
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (total > 0) {
                val progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                CircularProgressIndicator(progress = { progress })
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.downloading_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${mb(downloaded)} / ${mb(total)}  (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.downloading_once),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            } else {
                // The system downloads the language model in the background and
                // often does NOT report progress to the app, so tell the user
                // what's happening and let them re-check when it finishes.
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.downloading_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.downloading_bg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.recheck)) }
            }
        }
    }
}

@Composable
private fun UnsupportedScreen(
    modifier: Modifier = Modifier,
    detail: String,
    onRetry: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎙️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.unsupported_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.unsupported_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.recheck)) }
        }
    }
}
