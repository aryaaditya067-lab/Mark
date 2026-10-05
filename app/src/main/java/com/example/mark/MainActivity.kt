package com.example.mark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.mark.navigation.Screen
import com.example.mark.repository.AuthRepository
import com.example.mark.repository.SettingsRepository
import com.example.mark.ui.screens.ChatScreen
import com.example.mark.ui.screens.SettingsScreen
import com.example.mark.ui.screens.SignInScreen
import com.example.mark.ui.screens.SplashScreen
import com.example.mark.ui.screens.TasksScreen
import com.example.mark.ui.screens.VoiceModeScreen
import com.example.mark.ui.theme.MarkTheme
import com.example.mark.viewmodel.ChatViewModel
import com.example.mark.viewmodel.SettingsViewModel
import com.example.mark.viewmodel.SettingsViewModelFactory
import com.example.mark.viewmodel.VoiceModeViewModel
import com.example.mark.utils.TextToSpeechManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTE: FirebaseApp.initializeApp() and NetworkProvider.start() are NOT
        // called here — MarkApplication.onCreate already does both. Doing them
        // again on the main thread only delayed the first frame.

        enableEdgeToEdge()

        val app = application as MarkApplication

        setContent {
            val isDarkMode by app.settingsRepository.isDarkMode.collectAsState(initial = false)

            MarkTheme(darkTheme = isDarkMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MarkApp(app.settingsRepository, app.ttsManager)
                }
            }
        }
    }
}

@Composable
fun MarkApp(settingsRepository: SettingsRepository, ttsManager: TextToSpeechManager) {
    val navController = rememberNavController()
    val auth = remember { AuthRepository.instance }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen(onNextScreen = {
                val next = if (auth.isSignedIn) Screen.Chat.route else Screen.SignIn.route
                navController.navigate(next) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.SignIn.route) {
            SignInScreen(onSignedIn = {
                navController.navigate(Screen.Chat.route) {
                    popUpTo(Screen.SignIn.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Chat.route) {
            MainScreen(settingsRepository, ttsManager)
        }
    }
}

@Composable
fun MainScreen(settingsRepository: SettingsRepository, ttsManager: TextToSpeechManager) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val items = listOf(Screen.Chat, Screen.Tasks, Screen.Settings)

    val requiredPermissions = listOfNotNull(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        // Reminders and the morning brief arrive as notifications.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS else null
    )

    var missingPermissions by remember {
        mutableStateOf(requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        })
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        missingPermissions = requiredPermissions.filter { results[it] != true }
        if (results[Manifest.permission.READ_CONTACTS] == true) {
            com.example.mark.repository.ContactRepository.getInstance(context).tryRefresh()
        }
    }

    // Hoisted here so chat state survives switching bottom-nav tabs
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            settingsRepository, ttsManager,
            memory = com.example.mark.assistant.MarkAssistant.memory(context),
            laptop = com.example.mark.tools.LaptopTool(context)
        )
    )

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = missingPermissions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Mark needs permissions for calls, messages and calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { launcher.launch(missingPermissions.toTypedArray()) }) {
                            Text("Grant")
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    screen.icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selected) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                screen.title,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        selected = selected,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onOpenVoiceMode = { navController.navigate(Screen.VoiceMode.route) }
                )
            }
            composable(Screen.VoiceMode.route) {
                val voiceViewModel: VoiceModeViewModel = viewModel()
                VoiceModeScreen(
                    viewModel = voiceViewModel,
                    onExit = { navController.popBackStack() }
                )
            }
            composable(Screen.Tasks.route) {
                TasksScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onClearChat = { chatViewModel.clearChat() }
                )
            }
        }
    }
}