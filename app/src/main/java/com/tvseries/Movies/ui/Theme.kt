package com.tvseries.Movies.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC107),
    onPrimary = Color(0xFF1B1F3B),
    secondary = Color(0xFF9FA8FF),
    background = Color(0xFF11132A),
    surface = Color(0xFF191C36),
    surfaceVariant = Color(0xFF262A4A),
    onBackground = Color(0xFFEDEEFA),
    onSurface = Color(0xFFEDEEFA),
    onSurfaceVariant = Color(0xFFB8BBDD),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    secondary = Color(0xFFB8860B),
    background = Color(0xFFF6F6FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6E7F3),
    onBackground = Color(0xFF16182C),
    onSurface = Color(0xFF16182C),
    onSurfaceVariant = Color(0xFF4A4D6B),
)

@Composable
fun MoviesTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
