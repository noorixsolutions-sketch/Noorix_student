package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NoorixDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberDarkBg,
    primaryContainer = CyberPanelElevated,
    onPrimaryContainer = NeonCyanGlow,
    secondary = CyberGold,
    onSecondary = CyberDarkBg,
    secondaryContainer = CyberPanel,
    onSecondaryContainer = CyberGold,
    tertiary = CyberGreen,
    onTertiary = CyberDarkBg,
    background = CyberDarkBg,
    onBackground = CyberTextPrimary,
    surface = CyberPanel,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberPanelElevated,
    onSurfaceVariant = CyberTextSecondary,
    outline = CyberPanelBorder,
    error = CyberRed
)

private val NoorixLightColorScheme = lightColorScheme(
    primary = Color(0xFF007A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF00363D),
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF4E2600),
    tertiary = Color(0xFF00796B),
    onTertiary = Color.White,
    background = Color(0xFFF4F8FA),
    onBackground = Color(0xFF0D1B2A),
    surface = Color.White,
    onSurface = Color(0xFF0D1B2A),
    surfaceVariant = Color(0xFFE5EEF2),
    onSurfaceVariant = Color(0xFF415A77),
    outline = Color(0xFFB0C4DE),
    error = CyberRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek Noorix cyber-cyan dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NoorixDarkColorScheme else NoorixLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
