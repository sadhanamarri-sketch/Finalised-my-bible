package com.example.mybible.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.mybible.R

/**
 * Temporary, in-memory-only test rig for the verse-card font pass — see
 * the "Verse Card Font Lab" section in Settings. Deliberately NOT
 * persisted to SharedPreferences/DataStore: it resets to "off" on every
 * app restart by design, since this exists purely to A/B a candidate
 * font/size/spacing against the current Literata default before
 * committing to one for real.
 *
 * Scope: Search result cards, Cross-Reference cards (pinned source +
 * row previews), the verse-mention preview sheet, and Highlighted Verses
 * cards — the four surfaces that currently share the same 17sp/24sp
 * Literata treatment, kept in sync here by design (one shared choice
 * drives all four, not one each).
 *
 * Remove this file (and the Settings section, and revert
 * verseCardFontFamily/verseCardFontSizeSp/verseCardLineHeightSp call
 * sites back to plain LiterataFontFamily/17.sp/24.sp) once a choice is
 * made for real.
 */
object VerseCardFontLab {
    val testFonts: Map<String, FontFamily> = linkedMapOf(
        "Noto Serif" to NotoSerifFontFamily,
        "Source Serif 4" to FontFamily(Font(R.font.source_serif_4_regular, FontWeight.Normal)),
        "Nunito" to FontFamily(Font(R.font.nunito_regular, FontWeight.Normal))
    )

    var selectedFontKey by mutableStateOf<String?>(null)

    var fontSizeSp by mutableFloatStateOf(17f)
    var lineHeightMultiplier by mutableFloatStateOf(24f / 17f)
}

/** Literata unless a Verse Card Font Lab test font is active. */
val verseCardFontFamily: FontFamily
    get() = VerseCardFontLab.selectedFontKey?.let { VerseCardFontLab.testFonts[it] } ?: LiterataFontFamily
