package com.genai.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** High-level state the UI renders. */
enum class EngineState {
    CHECKING,      // querying feature status
    DOWNLOADING,   // model is being downloaded on-device
    READY,         // model available, ready to record
    UNSUPPORTED,   // device cannot run on-device speech recognition
}

/** True for CJK ideographs and Japanese kana (scripts written without spaces). */
private fun isCjk(c: Char): Boolean {
    val code = c.code
    return code in 0x3400..0x9FFF ||   // CJK ideographs (incl. ext-A)
        code in 0xF900..0xFAFF ||      // CJK compatibility ideographs
        code in 0x3040..0x30FF ||      // Hiragana + Katakana
        code in 0xAC00..0xD7A3         // Hangul syllables
}

/** True for ASCII letters/digits — the run we want kept separated from CJK. */
private fun isLatinAlnum(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

/**
 * Inserts a space at every CJK↔Latin boundary so mixed text reads naturally,
 * e.g. "我用android開發app" -> "我用 android 開發 app". This cannot split Latin
 * words the model already glued together (e.g. "androidstudio") because there is
 * no boundary information to split on.
 */
internal fun spaceCjkLatin(s: String): String {
    if (s.length < 2) return s
    val sb = StringBuilder(s.length + 8)
    for (i in s.indices) {
        val c = s[i]
        if (i > 0) {
            val p = s[i - 1]
            val boundary = (isCjk(p) && isLatinAlnum(c)) || (isLatinAlnum(p) && isCjk(c))
            if (boundary) sb.append(' ')
        }
        sb.append(c)
    }
    return sb.toString()
}

/** A language the user can pick, shown in the language menu. */
data class SupportedLocale(val tag: String, val label: String)

/** Languages offered in the UI (all supported by ML Kit GenAI Basic mode). */
val SUPPORTED_LOCALES: List<SupportedLocale> = listOf(
    SupportedLocale("cmn-Hant-TW", "中文(台灣)"),
    SupportedLocale("cmn-Hans-CN", "中文(简体)"),
    SupportedLocale("en-US", "English (US)"),
    SupportedLocale("ja-JP", "日本語"),
    SupportedLocale("ko-KR", "한국어"),
    SupportedLocale("fr-FR", "Français"),
    SupportedLocale("de-DE", "Deutsch"),
    SupportedLocale("es-ES", "Español"),
    SupportedLocale("vi-VN", "Tiếng Việt"),
)

data class SpeechUiState(
    val engine: EngineState = EngineState.CHECKING,
    val isRecording: Boolean = false,
    /** Text finalised so far (accumulated across final segments). */
    val finalText: String = "",
    /** The current in-progress (partial) hypothesis, shown greyed out. */
    val partialText: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val statusDetail: String = "",
    /** Currently selected recognition language tag. */
    val localeTag: String = "cmn-Hant-TW",
)

/**
 * Drives on-device speech recognition via the ML Kit GenAI Speech Recognition
 * API (Basic mode). No API key, everything runs on the device. Locale defaults
 * to Traditional Chinese (Taiwan); Basic mode also supports many other locales.
 */
class SpeechViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(SpeechUiState())
    val uiState: StateFlow<SpeechUiState> = _uiState.asStateFlow()

    private var locale: Locale = Locale.forLanguageTag("cmn-Hant-TW")
    private var recognizer: SpeechRecognizer? = null
    private var recognitionJob: Job? = null

    private fun getString(resId: Int): String =
        getApplication<Application>().getString(resId)

    init {
        prepare()
    }

    /**
     * Switches the recognition language. Stops any recording, releases the
     * current recognizer, and re-checks availability for the new locale
     * (downloading its model if needed).
     */
    fun setLocale(tag: String) {
        if (tag == _uiState.value.localeTag) return
        recognitionJob?.cancel()
        recognizer?.close()
        recognizer = null
        locale = Locale.forLanguageTag(tag)
        _uiState.update {
            it.copy(
                localeTag = tag,
                isRecording = false,
                finalText = "",
                partialText = "",
                statusDetail = "",
            )
        }
        prepare()
    }

    /** Checks feature availability and downloads the model if needed. */
    fun prepare() {
        viewModelScope.launch {
            _uiState.update { it.copy(engine = EngineState.CHECKING) }
            try {
                val client = recognizer ?: SpeechRecognition.getClient(
                    speechRecognizerOptions {
                        locale = this@SpeechViewModel.locale
                        preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
                    }
                ).also { recognizer = it }

                when (client.checkStatus()) {
                    FeatureStatus.AVAILABLE ->
                        _uiState.update { it.copy(engine = EngineState.READY) }

                    FeatureStatus.UNAVAILABLE ->
                        _uiState.update {
                            it.copy(
                                engine = EngineState.UNSUPPORTED,
                                statusDetail = getString(R.string.err_unsupported),
                            )
                        }

                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                        downloadModel(client)
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        engine = EngineState.UNSUPPORTED,
                        statusDetail = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private suspend fun downloadModel(client: SpeechRecognizer) {
        _uiState.update { it.copy(engine = EngineState.DOWNLOADING, downloadedBytes = 0, totalBytes = 0) }
        client.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted ->
                    _uiState.update { it.copy(totalBytes = status.bytesToDownload) }

                is DownloadStatus.DownloadProgress ->
                    _uiState.update { it.copy(downloadedBytes = status.totalBytesDownloaded) }

                is DownloadStatus.DownloadCompleted ->
                    _uiState.update { it.copy(engine = EngineState.READY) }

                is DownloadStatus.DownloadFailed ->
                    _uiState.update {
                        it.copy(
                            engine = EngineState.UNSUPPORTED,
                            statusDetail = status.e.message ?: getString(R.string.err_download_failed),
                        )
                    }
            }
        }
    }

    /** Starts recording from the mic and streaming recognition results. */
    fun startRecording() {
        val client = recognizer ?: return
        if (_uiState.value.engine != EngineState.READY || _uiState.value.isRecording) return

        _uiState.update { it.copy(isRecording = true, partialText = "") }

        recognitionJob = viewModelScope.launch {
            try {
                val request = speechRecognizerRequest {
                    audioSource = AudioSource.fromMic()
                }
                client.startRecognition(request).collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse ->
                            _uiState.update { it.copy(partialText = spaceCjkLatin(response.text)) }

                        is SpeechRecognizerResponse.FinalTextResponse ->
                            _uiState.update {
                                val joined = spaceCjkLatin((it.finalText + response.text).trimStart())
                                it.copy(finalText = joined, partialText = "")
                            }

                        is SpeechRecognizerResponse.CompletedResponse ->
                            _uiState.update { it.copy(isRecording = false, partialText = "") }

                        is SpeechRecognizerResponse.ErrorResponse ->
                            _uiState.update {
                                it.copy(
                                    isRecording = false,
                                    statusDetail = response.e.message ?: getString(R.string.err_recognition),
                                )
                            }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isRecording = false, statusDetail = t.message ?: t.javaClass.simpleName)
                }
            }
        }
    }

    /** Stops the current recording session. */
    fun stopRecording() {
        val client = recognizer ?: return
        viewModelScope.launch {
            try {
                client.stopRecognition()
            } catch (_: Throwable) {
                // ignore — we still flip the UI back to idle below
            }
            _uiState.update { it.copy(isRecording = false, partialText = "") }
        }
    }

    fun clearText() {
        _uiState.update { it.copy(finalText = "", partialText = "") }
    }

    override fun onCleared() {
        recognitionJob?.cancel()
        recognizer?.close()
        super.onCleared()
    }
}
