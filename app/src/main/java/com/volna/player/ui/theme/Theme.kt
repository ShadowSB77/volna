package com.volna.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object VColors {
    val Bg = Color(0xFF0A0A10)
    val Surface = Color(0xFF14141D)
    val Surface2 = Color(0xFF1D1D2A)
    val Violet = Color(0xFF8B5CFF)
    val Pink = Color(0xFFFF5C8A)
    val Amber = Color(0xFFFFB45C)
    val Text = Color(0xFFF2F2F7)
    val TextDim = Color(0xFF9A9AB2)

    val brandColors = listOf(Violet, Pink, Amber)
    val Brand: Brush get() = Brush.linearGradient(brandColors)
}

private val scheme = darkColorScheme(
    primary = Color(0xFFB79CFF),
    onPrimary = Color(0xFF1A0B3D),
    primaryContainer = Color(0xFF2C2150),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = VColors.Pink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1F3C),
    onSecondaryContainer = Color(0xFFF1E6FF),
    background = VColors.Bg,
    onBackground = VColors.Text,
    surface = VColors.Bg,
    onSurface = VColors.Text,
    surfaceVariant = VColors.Surface2,
    onSurfaceVariant = VColors.TextDim,
    surfaceContainerLowest = VColors.Bg,
    surfaceContainerLow = VColors.Surface,
    surfaceContainer = VColors.Surface,
    surfaceContainerHigh = VColors.Surface2,
    surfaceContainerHighest = Color(0xFF26263A),
    outline = Color(0xFF3A3A50),
    outlineVariant = Color(0xFF2A2A3C),
    error = Color(0xFFFF6B6B),
)

@Composable
fun VolnaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
