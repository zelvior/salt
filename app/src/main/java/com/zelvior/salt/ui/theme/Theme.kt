package com.zelvior.salt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette — deep, hardware-inspired, translucent-friendly.
val SaltBackground    = Color(0xFF0B0E14)
val SaltSurface       = Color(0xFF151A23)
val SaltPrimary       = Color(0xFF8AB4F8)
val SaltTextPrimary   = Color(0xFFE6E9EF)
val SaltTextSecondary = Color(0xFF9AA4B2)

// Translucent glass fill used for control-center surfaces.
val SaltGlassBackground = Color(0x33FFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = SaltPrimary,
    background = SaltBackground,
    surface = SaltSurface,
    onPrimary = Color.Black,
    onBackground = SaltTextPrimary,
    onSurface = SaltTextPrimary
)

@Composable
fun SaltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
