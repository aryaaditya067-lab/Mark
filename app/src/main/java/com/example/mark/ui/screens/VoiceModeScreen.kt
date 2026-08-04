package com.example.mark.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mark.model.VoicePhase
import com.example.mark.ui.components.OrbState
import com.example.mark.ui.components.VoiceOrb
import com.example.mark.ui.theme.MarkEmber
import com.example.mark.ui.theme.MarkMuted
import com.example.mark.viewmodel.VoiceModeViewModel

/**
 * Full-bleed voice surface — the phone's version of the watch experience.
 * The orb is the room; the words pass through it.
 */
@Composable
fun VoiceModeScreen(
    viewModel: VoiceModeViewModel,
    onExit: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    LaunchedEffect(state.active) {
        if (!state.active) onExit()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    val orbState = when (state.phase) {
        VoicePhase.LISTENING -> OrbState.LISTENING
        VoicePhase.THINKING -> OrbState.THINKING
        VoicePhase.SPEAKING -> OrbState.SPEAKING
        VoicePhase.IDLE -> OrbState.IDLE
    }

    // Rolling window, same as the watch: only the last few words, so the text
    // flows through instead of piling up.
    val caption = when (state.phase) {
        VoicePhase.LISTENING -> state.transcript
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .takeLast(7).joinToString(" ")
        VoicePhase.SPEAKING -> state.lastReply
        VoicePhase.THINKING -> ""
        VoicePhase.IDLE -> state.errorMessage ?: ""
    }

    val statusLine = when (state.phase) {
        VoicePhase.LISTENING -> "Listening"
        VoicePhase.THINKING -> "Thinking"
        VoicePhase.SPEAKING -> "Speaking"
        VoicePhase.IDLE -> ""
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {

            IconButton(
                onClick = { viewModel.stop(); onExit() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Exit voice mode",
                    tint = MarkMuted
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .offset(y = (-24).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tap the orb to cancel listening / end the session — same
                // gesture as the watch.
                VoiceOrb(
                    state = orbState,
                    amplitude = state.amplitude,
                    onClick = { viewModel.stop(); onExit() },
                    size = 240.dp
                )

                Spacer(Modifier.height(44.dp))

                AnimatedVisibility(
                    visible = statusLine.isNotBlank(),
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(220))
                ) {
                    Text(
                        text = statusLine.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.phase == VoicePhase.LISTENING) MarkEmber else MarkMuted
                    )
                }

                Spacer(Modifier.height(10.dp))

                AnimatedVisibility(
                    visible = caption.isNotBlank(),
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(220))
                ) {
                    Text(
                        text = caption,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (state.phase == VoicePhase.SPEAKING) 0.92f else 0.65f
                        ),
                        maxLines = 3
                    )
                }
            }
        }
    }
}