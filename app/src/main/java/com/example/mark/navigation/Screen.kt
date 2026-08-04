package com.example.mark.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Defines all screens in the application for navigation.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Splash : Screen("splash", "Splash", Icons.AutoMirrored.Filled.Chat)
    data object SignIn : Screen("signin", "Sign in", Icons.Default.AccountCircle)
    data object Chat : Screen("chat", "Chat", Icons.AutoMirrored.Filled.Chat)
    data object Tasks : Screen("tasks", "Tasks", Icons.Default.List)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object VoiceMode : Screen("voice", "Voice", Icons.Default.GraphicEq)
}