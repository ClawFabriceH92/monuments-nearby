package com.fabrice.monumentsnearby.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A237E),
    onPrimary = Color.White,
    secondary = Color(0xFF6A1B9A),
    onSecondary = Color.White
)

@Composable
fun MonumentsNearbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
