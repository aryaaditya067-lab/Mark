package com.example.mark.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mark.viewmodel.SettingsViewModel
import com.example.mark.utils.VoiceLabels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Settings screen for managing application preferences and data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onClearChat: () -> Unit
) {
    val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
    val voiceOutputEnabled by settingsViewModel.voiceOutputEnabled.collectAsState()
    val selectedVoice by settingsViewModel.voiceName.collectAsState()
    val savedPlaces by settingsViewModel.savedPlaces.collectAsState()
    val userName by settingsViewModel.userName.collectAsState()
    val facts by settingsViewModel.facts.collectAsState()
    val announceMessages by settingsViewModel.announceMessages.collectAsState()
    val briefEnabled by settingsViewModel.briefEnabled.collectAsState()
    val briefTime by settingsViewModel.briefTime.collectAsState()
    val laptopConfig by settingsViewModel.laptopConfig.collectAsState()
    val laptopStatus by settingsViewModel.laptopStatus.collectAsState()
    LaunchedEffect(Unit) { settingsViewModel.refreshFacts() }

    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The engine may still be starting when Settings first opens; retry briefly.
    var voices by remember { mutableStateOf(settingsViewModel.ttsManager.availableVoices(includeOnline = true)) }
    LaunchedEffect(Unit) {
        repeat(5) {
            if (voices.isNotEmpty()) return@LaunchedEffect
            kotlinx.coroutines.delay(1_000)
            voices = settingsViewModel.ttsManager.availableVoices(includeOnline = true)
        }
    }

    val context = LocalContext.current
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var isDndGranted by remember { mutableStateOf(notificationManager.isNotificationPolicyAccessGranted) }

    val dndLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDndGranted = notificationManager.isNotificationPolicyAccessGranted
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ListItem(
                headlineContent = { Text("Dark mode") },
                supportingContent = { Text("Toggle app theme") },
                trailingContent = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { settingsViewModel.setDarkMode(it) }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Voice replies") },
                supportingContent = { Text("Mark speaks his answers out loud") },
                trailingContent = {
                    Switch(
                        checked = voiceOutputEnabled,
                        onCheckedChange = { settingsViewModel.setVoiceOutput(it) }
                    )
                }
            )

            if (voices.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text("Voice") },
                    supportingContent = {
                        Text(
                            if (selectedVoice.isBlank()) "System default"
                            else VoiceLabels.label(selectedVoice)
                        )
                    },
                    modifier = Modifier.clickable { showVoiceDialog = true }
                )
            }

            val isAssistant = remember {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                    context.getSystemService(android.app.role.RoleManager::class.java)
                        ?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
            }
            ListItem(
                headlineContent = { Text("Make Mark your assistant") },
                supportingContent = {
                    Text(
                        if (isAssistant) "Done: long-press power (or the assist gesture) opens Mark"
                        else "Default apps > Digital assistant app > Mark. Then long-press power to talk."
                    )
                },
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    }.onFailure {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Do Not Disturb access") },
                supportingContent = { Text(if (isDndGranted) "Granted" else "Tap to allow Mark to manage DND") },
                trailingContent = {
                    Switch(
                        checked = isDndGranted,
                        onCheckedChange = {
                            runCatching {
                                dndLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                        }
                    )
                },
                modifier = Modifier.clickable {
                    runCatching {
                        dndLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                }
            )

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("About you", style = MaterialTheme.typography.titleMedium)

            var nameInput by remember(userName) { mutableStateOf(userName) }
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("What should Mark call you?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                trailingIcon = {
                    if (nameInput.trim() != userName) {
                        IconButton(onClick = { settingsViewModel.setUserName(nameInput) }) {
                            Icon(Icons.Default.Done, contentDescription = "Save")
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            Text("What Mark remembers", style = MaterialTheme.typography.titleMedium)
            Text(
                "Say \"Mark, yaad rakhna ...\" to add, or delete here. Your name and these facts " +
                    "are sent to the AI service with your questions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            if (facts.isEmpty()) {
                Text(
                    "Nothing yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            facts.forEach { fact ->
                ListItem(
                    headlineContent = { Text(fact.text) },
                    trailingContent = {
                        IconButton(onClick = { settingsViewModel.forget(fact) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Forget")
                        }
                    }
                )
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Saved Places", style = MaterialTheme.typography.titleMedium)
            
            var homeAddress by remember(savedPlaces) { mutableStateOf(savedPlaces["home"] ?: "") }
            var workAddress by remember(savedPlaces) { mutableStateOf(savedPlaces["work"] ?: "") }

            OutlinedTextField(
                value = homeAddress,
                onValueChange = { homeAddress = it },
                label = { Text("Home Address") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                trailingIcon = {
                    if (homeAddress != (savedPlaces["home"] ?: "")) {
                        IconButton(onClick = { settingsViewModel.savePlace("home", homeAddress) }) {
                            Icon(Icons.Default.Done, contentDescription = "Save")
                        }
                    }
                }
            )

            OutlinedTextField(
                value = workAddress,
                onValueChange = { workAddress = it },
                label = { Text("Work Address") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                trailingIcon = {
                    if (workAddress != (savedPlaces["work"] ?: "")) {
                        IconButton(onClick = { settingsViewModel.savePlace("work", workAddress) }) {
                            Icon(Icons.Default.Done, contentDescription = "Save")
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Read new messages aloud") },
                supportingContent = {
                    Text("WhatsApp, Messages, Telegram. Only into headphones, never 10 pm to 7 am or in Do Not Disturb. Needs notification access.")
                },
                trailingContent = {
                    Switch(checked = announceMessages, onCheckedChange = { settingsViewModel.setAnnounceMessages(it) })
                }
            )

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Morning brief", style = MaterialTheme.typography.titleMedium)
            var briefTimeInput by remember(briefTime) { mutableStateOf(briefTime) }
            ListItem(
                headlineContent = { Text("Daily brief notification") },
                supportingContent = {
                    Text("Weather, meetings, reminders and battery, with a heads-up 10 minutes before each meeting")
                },
                trailingContent = {
                    Switch(
                        checked = briefEnabled,
                        onCheckedChange = { settingsViewModel.setBrief(context, it, briefTimeInput) }
                    )
                }
            )
            OutlinedTextField(
                value = briefTimeInput,
                onValueChange = { briefTimeInput = it },
                label = { Text("Time (24-hour, e.g. 07:30)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = {
                    if (briefTimeInput != briefTime) {
                        IconButton(onClick = { settingsViewModel.setBrief(context, briefEnabled, briefTimeInput) }) {
                            Icon(Icons.Default.Done, contentDescription = "Save")
                        }
                    }
                }
            )

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Laptop", style = MaterialTheme.typography.titleMedium)
            Text(
                "For the Mark agent on your Windows laptop (same Wi-Fi). Give the laptop a fixed " +
                    "address in your router so this keeps working. The MAC address lets Mark wake it up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            var laptopHost by remember(laptopConfig) { mutableStateOf(laptopConfig.host) }
            var laptopToken by remember(laptopConfig) { mutableStateOf(laptopConfig.token) }
            var laptopMac by remember(laptopConfig) { mutableStateOf(laptopConfig.mac) }

            OutlinedTextField(
                value = laptopHost,
                onValueChange = { laptopHost = it },
                label = { Text("Laptop IP address, e.g. 192.168.1.20") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = laptopToken,
                onValueChange = { laptopToken = it },
                label = { Text("Agent token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = laptopMac,
                onValueChange = { laptopMac = it },
                label = { Text("MAC address (optional, for wake-up)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { settingsViewModel.saveLaptop(laptopHost, laptopToken, laptopMac) }) {
                    Text("Save")
                }
                OutlinedButton(onClick = {
                    settingsViewModel.saveLaptop(laptopHost, laptopToken, laptopMac)
                    settingsViewModel.testLaptop()
                }) { Text("Test connection") }
            }
            laptopStatus?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear chat history") }

            Spacer(Modifier.height(28.dp))

            Text("About", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Mark v1.0 — Your AI personal assistant.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear chat?") },
            text = { Text("All messages will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearChat()
                    showClearDialog = false
                    scope.launchSnack(snackbarHostState, "Chat cleared")
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text("Select Voice") },
            text = {
                Column {
                    voices.forEach { voice ->
                        ListItem(
                            headlineContent = { Text(VoiceLabels.label(voice.name)) },
                            leadingContent = {
                                RadioButton(
                                    selected = voice.name == selectedVoice,
                                    onClick = {
                                        settingsViewModel.setVoiceName(voice.name)
                                        showVoiceDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                settingsViewModel.setVoiceName(voice.name)
                                showVoiceDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceDialog = false }) { Text("Close") }
            }
        )
    }
}

/**
 * Helper function to launch a snackbar within a coroutine scope.
 */
private fun CoroutineScope.launchSnack(
    host: SnackbarHostState,
    message: String
) {
    launch { host.showSnackbar(message) }
}
