package com.example.live

import android.content.Context
import android.util.Log
import com.example.actions.DeviceActionExecutor
import com.example.audio.AudioTrackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveSession(
    private val context: Context,
    private val apiKey: String,
    private val coroutineScope: CoroutineScope,
    private val audioTrackPlayer: AudioTrackPlayer,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val onConnectionStateChanged: (isConnected: Boolean, isConnecting: Boolean) -> Unit,
    private val onAssistantTranscript: (text: String) -> Unit,
    private val onActionFeedback: (message: String) -> Unit,
    private val onError: (String) -> Unit,
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "ArushiLiveSession"
        // Using Gemini Live Multimodal Audio endpoint
        private const val LIVE_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        private const val WS_HOST = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private var heartbeatJob: Job? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for continuous live streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        if (isConnected.get() || isConnecting.get()) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val err = "Gemini API key is missing or not configured in Secrets"
            Log.e(TAG, err)
            onLog("Gemini session error: $err")
            onError(err)
            return
        }

        isConnecting.set(true)
        onConnectionStateChanged(false, true)
        onLog("Gemini session connecting to Live API...")

        val wsUrl = "$WS_HOST?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully")
                isConnected.set(true)
                isConnecting.set(false)
                coroutineScope.launch(Dispatchers.Main) {
                    onConnectionStateChanged(true, false)
                }
                onLog("Gemini session connected")

                // Send setup handshake message
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                onLog("Gemini session closing ($code: $reason)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected.set(false)
                isConnecting.set(false)
                coroutineScope.launch(Dispatchers.Main) {
                    onConnectionStateChanged(false, false)
                }
                onLog("Gemini session disconnected")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected.set(false)
                isConnecting.set(false)
                coroutineScope.launch(Dispatchers.Main) {
                    onConnectionStateChanged(false, false)
                }
                val msg = t.message ?: "Connection failure"
                onLog("Gemini session error: $msg")
                onError("Live connection failed: $msg")
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject()
            val setupObj = JSONObject()
            setupObj.put("model", LIVE_MODEL)

            // Generation config with AUDIO modality and Aoede voice
            val genConfig = JSONObject()
            val modalities = JSONArray().apply { put("AUDIO") }
            genConfig.put("responseModalities", modalities)

            val voiceConfig = JSONObject()
            val prebuiltVoiceConfig = JSONObject().put("voiceName", "Aoede")
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)

            val speechConfig = JSONObject().put("voiceConfig", voiceConfig)
            genConfig.put("speechConfig", speechConfig)
            setupObj.put("generationConfig", genConfig)

            // Arushi System Instruction
            val systemInstruction = JSONObject()
            val partsArray = JSONArray()
            val partText = JSONObject().put(
                "text",
                "You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. Automatically understand and respond in the language the user is speaking (Hindi, English, Hinglish, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, etc.). Keep responses natural, engaging, and concise enough for real-time voice conversation. You can execute safe supported device actions through available tools. Never claim that an action was completed unless the application actually executed it. Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality."
            )
            partsArray.put(partText)
            systemInstruction.put("parts", partsArray)
            setupObj.put("systemInstruction", systemInstruction)

            // Function declarations / tools
            val toolsArray = JSONArray()
            val toolsObj = JSONObject()
            val funcDecls = JSONArray()

            // Tool: openWhatsApp
            funcDecls.put(
                JSONObject()
                    .put("name", "openWhatsApp")
                    .put("description", "Opens WhatsApp application on user's device when requested.")
            )

            // Tool: openApp
            val openAppParams = JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject().put(
                        "appName",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Name of the app to launch, e.g. YouTube, Instagram, Spotify, Chrome, Camera, Maps, Settings, Calculator, Calendar")
                    )
                )
                .put("required", JSONArray().apply { put("appName") })

            funcDecls.put(
                JSONObject()
                    .put("name", "openApp")
                    .put("description", "Opens a supported mobile app (YouTube, Instagram, Spotify, Chrome, Camera, Maps, Settings, Calculator, Calendar) on the device.")
                    .put("parameters", openAppParams)
            )

            // Tool: openUrl
            val openUrlParams = JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject().put(
                        "url",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "The website URL to open in browser, e.g. https://google.com")
                    )
                )
                .put("required", JSONArray().apply { put("url") })

            funcDecls.put(
                JSONObject()
                    .put("name", "openUrl")
                    .put("description", "Opens an external web URL safely in the device browser.")
                    .put("parameters", openUrlParams)
            )

            // Tool: makeCall
            val makeCallParams = JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject().put(
                        "phoneNumber",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "The phone number to dial")
                    )
                )
                .put("required", JSONArray().apply { put("phoneNumber") })

            funcDecls.put(
                JSONObject()
                    .put("name", "makeCall")
                    .put("description", "Initiates a phone call or opens the dialer with the target phone number.")
                    .put("parameters", makeCallParams)
            )

            // Tool: callContact
            val callContactParams = JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject().put(
                        "contactName",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Name of the contact to call, e.g. Mom, Rahul, Dad, Priya")
                    )
                )
                .put("required", JSONArray().apply { put("contactName") })

            funcDecls.put(
                JSONObject()
                    .put("name", "callContact")
                    .put("description", "Searches device address book contacts by person's name and calls them if found.")
                    .put("parameters", callContactParams)
            )

            toolsObj.put("functionDeclarations", funcDecls)
            toolsArray.put(toolsObj)
            setupObj.put("tools", toolsArray)

            setupJson.put("setup", setupObj)

            val setupPayload = setupJson.toString()
            Log.d(TAG, "Sending setup message: $setupPayload")
            ws.send(setupPayload)
            onLog("Gemini setup message sent with Arushi personality and device tools")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message: ${e.message}", e)
            onLog("Setup message error: ${e.message}")
        }
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        try {
            val root = JSONObject(text)
            onLog("Gemini response received")

            // Check for serverContent
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Handle interruption
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Gemini detected interruption from user")
                    onLog("Audio playback interrupted by user input")
                    audioTrackPlayer.stopAndClear()
                }

                // Handle model turn parts
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio part
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val dataLen = base64Data.length
                                    onLog("Response contains audio ($mimeType, length=$dataLen)")
                                    audioTrackPlayer.enqueueBase64Audio(base64Data, mimeType)
                                }
                            }

                            // Text part / captions
                            if (part.has("text")) {
                                val transcript = part.getString("text")
                                if (transcript.isNotBlank()) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onAssistantTranscript(transcript)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Check for toolCall
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    val responsesArray = JSONArray()
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_$i")
                        val funcName = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        onLog("Gemini requested tool call: $funcName")
                        val result = deviceActionExecutor.execute(funcName, args)

                        coroutineScope.launch(Dispatchers.Main) {
                            onActionFeedback(result.message)
                        }

                        val funcResp = JSONObject()
                        funcResp.put("id", callId)
                        funcResp.put("name", funcName)
                        val responseBody = JSONObject()
                        responseBody.put("output", result.outputJson)
                        funcResp.put("response", responseBody)

                        responsesArray.put(funcResp)
                    }

                    // Send toolResponse back to Gemini Live
                    val toolResponseMsg = JSONObject()
                    val trObj = JSONObject()
                    trObj.put("functionResponses", responsesArray)
                    toolResponseMsg.put("toolResponse", trObj)

                    ws.send(toolResponseMsg.toString())
                    onLog("Tool response sent back to Gemini Live")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Live API message: ${e.message}", e)
            onLog("Error parsing Gemini response: ${e.message}")
        }
    }

    /**
     * Sends microphone PCM audio chunks (16kHz 16-bit mono PCM Base64) to the Live session
     */
    fun sendAudioChunk(base64Pcm: String) {
        if (!isConnected.get() || webSocket == null) {
            return
        }

        try {
            val msg = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()

            val chunk = JSONObject()
            chunk.put("mimeType", "audio/pcm;rate=16000")
            chunk.put("data", base64Pcm)
            mediaChunks.put(chunk)

            realtimeInput.put("mediaChunks", mediaChunks)
            msg.put("realtimeInput", realtimeInput)

            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk: ${e.message}")
        }
    }

    /**
     * Send direct text prompt if user types or quick-actions trigger it
     */
    fun sendTextMessage(text: String) {
        if (!isConnected.get() || webSocket == null) {
            connect()
        }

        coroutineScope.launch(Dispatchers.IO) {
            var retries = 0
            while (!isConnected.get() && retries < 20) {
                delay(150)
                retries++
            }

            try {
                val msg = JSONObject()
                val clientContent = JSONObject()
                val turns = JSONArray()
                val turn = JSONObject()
                turn.put("role", "user")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", text))
                turn.put("parts", parts)
                turns.put(turn)

                clientContent.put("turns", turns)
                clientContent.put("turnComplete", true)
                msg.put("clientContent", clientContent)

                webSocket?.send(msg.toString())
                onLog("User message sent: \"$text\"")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send text message: ${e.message}")
            }
        }
    }

    fun disconnect() {
        onLog("Disconnecting Gemini Live session")
        try {
            webSocket?.close(1000, "User requested disconnect")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket: ${e.message}")
        } finally {
            isConnected.set(false)
            isConnecting.set(false)
            onConnectionStateChanged(false, false)
        }
    }
}
