package com.example.mybible.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/** Where the app's current theme name is mirrored so the widget process
 *  (a separate, lighter-weight process than the main app) can read it
 *  synchronously without needing DataStore/Compose state.
 *
 *  No extra wiring needed: BibleRepository already stores the theme under
 *  the key "theme_mode" in the same "my_bible_prefs" SharedPreferences file
 *  this reads from, so every setTheme() call is already visible here.
 */
const val WIDGET_PREFS_NAME = "my_bible_prefs"
const val WIDGET_THEME_KEY = "theme_mode"

// Temporary diagnostic for the widget exact-resume bug — turns
// BibleRepository.saveLastReadPosition's raw "verse=... durable=... at=..."
// trace (see its own doc) into a compact on-widget line, e.g.
// "v=null d=true 42s ago", so it can be read on-device without adb. Remove
// alongside the trace write and the widget label lines that call this once
// the bug is resolved.
fun formatDebugSaveTrace(raw: String?): String? {
    if (raw == null) return null
    val verse = raw.substringAfter("verse=").substringBefore(" durable=")
    val durable = raw.substringAfter("durable=").substringBefore(" at=")
    val at = raw.substringAfter("at=").toLongOrNull() ?: return null
    val agoSec = (System.currentTimeMillis() - at) / 1000
    return "save: v=$verse d=$durable ${agoSec}s ago"
}

// Companion to formatDebugSaveTrace above, decoding
// MainViewModel's init{}-time "what did cold start read back" trace
// (BibleRepository.setDebugRestoreTrace).
fun formatDebugRestoreTrace(raw: String?): String? {
    if (raw == null) return null
    val book = raw.substringAfter("book=").substringBefore(" chap=")
    val chap = raw.substringAfter("chap=").substringBefore(" verse=")
    val verse = raw.substringAfter("verse=").substringBefore(" at=")
    val at = raw.substringAfter("at=").toLongOrNull() ?: return null
    val agoSec = (System.currentTimeMillis() - at) / 1000
    return "init: $book $chap v=$verse ${agoSec}s ago"
}

// Companion to formatDebugSaveTrace above, decoding ReaderScreen's
// scroll-to-focus attempt trace (BibleRepository.setDebugScrollTrace) —
// foundIdx=-1 means the target verse was NOT in the loaded chapter, which
// is exactly what falls back to scrolling to the top instead.
fun formatDebugScrollTrace(raw: String?): String? {
    if (raw == null) return null
    val target = raw.substringAfter("target=").substringBefore(" foundIdx=")
    val foundIdx = raw.substringAfter("foundIdx=").substringBefore(" versesSize=")
    val versesSize = raw.substringAfter("versesSize=").substringBefore(" at=")
    val at = raw.substringAfter("at=").toLongOrNull() ?: return null
    val agoSec = (System.currentTimeMillis() - at) / 1000
    return "scroll: t=$target idx=$foundIdx n=$versesSize ${agoSec}s ago"
}

data class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val accent: ColorProvider,
    val buttonBackground: ColorProvider,
    val buttonText: ColorProvider,
    // Subtle 1dp card outline. Glance has no Modifier.border(), so this is
    // consumed via the nested-Box trick in VerseOfDayWidget (outer box
    // painted this color, inset by the border width, inner box holds the
    // real card content) rather than a real border modifier.
    val cardBorder: ColorProvider
)

object WidgetColors {

    private val PAPER = WidgetPalette(
        background = ColorProvider(Color(0xFFFAF7F0)),
        text = ColorProvider(Color(0xFF2B2820)),
        accent = ColorProvider(Color(0xFF6B5B3A)),
        buttonBackground = ColorProvider(Color(0xFFEDE6D6)),
        buttonText = ColorProvider(Color(0xFF2B2820)),
        cardBorder = ColorProvider(Color(0x1F2B2820)) // ink @ ~12%
    )

    private val SEPIA = WidgetPalette(
        background = ColorProvider(Color(0xFFF4ECD8)),
        text = ColorProvider(Color(0xFF3B2F1E)),
        accent = ColorProvider(Color(0xFF8B5E34)),
        buttonBackground = ColorProvider(Color(0xFFE0D0AE)),
        buttonText = ColorProvider(Color(0xFF3B2F1E)),
        cardBorder = ColorProvider(Color(0x1F3B2F1E)) // ink @ ~12%
    )

    private val LIGHT = WidgetPalette(
        background = ColorProvider(Color(0xFFFFFFFF)),
        text = ColorProvider(Color(0xFF1C1C1E)),
        accent = ColorProvider(Color(0xFF3F6FBF)),
        buttonBackground = ColorProvider(Color(0xFFEFEFEF)),
        buttonText = ColorProvider(Color(0xFF1C1C1E)),
        cardBorder = ColorProvider(Color(0x1A1C1C1E)) // ink @ ~10%
    )

    private val DARK = WidgetPalette(
        background = ColorProvider(Color(0xFF1C1C1E)),
        text = ColorProvider(Color(0xFFE5E5E7)),
        accent = ColorProvider(Color(0xFFC9A86A)),
        buttonBackground = ColorProvider(Color(0xFF2C2C2E)),
        buttonText = ColorProvider(Color(0xFFE5E5E7)),
        cardBorder = ColorProvider(Color(0x24E5E5E7)) // light ink @ ~14%, dark cards need a touch more to read
    )

    // Ported directly from the app's actual Classic Dark Compose theme
    // (ui/theme/Theme.kt's ClassicDarkColorScheme / the Capacitor app's
    // html[data-theme="dark"] CSS), not a generic Material dark palette —
    // this used to be plain Material dark-purple (#121212 / #BB86FC),
    // which didn't match the in-app theme's warm terracotta look at all.
    private val CLASSIC_DARK = WidgetPalette(
        background = ColorProvider(Color(0xFF1C1A17)),      // --paper
        text = ColorProvider(Color(0xFFEDE8DD)),             // --ink
        accent = ColorProvider(Color(0xFFE0836F)),           // --accent (coral)
        buttonBackground = ColorProvider(Color(0xFF262320)), // --paper-dim / --input-bg
        buttonText = ColorProvider(Color(0xFFEDE8DD)),        // --ink
        cardBorder = ColorProvider(Color(0x26EDE8DD))        // ink @ ~15%
    )

    fun forCurrentTheme(context: Context): WidgetPalette {
        val prefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        // Default of CLASSIC_DARK matches ThemeMode's own real default in
        // BibleRepository.getSavedTheme() — a fresh install that has never
        // explicitly saved a theme should show the same look on the widget
        // as it does in the app itself.
        return when (prefs.getString(WIDGET_THEME_KEY, "CLASSIC_DARK")) {
            "SEPIA" -> SEPIA
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            "CLASSIC_DARK" -> CLASSIC_DARK
            "PAPER" -> PAPER
            else -> CLASSIC_DARK
        }
    }
}
