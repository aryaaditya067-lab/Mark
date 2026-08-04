package com.example.mark.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = MarkEmber,
    onPrimary = MarkBg,
    secondary = MarkEmberDim,
    onSecondary = Color.White,
    background = MarkBg,
    onBackground = MarkText,
    surface = MarkSurface,
    onSurface = MarkText,
    surfaceVariant = MarkSurface,
    onSurfaceVariant = MarkMuted,
    outline = MarkLine
)

// Mark is a dark-first app. We use the same palette for light mode 
// but could tweak it if a true light mode is ever needed.
private val LightColors = DarkColors 

@Composable
fun MarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> DarkColors // Always dark for now to maintain the vibe
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
