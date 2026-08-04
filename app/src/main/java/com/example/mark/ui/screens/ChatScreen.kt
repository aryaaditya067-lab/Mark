package com.example.mark.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mark.ui.components.MessageBubble
import com.example.mark.ui.components.TypingIndicator
import com.example.mark.ui.components.VoiceOrb
import com.example.mark.ui.components.OrbState
import com.example.mark.health.HealthConnectProvider
import com.example.mark.model.Message
import com.example.mark.viewmodel.ChatViewModel

/**
 * The main chat screen where users interact with Mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenVoiceMode: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val health = remember { HealthConnectProvider(context) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    val healthPermission = rememberLauncherForActivityResult(
        health.permissionContract()
    ) { /* tools handle both outcomes */ }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — the tool handles both */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!health.isAvailable()) {
            runCatching { healthPermission.launch(health.requiredPermissions()) }
        }
    }

    val onMicClick: () -> Unit = {
        if (state.isListening) {
            viewModel.stopListening()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) viewModel.startListening()
            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Automatically scroll to the bottom when new messages are added, loading state changes, or streaming text arrives.
    LaunchedEffect(messages.size, state.isLoading, state.streamingReply) {
        val count = messages.size + (if (state.isLoading) 1 else 0) + (if (state.streamingReply != null) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    // Display error messages as snackbars
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    val orbState = when {
        state.isListening -> OrbState.LISTENING
        state.isLoading -> OrbState.THINKING
        state.isSpeaking -> OrbState.SPEAKING
        else -> OrbState.IDLE
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Mark",
                            fontSize = 30.sp,
                            fontWeight = FontWeight(680),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(10.dp))
                        BreathingDot()
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVoiceMode) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = "Voice mode")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty() && !state.isLoading) {
                EmptyChatHint(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                    if (state.isLoading) {
                        item { TypingIndicator() }
                    }
                    state.streamingReply?.let { reply ->
                        item { MessageBubble(Message(role = "assistant", content = reply)) }
                    }
                }
            }

            ChatInputBar(
                text = state.inputText,
                enabled = !state.isLoading,
                isListening = state.isListening,
                orbState = orbState,
                amplitude = amplitude,
                onTextChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage,
                onMicClick = onMicClick
            )
        }
    }
}

@Composable
fun BreathingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "s"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "a"
    )
    Box(
        Modifier
            .size(9.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

/**
 * Displays a hint when the chat conversation is empty.
 */
@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "Hi there 👋\nAsk Mark anything.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

/**
 * The input bar at the bottom of the chat screen.
 */
@Composable
private fun ChatInputBar(
    text: String,
    enabled: Boolean,
    isListening: Boolean,
    orbState: OrbState,
    amplitude: Float,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty() && !isListening) {
                    Text(
                        "Message Mark…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                if (isListening) {
                    Text(
                        "Listening…",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                    )
                }
            }
            
            Spacer(Modifier.width(8.dp))

            if (text.isBlank() || isListening) {
                VoiceOrb(
                    state = orbState,
                    amplitude = amplitude,
                    onClick = onMicClick,
                    size = 48.dp
                )
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
