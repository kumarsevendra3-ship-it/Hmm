package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.actions.DeviceActionExecutor
import com.example.audio.AudioRecordManager
import com.example.audio.AudioTrackPlayer
import com.example.live.GeminiLiveSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AssistantState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class ArushiUiState(
    val state: AssistantState = AssistantState.IDLE,
    val isMicActive: Boolean = false,
    val isLiveConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val audioLevel: Float = 0f,
    val transcript: String = "Tap the microphone to speak with Arushi",
    val actionMessage: String? = null,
    val errorMessage: String? = null,
    val hasApiKey: Boolean = true,
    val debugLogs: List<String> = emptyList()
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArushiUiState())
    val uiState: StateFlow<ArushiUiState> = _uiState.asStateFlow()

    private val actionExecutor = DeviceActionExecutor(application.applicationContext)

    private val audioTrackPlayer = AudioTrackPlayer(
        coroutineScope = viewModelScope,
        onPlaybackStateChanged = { isSpeaking ->
            _uiState.update { current ->
                val nextState = when {
                    isSpeaking -> AssistantState.SPEAKING
                    current.isMicActive -> AssistantState.LISTENING
                    current.errorMessage != null -> AssistantState.ERROR
                    else -> AssistantState.IDLE
                }
                current.copy(state = nextState)
            }
        },
        onAmplitudeChanged = { amp ->
            if (_uiState.value.state == AssistantState.SPEAKING) {
                _uiState.update { it.copy(audioLevel = amp) }
            }
        },
        onLog = { log(it) }
    )

    private val liveSession: GeminiLiveSession

    private val audioRecordManager = AudioRecordManager(
        context = application.applicationContext,
        coroutineScope = viewModelScope,
        onAudioChunk = { base64Data, micAmp ->
            liveSession.sendAudioChunk(base64Data)
            if (_uiState.value.state == AssistantState.LISTENING) {
                _uiState.update { it.copy(audioLevel = micAmp) }
            }
        },
        onError = { err ->
            _uiState.update { it.copy(state = AssistantState.ERROR, errorMessage = err) }
        },
        onLog = { log(it) }
    )

    init {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        val hasValidKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
        _uiState.update { it.copy(hasApiKey = hasValidKey) }

        liveSession = GeminiLiveSession(
            context = application.applicationContext,
            apiKey = apiKey,
            coroutineScope = viewModelScope,
            audioTrackPlayer = audioTrackPlayer,
            deviceActionExecutor = actionExecutor,
            onConnectionStateChanged = { connected, connecting ->
                _uiState.update { current ->
                    val nextState = when {
                        connecting -> AssistantState.CONNECTING
                        connected && current.isMicActive -> AssistantState.LISTENING
                        connected -> AssistantState.IDLE
                        current.errorMessage != null -> AssistantState.ERROR
                        else -> AssistantState.IDLE
                    }
                    current.copy(
                        isLiveConnected = connected,
                        isConnecting = connecting,
                        state = nextState
                    )
                }
            },
            onAssistantTranscript = { text ->
                _uiState.update { it.copy(transcript = text) }
            },
            onActionFeedback = { actionMsg ->
                _uiState.update { it.copy(actionMessage = actionMsg) }
            },
            onError = { err ->
                _uiState.update {
                    it.copy(
                        state = AssistantState.ERROR,
                        errorMessage = err
                    )
                }
            },
            onLog = { log(it) }
        )

        log("Arushi AI initialized. Ready for real-time voice-to-voice interaction.")
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$time] $message"
        _uiState.update { state ->
            val updated = (listOf(logLine) + state.debugLogs).take(60)
            state.copy(debugLogs = updated)
        }
    }

    fun toggleVoiceAssistant(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            _uiState.update {
                it.copy(
                    state = AssistantState.ERROR,
                    errorMessage = "Microphone permission required to talk with Arushi"
                )
            }
            return
        }

        if (_uiState.value.isMicActive) {
            // Stop session
            stopAssistant()
        } else {
            // Start session
            startAssistant()
        }
    }

    fun startAssistant() {
        log("Starting voice assistant session...")
        _uiState.update {
            it.copy(
                isMicActive = true,
                errorMessage = null,
                state = AssistantState.CONNECTING,
                transcript = "Connecting to Gemini Live..."
            )
        }

        // Connect Gemini Live WebSocket
        liveSession.connect()

        // Start Microphone capture
        val micStarted = audioRecordManager.startRecording()
        if (micStarted) {
            _uiState.update {
                it.copy(
                    isMicActive = true,
                    state = AssistantState.LISTENING,
                    transcript = "I'm listening! Speak to me in any language..."
                )
            }
        }
    }

    fun stopAssistant() {
        log("Stopping voice assistant session...")
        audioRecordManager.stopRecording()
        audioTrackPlayer.stopAndClear()
        liveSession.disconnect()
        _uiState.update {
            it.copy(
                isMicActive = false,
                isLiveConnected = false,
                isConnecting = false,
                state = AssistantState.IDLE,
                audioLevel = 0f,
                transcript = "Tap the microphone to speak with Arushi"
            )
        }
    }

    fun testSpeaker() {
        log("Manual diagnostic speaker test initiated")
        audioTrackPlayer.playTestTone(440f, 1500)
        _uiState.update {
            it.copy(actionMessage = "Playing 440Hz speaker test tone...")
        }
    }

    fun sendQuickPrompt(prompt: String) {
        log("Quick prompt sent: $prompt")
        _uiState.update {
            it.copy(
                transcript = "You: $prompt",
                state = AssistantState.CONNECTING
            )
        }
        liveSession.sendTextMessage(prompt)
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, state = AssistantState.IDLE) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecordManager.stopRecording()
        audioTrackPlayer.release()
        liveSession.disconnect()
    }
}
