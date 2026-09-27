package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.math.sqrt

class AudioTrackPlayer(
    private val coroutineScope: CoroutineScope,
    private val onPlaybackStateChanged: (isSpeaking: Boolean) -> Unit,
    private val onAmplitudeChanged: (amplitude: Float) -> Unit,
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "ArushiAudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var currentSampleRate = DEFAULT_SAMPLE_RATE
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val isPlaying = AtomicBoolean(false)

    init {
        initAudioTrack(DEFAULT_SAMPLE_RATE)
        startPlaybackLoop()
    }

    @Synchronized
    private fun initAudioTrack(sampleRate: Int) {
        if (audioTrack != null && currentSampleRate == sampleRate && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            return
        }

        try {
            audioTrack?.release()
        } catch (_: Exception) {}

        currentSampleRate = sampleRate
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, sampleRate * 2) // ~1 sec buffer

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        onLog("AudioTrack initialized: sampleRate=$sampleRate, bufferSize=$bufferSize")
        Log.d(TAG, "AudioTrack initialized: state=${audioTrack?.state}")

        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            try {
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AudioTrack playback: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            var chunkIndex = 0
            while (isActive) {
                try {
                    val pcmBytes = audioQueue.receive()
                    if (!isPlaying.get()) {
                        isPlaying.set(true)
                        withContext(Dispatchers.Main) {
                            onPlaybackStateChanged(true)
                        }
                        onLog("Audio playback started")
                    }

                    // Check track state
                    if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                        initAudioTrack(currentSampleRate)
                    }

                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.play()
                    }

                    // Calculate amplitude for speaking visualizer
                    var sum = 0.0
                    val sampleCount = pcmBytes.size / 2
                    for (i in 0 until sampleCount) {
                        val low = pcmBytes[i * 2].toInt() and 0xFF
                        val high = pcmBytes[i * 2 + 1].toInt()
                        val sample = (high shl 8) or low
                        sum += sample * sample
                    }
                    val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
                    val amp = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()

                    withContext(Dispatchers.Main) {
                        onAmplitudeChanged(amp)
                    }

                    chunkIndex++
                    // Write to AudioTrack in blocking stream mode
                    val written = audioTrack?.write(pcmBytes, 0, pcmBytes.size, AudioTrack.WRITE_BLOCKING) ?: -1
                    if (chunkIndex % 10 == 0) {
                        Log.d(TAG, "Wrote chunk #$chunkIndex ($written bytes) to speaker, amp: $amp")
                    }

                    // If queue is empty, wait slightly then report idle speaking state
                    if (audioQueue.isEmpty) {
                        // Short grace period for smooth speech
                        kotlinx.coroutines.delay(120)
                        if (audioQueue.isEmpty && isPlaying.get()) {
                            isPlaying.set(false)
                            withContext(Dispatchers.Main) {
                                onAmplitudeChanged(0f)
                                onPlaybackStateChanged(false)
                            }
                            onLog("Audio playback ended (queue complete)")
                            chunkIndex = 0
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) break
                    Log.e(TAG, "Playback loop error: ${e.message}")
                }
            }
        }
    }

    /**
     * Enqueue base64 PCM data received from Gemini Live
     */
    fun enqueueBase64Audio(base64Data: String, mimeType: String? = null) {
        try {
            // Check sample rate in mimeType, e.g. "audio/pcm;rate=24000" or "audio/pcm;rate=16000"
            mimeType?.let { mime ->
                val rateMatch = Regex("rate=(\\d+)").find(mime)
                val rate = rateMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (rate != null && rate != currentSampleRate) {
                    onLog("Detected audio sample rate change: $rate Hz")
                    initAudioTrack(rate)
                }
            }

            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (pcmBytes.isNotEmpty()) {
                audioQueue.trySend(pcmBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode and enqueue audio: ${e.message}", e)
            onLog("Audio decoding error: ${e.message}")
        }
    }

    /**
     * Clear queue and stop playback on user interruption
     */
    fun stopAndClear() {
        onLog("Audio playback interrupted - clearing queue")
        try {
            // Drain the queue
            while (audioQueue.tryReceive().isSuccess) {
                // discard
            }
            if (isPlaying.getAndSet(false)) {
                coroutineScope.launch(Dispatchers.Main) {
                    onAmplitudeChanged(0f)
                    onPlaybackStateChanged(false)
                }
            }

            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
            }
            Log.d(TAG, "Audio playback interrupted and flushed")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback: ${e.message}")
        }
    }

    /**
     * Speaker diagnostic test: plays a clear 440Hz sine wave tone
     */
    fun playTestTone(frequency: Float = 440f, durationMs: Int = 1200) {
        coroutineScope.launch(Dispatchers.IO) {
            onLog("Speaker test: generating ${frequency}Hz tone")
            try {
                val sampleRate = DEFAULT_SAMPLE_RATE
                initAudioTrack(sampleRate)

                val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
                val pcmData = ByteArray(numSamples * 2)

                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i * frequency / sampleRate
                    // Apply fade-in and fade-out envelope to avoid clicks
                    val envelope = when {
                        i < 400 -> i / 400.0
                        i > numSamples - 400 -> (numSamples - i) / 400.0
                        else -> 1.0
                    }
                    val sample = (sin(angle) * 26000 * envelope).toInt().toShort()
                    pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
                    pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }

                enqueueBase64Audio(Base64.encodeToString(pcmData, Base64.NO_WRAP))
                onLog("Speaker test tone queued successfully (${durationMs}ms)")
            } catch (e: Exception) {
                val err = "Speaker test tone error: ${e.message}"
                Log.e(TAG, err, e)
                onLog(err)
            }
        }
    }

    fun release() {
        try {
            stopAndClear()
            playbackJob?.cancel()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack: ${e.message}")
        }
    }
}
