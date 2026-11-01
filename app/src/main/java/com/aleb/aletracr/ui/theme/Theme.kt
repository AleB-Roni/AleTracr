package com.aleb.aletracr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F9D6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF5E4),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF0E8FD6),
    secondaryContainer = Color(0xFFD5EEFB),
    tertiary = Color(0xFFF08A00),
    tertiaryContainer = Color(0xFFFFE6C2),
    background = Color(0xFFF4F7F5),
    surface = Color(0xFFF4F7F5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF3F0),
    surfaceContainerHigh = Color(0xFFE6EDE9),
    surfaceVariant = Color(0xFFE2EAE6),
    onSurfaceVariant = Color(0xFF5B6763),
    outlineVariant = Color(0xFFD5DEDA),
    error = Color(0xFFE5484D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0B4F3A),
    onPrimaryContainer = Color(0xFFCFF5E4),
    secondary = Color(0xFF5CC3FF),
    secondaryContainer = Color(0xFF0B3A57),
    tertiary = Color(0xFFFFB648),
    tertiaryContainer = Color(0xFF4F3200),
    background = Color(0xFF0D1311),
    surface = Color(0xFF0D1311),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF151C19),
    surfaceContainer = Color(0xFF19221E),
    surfaceContainerHigh = Color(0xFF212B27),
    surfaceVariant = Color(0xFF26312C),
    onSurfaceVariant = Color(0xFFA6B3AD),
    outlineVariant = Color(0xFF2E3A35),
    error = Color(0xFFFF6B6B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
)

/** Colors for macro nutrients, used consistently across screens and the widget. */
object MacroColors {
    val protein = Color(0xFF4F7CFF)
    val carbs = Color(0xFFFFA928)
    val fat = Color(0xFFFF4F8B)
    val sugar = Color(0xFFB36BFF)
    val fiber = Color(0xFF2BB673)
}

/** Brand gradient for the hero card and the AI button. */
object Gradients {
    val hero = Brush.linearGradient(listOf(Color(0xFF0FA376), Color(0xFF0E8FD6)))
    val ai = Brush.linearGradient(listOf(Color(0xFF7B5CFF), Color(0xFFE356C0), Color(0xFFFF8A3D)))
    val over = Brush.linearGradient(listOf(Color(0xFFE5484D), Color(0xFFF08A00)))
}

@Composable
fun AleTracrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
