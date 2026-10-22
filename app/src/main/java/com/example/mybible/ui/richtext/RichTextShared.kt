package com.example.mybible.ui.richtext

import androidx.compose.ui.graphics.Color
import com.example.mybible.model.ThemeMode

// The highlighter mark has no existing Material3 role to borrow (unlike
// heading/blockquote/list-marker accents, which just use colorScheme.primary
// and are already theme-correct for free) — these pairs match the ones
// validated in the formatting preview, brighter yellow on the light themes,
// a deeper amber with light ink on the dark ones so the mark stays legible
// either way.
internal fun highlightColors(themeMode: ThemeMode): Pair<Color, Color> = when (themeMode) {
    ThemeMode.PAPER -> Color(0xFFF6D978) to Color(0xFF3A342C)
    ThemeMode.SEPIA -> Color(0xFFF0CE72) to Color(0xFF3E332A)
    ThemeMode.LIGHT -> Color(0xFFFBE28A) to Color(0xFF1F1F1F)
    ThemeMode.DARK -> Color(0xFF6B5A22) to Color(0xFFFFF6DC)
    ThemeMode.CLASSIC_DARK -> Color(0xFF6E4A2A) to Color(0xFFFFF1E0)
}
