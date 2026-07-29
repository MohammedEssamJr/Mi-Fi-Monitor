package com.mohammed.mifimonitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A deep-teal / signal-green identity — distinct from stock Material purple,
// and green reads naturally as "connectivity / online" for this kind of app.
private val TealPrimary = Color(0xFF17B897)
private val TealPrimaryContainer = Color(0xFF0E4A3C)
private val DeepBackground = Color(0xFF0E1416)
private val SurfaceDark = Color(0xFF162024)
private val SurfaceVariantDark = Color(0xFF1E2A2E)
private val AmberAccent = Color(0xFFE8A33D)

private val MiFiColorScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = Color(0xFF00201A),
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = Color(0xFFB6FFEA),
    secondary = AmberAccent,
    onSecondary = Color(0xFF2A1800),
    background = DeepBackground,
    onBackground = Color(0xFFE4EDEB),
    surface = SurfaceDark,
    onSurface = Color(0xFFE4EDEB),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFA9BDB8),
    error = Color(0xFFFF6B6B)
)

@Composable
fun MiFiMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MiFiColorScheme,
        content = content
    )
}
