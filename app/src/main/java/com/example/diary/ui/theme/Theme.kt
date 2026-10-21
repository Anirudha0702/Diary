package com.example.diary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NightSage,
    onPrimary = Color(0xFF163A22),
    primaryContainer = Color(0xFF2D4A35),
    onPrimaryContainer = Color(0xFFD9F5DE),
    secondary = Moss,
    background = Night,
    onBackground = Color(0xFFE6EEE6),
    surface = NightSurface,
    onSurface = Color(0xFFE6EEE6),
    surfaceVariant = Color(0xFF354239),
    onSurfaceVariant = Color(0xFFC0CDC1)
)

private val LightColorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageLight,
    onPrimaryContainer = Color(0xFF173923),
    secondary = Color(0xFF6B6654),
    secondaryContainer = Color(0xFFEAE4CE),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = Color(0xFF52574F),
    outline = Color(0xFF757A72),
    outlineVariant = Color(0xFFC5C9BF)
)

@Composable
fun DiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // A stable palette keeps the journal feeling like one personal space on every device.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
