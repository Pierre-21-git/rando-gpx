package com.tonapp.rando.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    secondary = MossGreen,
    tertiary = TrailOrange,
    background = androidx.compose.ui.graphics.Color(0xFFF7F7F2)
)

private val DarkColors = darkColorScheme(
    primary = MossGreen,
    secondary = ForestGreen,
    tertiary = TrailOrange
)

@Composable
fun RandoGPXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
