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
 * Temporary, in-memory-only test rig for the Notes-tab font pass — see the
 * "Notes Font Lab" section in Settings. Deliberately NOT persisted to
 * SharedPreferences/DataStore: it resets to "off" on every app restart by
 * design, since this exists purely to A/B a candidate font against the
 * current Literata/Noto Serif defaults before committing to one for real.
 * Remove this file (and the Settings section, and revert the two
 * properties below to plain references) once a font is chosen.
 */
object FontTestLab {
    // Candidate fonts requested for the Notes-tab A/B test. Regular weight
    // only (single static file per family, no variable-font axes) — bold
    // synthesizes if ever requested, same as EbGaramond/PlayfairDisplay in
    // AppFonts.kt.
    val testFonts: Map<String, FontFamily> = linkedMapOf(
        "Nunito" to FontFamily(Font(R.font.nunito_regular, FontWeight.Normal)),
        "Quicksand" to FontFamily(Font(R.font.quicksand_regular, FontWeight.Normal)),
        "Dosis" to FontFamily(Font(R.font.dosis_regular, FontWeight.Normal)),
        "Zilla Slab" to FontFamily(Font(R.font.zilla_slab_regular, FontWeight.Normal)),
        "Source Serif 4" to FontFamily(Font(R.font.source_serif_4_regular, FontWeight.Normal)),
        "Varela Round" to FontFamily(Font(R.font.varela_round_regular, FontWeight.Normal)),
        "Antic Slab" to FontFamily(Font(R.font.antic_slab_regular, FontWeight.Normal)),
        "Crimson Text" to FontFamily(Font(R.font.crimson_text_regular, FontWeight.Normal))
    )

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
