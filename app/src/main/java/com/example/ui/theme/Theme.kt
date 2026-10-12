package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UltronDarkColorScheme = darkColorScheme(
    primary = UltronElectricBlue,
    onPrimary = Color(0xFF041022),
    primaryContainer = UltronSapphire,
    onPrimaryContainer = Color.White,
    secondary = UltronCyanAccent,
    onSecondary = Color(0xFF041022),
    secondaryContainer = UltronCardDark,
    onSecondaryContainer = UltronTextPrimaryDark,
    tertiary = UltronEmerald,
    background = UltronNavyDark,
    onBackground = UltronTextPrimaryDark,
    surface = UltronSurfaceDark,
    onSurface = UltronTextPrimaryDark,
    surfaceVariant = UltronCardDark,
    onSurfaceVariant = UltronTextSecondaryDark,
    error = UltronCoralRed
)

private val UltronLightColorScheme = lightColorScheme(
    primary = UltronPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = UltronCardLight,
    onPrimaryContainer = UltronPrimaryLight,
    secondary = UltronSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = UltronTextPrimaryLight,
    tertiary = UltronEmerald,
    background = UltronBackgroundLight,
    onBackground = UltronTextPrimaryLight,
    surface = UltronSurfaceLight,
    onSurface = UltronTextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = UltronTextSecondaryLight,
    error = UltronCoralRed
)

@Composable
fun MyApplicationTheme(
    themePreference: String = "DARK",
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    val colorScheme = if (useDark) UltronDarkColorScheme else UltronLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
