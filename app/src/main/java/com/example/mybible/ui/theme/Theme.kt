package com.example.mybible.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.mybible.model.ThemeMode

// tertiary = Capacitor's "--gold" (uppercase section-label color in Display
// settings); error = "--redletter" (the color the ds-switch pill turns when
// on, and the words-of-Christ red-letter text color). Paper is a direct
// port of Capacitor's light theme, so it uses the exact light-mode hex
// values; Sepia/Light/Dark are Kotlin-only bonus themes without a Capacitor
// counterpart, so they get in-family gold/red tones rather than literal
// ports.
private val PaperColorScheme = lightColorScheme(
    primary = Color(0xFF2C221E),
    onPrimary = Color(0xFFFAF8F5),
    primaryContainer = Color(0xFFE8DFC8),
    onPrimaryContainer = Color(0xFF2C221E),
    background = Color(0xFFF6F3EC),
    onBackground = Color(0xFF2C221E),
    surface = Color(0xFFEFECE4),
    onSurface = Color(0xFF2C221E),
    surfaceContainerHigh = Color(0xFFE5E0D5),
    surfaceVariant = Color(0xFFE8E3D8),
    onSurfaceVariant = Color(0xFF6B625B),
    tertiary = Color(0xFFA9852F),   // --gold (light)
    error = Color(0xFFB7362A)       // --redletter (light)
)

private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF4A3E3D),
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFE2D0BE),
    onPrimaryContainer = Color(0xFF322826),
    background = Color(0xFFF5EBE0),
    onBackground = Color(0xFF3D312A),
    surface = Color(0xFFEADBC8),
    onSurface = Color(0xFF3D312A),
    surfaceContainerHigh = Color(0xFFDECDB9),
    surfaceVariant = Color(0xFFDFD1C0),
    onSurfaceVariant = Color(0xFF6E6056),
    tertiary = Color(0xFFA9852F),
    error = Color(0xFFA8402C)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1F1F1F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF1F1F1F),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF616161),
    tertiary = Color(0xFF9A7B2E),
    error = Color(0xFFB7362A)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8D8C8),
    onPrimary = Color(0xFF1C1A17),
    primaryContainer = Color(0xFF3B3530),
    onPrimaryContainer = Color(0xFFE8D8C8),
    background = Color(0xFF1C1A17),
    onBackground = Color(0xFFE8D8C8),
    surface = Color(0xFF262320),
    onSurface = Color(0xFFE8D8C8),
    surfaceContainerHigh = Color(0xFF332F2A),
    surfaceVariant = Color(0xFF302C28),
    onSurfaceVariant = Color(0xFFAAA298),
    tertiary = Color(0xFFD2A94F),
    error = Color(0xFFE2694F)
)

// Ported directly from the Capacitor app's html[data-theme="dark"] CSS block:
// --paper:#1C1A17 --paper-dim:#262320 --ink:#EDE8DD --ink-soft:#A29C8E
// --accent:#E0836F --accent-solid:#A8503D --gold:#D2A94F --line:#3A3631 --input-bg:#262320
private val ClassicDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0836F),           // --accent
    onPrimary = Color(0xFF1C1A17),         // --paper (dark text on the lighter coral accent)
    primaryContainer = Color(0xFFA8503D),  // --accent-solid
    onPrimaryContainer = Color(0xFFFFF3EC),
    background = Color(0xFF1C1A17),        // --paper
    onBackground = Color(0xFFEDE8DD),      // --ink
    surface = Color(0xFF262320),           // --paper-dim / --input-bg
    onSurface = Color(0xFFEDE8DD),         // --ink
    surfaceContainerHigh = Color(0xFF3A3631), // --line
    surfaceVariant = Color(0xFF262320),    // --paper-dim
    onSurfaceVariant = Color(0xFFA29C8E),  // --ink-soft
    tertiary = Color(0xFFD2A94F),          // --gold
    error = Color(0xFFE2694F)              // --redletter (words-of-Christ red-letter color)
)

@Composable
fun MyBibleTheme(
    themeMode: ThemeMode = ThemeMode.PAPER,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.PAPER -> PaperColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.CLASSIC_DARK -> ClassicDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
