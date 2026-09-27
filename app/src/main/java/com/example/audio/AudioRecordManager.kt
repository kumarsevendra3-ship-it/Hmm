package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecordManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onAudioChunk: (base64Pcm: String, amplitude: Float) -> Unit,
    private val onError: (String) -> Unit,
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "ArushiAudioRecord"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 100ms chunks at 16kHz = 1600 samples = 3200 bytes
        private const val CHUNK_SAMPLES = 1600
        private const val CHUNK_BYTES = CHUNK_SAMPLES * 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    private var isRecording = false

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (!hasPermission()) {
            val msg = "Microphone permission not granted"
            Log.e(TAG, msg)
            onLog("Microphone permission requested: denied")
            onError(msg)
            return false
        }

        if (isRecording) {
            Log.d(TAG, "Already recording")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, CHUNK_BYTES * 2)

            onLog("Microphone permission granted")
            onLog("Initializing AudioRecord: sampleRate=$SAMPLE_RATE, bufferSize=$bufferSize")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_RECOGNITION fails
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val err = "AudioRecord failed to initialize"
                Log.e(TAG, err)
                onLog("Error: $err")
                onError(err)
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            onLog("Microphone started successfully")

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(CHUNK_SAMPLES)
                val byteBuffer = ByteArray(CHUNK_BYTES)

                var chunkCounter = 0

                while (isActive && isRecording) {
                    val readShorts = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readShorts > 0) {
                        // Calculate RMS amplitude for UI audio visualizer
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            val sample = buffer[i].toInt()
                            sum += sample * sample

                            // Convert Short to little-endian bytes
                            val byteIndex = i * 2
                            byteBuffer[byteIndex] = (sample and 0xFF).toByte()
                            byteBuffer[byteIndex + 1] = ((sample shr 8) and 0xFF).toByte()
                        }

                        val rms = sqrt(sum / readShorts)
                        val normalizedAmplitude = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()

                        val base64Data = Base64.encodeToString(byteBuffer, 0, readShorts * 2, Base64.NO_WRAP)

                        chunkCounter++
                        if (chunkCounter % 20 == 0) {
                            Log.d(TAG, "Microphone chunk #$chunkCounter created: ${readShorts * 2} bytes, amp: $normalizedAmplitude")
                            onLog("Microphone audio chunk created (#$chunkCounter)")
                        }

                        onAudioChunk(base64Data, normalizedAmplitude)
                    } else if (readShorts < 0) {
                        Log.e(TAG, "AudioRecord read error code: $readShorts")
                        onLog("AudioRecord read error: $readShorts")
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            val err = "Failed to start microphone: ${e.message}"
            Log.e(TAG, err, e)
            onLog("Microphone start failed: ${e.message}")
            onError(err)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        onLog("Microphone stopped")
        try {
            recordingJob?.cancel()
            recordingJob = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "AudioRecord stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }
}
