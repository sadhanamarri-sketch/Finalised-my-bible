@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.mybible.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.mybible.R

/**
 * BUG FIX ("English font is not applicable"): this previously fetched
 * Lora / EB Garamond / Merriweather / Playfair Display / Gelasio from
 * Google's *downloadable* font provider (Play Services), which:
 *  - needs Google Play Services installed & up to date (missing on many
 *    emulators and some devices), AND
 *  - needs network on first use to actually fetch the file.
 * When either was missing, Compose silently falls back to the system
 * default font for that text instead of crashing — which from the user's
 * side looks exactly like "picking a font does nothing."
 *
 * Fix: bundle real font files directly under res/font/ so font selection
 * is instant, offline, and works on every device — no Play Services, no
 * network, consistent with the rest of the app's offline-first design.
 *
 * Lora is the real, authentic typeface (static Regular/Bold instances
 * generated from the official variable font). Two others are open-license
 * (SIL OFL) stand-ins chosen to look genuinely different from each other
 * and from Lora, since the exact named files weren't available to bundle
 * here:
 *   - "EB Garamond" slot -> GFS Porson (classical old-style serif)
 *   - "Playfair Display" slot -> GFS Baskerville (high-contrast display serif)
 * To swap in the authentic Google Fonts files later: download the .ttf from
 * fonts.google.com, drop it in res/font/ under the same file name used
 * below, and rebuild — no code changes needed.
 *
 * "Georgia" is intentionally NOT a bundled file — see SystemSerifFontFamily
 * comment below for why.
 */
val LoraFontFamily: FontFamily = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_bold, FontWeight.Bold)
)
val EbGaramondFontFamily: FontFamily = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal)
    // No dedicated bold file for this stand-in; Compose synthesizes bold
    // (FontSynthesis.All is the TextStyle default) when Bold is requested.
)
val MerriweatherFontFamily: FontFamily = FontFamily(
    Font(R.font.merriweather_regular, FontWeight.Normal),
    Font(R.font.merriweather_bold, FontWeight.Bold)
)
val PlayfairDisplayFontFamily: FontFamily = FontFamily(
    Font(R.font.playfair_display_regular, FontWeight.Normal)
    // Synthesized bold, same reasoning as EB Garamond above.
)

/**
 * "Georgia" option, and the new default font (matching Capacitor's
 * DEFAULT_FONT = 'georgia').
 *
 * Deliberately FontFamily.Serif, not a bundled substitute file. Capacitor's
 * stack is `Georgia, 'Iowan Old Style', 'Palatino Linotype', 'Book
 * Antiqua', serif` — none of those named fonts exist on stock Android, so
 * the WebView falls all the way through to the generic `serif` keyword,
 * which Android resolves to its system default serif (Noto Serif / Droid
 * Serif depending on OS version/OEM). Compose's FontFamily.Serif resolves
 * to that exact same system font, so this matches what Capacitor actually
 * renders on Android — device for device — rather than a fixed bundled
 * file that could drift from it.
 */
val GelasioFontFamily: FontFamily = FontFamily(
    Font(R.font.gelasio_regular, FontWeight.Normal),
    Font(R.font.gelasio_bold, FontWeight.Bold)
)
// Georgia itself is proprietary (Microsoft/Monotype) and its license
// doesn't permit redistributing the .ttf, even for a private/personal
// build. Gelasio is Google's metric-compatible, SIL-OFL-licensed
// substitute — same letterforms and spacing as Georgia, free to bundle.

/**
 * Font-consistency pass: Literata (secondary Scripture surfaces — search
 * results, cross references, lexicon, verse previews), Work Sans (UI
 * chrome — buttons, menus, segmented controls, switch labels), Fraunces
 * (headers/titles), and Noto Serif (Notes) are pinned to real bundled
 * files instead of the generic FontFamily.Serif / FontFamily.Default
 * aliases used before. Those aliases resolve to whatever a given device
 * or OEM ships as its system serif/sans-serif, which is exactly what made
 * "Noto Serif" render differently across devices/renderers.
 *
 * All four ship upstream as variable fonts, so each named weight below is
 * an instance of the SAME bundled file selected via FontVariation, not a
 * separate download — one file per family, multiple weights for free.
 */
val LiterataFontFamily: FontFamily = FontFamily(
    Font(
        R.font.literata_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.literata_variable,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

val WorkSansFontFamily: FontFamily = FontFamily(
    Font(
        R.font.work_sans_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.work_sans_variable,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.work_sans_variable,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.work_sans_variable,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

val FrauncesFontFamily: FontFamily = FontFamily(
    Font(
        R.font.fraunces_variable,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.fraunces_variable,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

val NotoSerifFontFamily: FontFamily = FontFamily(
    Font(
        R.font.noto_serif_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.noto_serif_variable,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)
