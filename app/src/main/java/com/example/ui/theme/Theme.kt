package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WearDarkColorScheme = darkColorScheme(
    primary = AppleMusicRose,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A0E1E),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF002022),
    tertiary = AmberGold,
    background = DeepObsidian,
    onBackground = LyricActiveWhite,
    surface = WatchSurfaceDark,
    onSurface = LyricActiveWhite,
    surfaceVariant = WatchCardDark,
    onSurfaceVariant = Color(0xFFB8BFCE)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearDarkColorScheme,
        typography = Typography,
        content = content
    )
}
