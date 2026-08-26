package com.example.mybible.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily

/**
 * Temporary, in-memory-only test rig for the Notes-tab font pass — see the
 * "Notes Font Lab" section in Settings. Deliberately NOT persisted to
 * SharedPreferences/DataStore: it resets to "off" on every app restart by
 * design, since this exists purely to A/B a candidate font against the
 * current Literata/Noto Serif defaults before committing to one for real.
 * Remove this file (and the Settings section, and revert the two
 * properties below to plain references) once a font is chosen.
 */
object FontTestLab {
    // Populated with real bundled FontFamily entries once the candidate
    // Google Fonts are named — see AppFonts.kt for the bundling pattern.
    val testFonts: Map<String, FontFamily> = emptyMap()

    var selectedFontKey by mutableStateOf<String?>(null)

    var listPreviewFontSizeSp by mutableFloatStateOf(14f)
    var listPreviewLineHeightMultiplier by mutableFloatStateOf(20f / 14f)

    var readerFontSizeSp by mutableFloatStateOf(18f)
    var readerLineHeightMultiplier by mutableFloatStateOf(29f / 18f)
}

/** Literata (secondary Scripture surfaces) unless a Font Lab test font is active. */
val literataOrTestFontFamily: FontFamily
    get() = FontTestLab.selectedFontKey?.let { FontTestLab.testFonts[it] } ?: LiterataFontFamily

/** Noto Serif (Notes) unless a Font Lab test font is active. */
val notoSerifOrTestFontFamily: FontFamily
    get() = FontTestLab.selectedFontKey?.let { FontTestLab.testFonts[it] } ?: NotoSerifFontFamily
