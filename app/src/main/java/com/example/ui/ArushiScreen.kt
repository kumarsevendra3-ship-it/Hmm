package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showLogsSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Permissions
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startAssistant()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required to talk with Arushi")
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Contacts permission granted! You can now call contacts by name.")
            }
        }
    }

    // Trigger toast on action feedback
    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F081D),
                        Color(0xFF0A0514),
                        Color(0xFF06030A)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFD946EF), Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Arushi Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Arushi",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Live badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (uiState.isLiveConnected) Color(0xFF10B981).copy(alpha = 0.2f)
                                        else Color(0xFF6B7280).copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (uiState.isLiveConnected) "LIVE" else "VOICE",
                                    color = if (uiState.isLiveConnected) Color(0xFF34D399) else Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(
                            text = "Real-Time AI Companion",
                            color = Color(0xFFA5B4FC),
                            fontSize = 12.sp
                        )
                    }
                }

                // Diagnostics / Logs Button
                IconButton(
                    onClick = { showLogsSheet = true },
                    modifier = Modifier.testTag("logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "View Live Logs",
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }

            // State Pill Indicator
            StateIndicatorPill(state = uiState.state)

            // Center: Glowing Animated Voice Orb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ArushiVoiceOrb(
                    state = uiState.state,
                    audioLevel = uiState.audioLevel,
                    onClick = {
                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleVoiceAssistant(true)
                        }
                    }
                )
            }

            // Live Captions / Speech Bubble Card
            TranscriptBubble(
                transcript = uiState.transcript,
                state = uiState.state
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Actions FlowRow
            QuickActionsBar(
                onQuickPrompt = { prompt ->
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (!uiState.isMicActive) {
                            viewModel.startAssistant()
                        }
                        viewModel.sendQuickPrompt(prompt)
                    }
                },
                onTestSpeaker = {
                    viewModel.testSpeaker()
                },
                onRequestContacts = {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Primary Microphone Control
            BottomMicControl(
                uiState = uiState,
                onMicClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleVoiceAssistant(true)
                    }
                }
            )
        }

        // Snackbar Host for messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )

        // Debug & Diagnostics Bottom Sheet
        if (showLogsSheet) {
            DiagnosticsBottomSheet(
                uiState = uiState,
                onDismiss = { showLogsSheet = false },
                onTestSpeaker = { viewModel.testSpeaker() }
            )
        }
    }
}

