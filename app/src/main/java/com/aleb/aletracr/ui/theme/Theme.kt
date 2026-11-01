package com.aleb.aletracr.ui.theme

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

private val Green = Color(0xFF2E7D32)
private val GreenLight = Color(0xFFA5D6A7)

private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF00897B), tertiary = Color(0xFFF57C00))
private val DarkColors = darkColorScheme(primary = GreenLight, secondary = Color(0xFF80CBC4), tertiary = Color(0xFFFFB74D))

/** Colors for macro nutrients, used consistently across screens. */
object MacroColors {
    val protein = Color(0xFF1E88E5)
    val carbs = Color(0xFFFFA000)
    val fat = Color(0xFFD81B60)
}

@Composable
fun AleTracrTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
