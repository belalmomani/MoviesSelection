package com.tvseries.Movies.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand: cinematic navy + gold (the logo's colours), violet and cyan as supporting accents. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC53D),
    onPrimary = Color(0xFF1A1300),
    primaryContainer = Color(0xFF4A3A00),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFF8C7CFF),
    onSecondary = Color(0xFF0E0A33),
    secondaryContainer = Color(0xFF2A2F55),
    onSecondaryContainer = Color(0xFFE6E8FF),
    tertiary = Color(0xFF22D3EE),
    onTertiary = Color(0xFF00232B),
    background = Color(0xFF0A0C16),
    onBackground = Color(0xFFEDEFFA),
    surface = Color(0xFF121626),
    onSurface = Color(0xFFEDEFFA),
    surfaceVariant = Color(0xFF1D2238),
    onSurfaceVariant = Color(0xFFA7ADCC),
    surfaceContainerLowest = Color(0xFF080A12),
    surfaceContainerLow = Color(0xFF0F1220),
    surfaceContainer = Color(0xFF151A2C),
    surfaceContainerHigh = Color(0xFF1B2033),
    surfaceContainerHighest = Color(0xFF232944),
    outline = Color(0xFF3A4060),
    outlineVariant = Color(0xFF2A3049),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4636D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF14004F),
    secondary = Color(0xFFB07A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E7FA),
    onSecondaryContainer = Color(0xFF1B1550),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color.White,
    background = Color(0xFFF6F6FC),
    onBackground = Color(0xFF14172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14172A),
    surfaceVariant = Color(0xFFE6E7F3),
    onSurfaceVariant = Color(0xFF4A4E6B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2FA),
    surfaceContainer = Color(0xFFECECF7),
    surfaceContainerHigh = Color(0xFFE6E6F3),
    surfaceContainerHighest = Color(0xFFDFDFEE),
    outline = Color(0xFF8A8EAA),
    outlineVariant = Color(0xFFCDD0E4),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun MoviesTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
