package fr.pierre.randogpx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = MossGreen,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = MossGreen,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD9E6D3),
    tertiary = TrailOrange,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF7F7F2),
    surface = androidx.compose.ui.graphics.Color(0xFFF7F7F2),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE1E4DC)
)

private val DarkColors = darkColorScheme(
    primary = MossGreen,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0E1F12),
    primaryContainer = ForestGreen,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = MossGreen,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF0E1F12),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF35492F),
    tertiary = TrailOrange,
    onTertiary = androidx.compose.ui.graphics.Color(0xFF3A2400)
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
