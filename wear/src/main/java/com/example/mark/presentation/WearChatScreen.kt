package com.example.mark.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

private val CAPABILITY_ITEMS = listOf(
    "Torch", "Brightness", "Volume", "Call", "Message",
    "Alarm", "Timer", "Music", "Weather", "Steps"
)

/**
 * The whole screen is the microphone. The ring at the bezel shows state, the
 * centre holds only the latest exchange.
 */
@Composable
fun WearChatScreen(viewModel: WearChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    // PERF: this used to be `val messages by viewModel.messages.collectAsState()`
    // read directly in this scope, so every single message appended recomposed
    // the entire screen — including the orb's composable. Only one boolean is
    // actually needed, and derivedStateOf means recomposition happens only when
    // that boolean flips, not on every message.
    val messages by viewModel.messages.collectAsState()
    val showHelpList by remember {
        derivedStateOf {
            val last = messages.lastOrNull()
            last?.role == "assistant" &&
                    last.content?.contains("kar sakta hun sir", ignoreCase = true) == true
        }
    }

    val orbState = when {
        state.isPreparing -> OrbState.PREPARING
        state.isListening -> OrbState.LISTENING
        state.isLoading -> OrbState.THINKING
        state.isSpeaking -> OrbState.SPEAKING
        else -> OrbState.IDLE
    }

    val statusText = when (orbState) {
        OrbState.IDLE -> "Tap or speak"
        OrbState.PREPARING -> "Wait..."
        OrbState.LISTENING -> "Listening..."
        OrbState.THINKING -> "Thinking..."
        OrbState.SPEAKING -> "Speaking..."
    }

    // Hoisted so the lambda identity is stable across recompositions.
    val interactionSource = remember { MutableInteractionSource() }
    val onTap: () -> Unit = remember(state.isListening, state.isLoading) {
        {
            if (state.isListening) {
                viewModel.stopListening()
            } else if (!state.isLoading) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.startListening()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0C))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap
            )
    ) {
        WearVoiceRing(
            state = orbState,
            amplitudeFlow = viewModel.amplitude,
            liveTranscript = viewModel.liveTranscript,
            liveReply = viewModel.liveReply,
            statusText = statusText
        )

        if (showHelpList) {
            CapabilityList()
        }

        TimeText()
    }
}

@Composable
private fun CapabilityList() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Capabilities",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )
            }
            items(CAPABILITY_ITEMS) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.3.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}