@Composable
fun StateIndicatorPill(state: AssistantState) {
    val (bgColor, textColor, label) = when (state) {
        AssistantState.IDLE -> Triple(
            Color(0xFF1E1533),
            Color(0xFFC4B5FD),
            "IDLE — Tap mic to talk"
        )
        AssistantState.CONNECTING -> Triple(
            Color(0xFF0F2B48),
            Color(0xFF67E8F9),
            "CONNECTING TO GEMINI LIVE…"
        )
        AssistantState.LISTENING -> Triple(
            Color(0xFF063A36),
            Color(0xFF34D399),
            "LISTENING TO YOU…"
        )
        AssistantState.SPEAKING -> Triple(
            Color(0xFF3B1238),
            Color(0xFFF472B6),
            "ARUSHI IS SPEAKING…"
        )
        AssistantState.ERROR -> Triple(
            Color(0xFF3E1212),
            Color(0xFFF87171),
            "CONNECTION ERROR"
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ArushiVoiceOrb(
    state: AssistantState,
    audioLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotate"
    )

    // Dynamic reactive scale influenced by real-time voice amplitude
    val dynamicAudioBoost by animateFloatAsState(
        targetValue = (audioLevel * 0.45f).coerceIn(0f, 0.5f),
        animationSpec = tween(80),
        label = "audio_boost"
    )

    val coreScale = (if (state == AssistantState.SPEAKING || state == AssistantState.LISTENING) {
        pulseScale + dynamicAudioBoost
    } else pulseScale).coerceIn(0.85f, 1.45f)

    val orbColorPrimary by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> Color(0xFF8B5CF6)
            AssistantState.CONNECTING -> Color(0xFF06B6D4)
            AssistantState.LISTENING -> Color(0xFF10B981)
            AssistantState.SPEAKING -> Color(0xFFEC4899)
            AssistantState.ERROR -> Color(0xFFEF4444)
        },
        label = "orb_color"
    )

    val orbColorSecondary by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> Color(0xFF6366F1)
            AssistantState.CONNECTING -> Color(0xFF3B82F6)
            AssistantState.LISTENING -> Color(0xFF06B6D4)
            AssistantState.SPEAKING -> Color(0xFFF59E0B)
            AssistantState.ERROR -> Color(0xFFB91C1C)
        },
        label = "orb_color_sec"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .scale(coreScale)
            .clickable(onClick = onClick)
            .testTag("voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing energy rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2.6f

            // Outer decorative wave circles
            for (i in 1..3) {
                val ringRadius = radius + (i * 24f * (1f + dynamicAudioBoost * 1.5f))
                val alpha = (0.28f / i).coerceIn(0.05f, 0.4f)
                drawCircle(
                    color = orbColorPrimary.copy(alpha = alpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Radial energy aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbColorPrimary.copy(alpha = 0.85f),
                        orbColorSecondary.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.3f
                ),
                radius = radius * 1.25f,
                center = center
            )

            // Inner core sphere
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        orbColorPrimary,
                        orbColorSecondary,
                        Color(0xFFE879F9),
                        orbColorPrimary
                    ),
                    center = center
                ),
                radius = radius * 0.75f,
                center = center
            )

            // Light highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = radius * 0.35f,
                center = Offset(center.x - radius * 0.2f, center.y - radius * 0.25f)
            )
        }

        // Center Icon based on state
        when (state) {
            AssistantState.CONNECTING -> {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
            AssistantState.SPEAKING -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaking",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            AssistantState.LISTENING -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Listening",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            AssistantState.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            AssistantState.IDLE -> {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Tap to speak",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
fun TranscriptBubble(
    transcript: String,
    state: AssistantState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .testTag("transcript_bubble"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF160E29).copy(alpha = 0.95f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF8B5CF6).copy(alpha = 0.4f),
                    Color(0xFFEC4899).copy(alpha = 0.4f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (state) {
                        AssistantState.SPEAKING -> "Arushi"
                        AssistantState.LISTENING -> "You"
                        else -> "Live Conversation"
                    },
                    color = Color(0xFFA78BFA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = transcript,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActionsBar(
    onQuickPrompt: (String) -> Unit,
    onTestSpeaker: () -> Unit,
    onRequestContacts: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TRY ASKING ARUSHI",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip(label = "WhatsApp Kholo", icon = Icons.AutoMirrored.Filled.Chat) {
                onQuickPrompt("WhatsApp kholo")
            }
            Spacer(modifier = Modifier.width(6.dp))
            ActionChip(label = "Call Mom", icon = Icons.Default.Call) {
                onRequestContacts()
                onQuickPrompt("Call Mom")
            }
            Spacer(modifier = Modifier.width(6.dp))
            ActionChip(label = "Open YouTube", icon = Icons.Default.PlayArrow) {
                onQuickPrompt("Open YouTube")
            }
            Spacer(modifier = Modifier.width(6.dp))
            ActionChip(label = "Hindi mein baat karo", icon = Icons.Default.SmartToy) {
                onQuickPrompt("Arushi, Hindi mein baat karo")
            }
            Spacer(modifier = Modifier.width(6.dp))
            ActionChip(label = "Speaker Test", icon = Icons.AutoMirrored.Filled.VolumeUp, isHighlight = true) {
                onTestSpeaker()
            }
        }
    }
}

@Composable
fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHighlight) Color(0xFF4C1D95) else Color(0xFF1E1338),
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (isHighlight) Color(0xFFA855F7) else Color(0xFF3B2860),
                RoundedCornerShape(14.dp)
            )
            .testTag("chip_$label")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlight) Color(0xFFF0ABFC) else Color(0xFFC084FC),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BottomMicControl(
    uiState: ArushiUiState,
    onMicClick: () -> Unit
) {
    val isSessionActive = uiState.isMicActive

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        FloatingActionButton(
            onClick = onMicClick,
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = if (isSessionActive) 16.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = if (isSessionActive) Color(0xFFEC4899) else Color(0xFF8B5CF6),
                    spotColor = if (isSessionActive) Color(0xFFEC4899) else Color(0xFF8B5CF6)
                )
                .testTag("mic_toggle_button"),
            shape = CircleShape,
            containerColor = if (isSessionActive) Color(0xFFEF4444) else Color(0xFF8B5CF6),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (isSessionActive) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isSessionActive) "Stop Assistant" else "Start Assistant",
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isSessionActive) "Tap to End Conversation" else "Tap to Speak with Arushi",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    uiState: ArushiUiState,
    onDismiss: () -> Unit,
    onTestSpeaker: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF130924)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Diagnostics & Live Logs",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time inspection of voice & audio pipeline",
                        color = Color(0xFFA5B4FC),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Diagnostic status cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1338))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Gemini Live", color = Color(0xFFA5B4FC), fontSize = 11.sp)
                        Text(
                            text = if (uiState.isLiveConnected) "CONNECTED" else if (uiState.isConnecting) "CONNECTING" else "DISCONNECTED",
                            color = if (uiState.isLiveConnected) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1338))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Microphone", color = Color(0xFFA5B4FC), fontSize = 11.sp)
                        Text(
                            text = if (uiState.isMicActive) "CAPTURING" else "IDLE",
                            color = if (uiState.isMicActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Speaker Diagnostic Button
            OutlinedButton(
                onClick = onTestSpeaker,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speaker_test_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Speaker Diagnostic (440Hz Sine Tone)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "LOG TRACE (${uiState.debugLogs.size})",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF090412)),
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    reverseLayout = false
                ) {
                    if (uiState.debugLogs.isEmpty()) {
                        item {
                            Text(
                                text = "No logs yet. Start the session to view real-time traces.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(uiState.debugLogs) { logLine ->
                            Text(
                                text = logLine,
                                color = when {
                                    logLine.contains("error", ignoreCase = true) || logLine.contains("failed", ignoreCase = true) -> Color(0xFFF87171)
                                    logLine.contains("connected", ignoreCase = true) || logLine.contains("started", ignoreCase = true) -> Color(0xFF34D399)
                                    logLine.contains("audio", ignoreCase = true) -> Color(0xFFF472B6)
                                    else -> Color(0xFFE2E8F0)
                                },
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
