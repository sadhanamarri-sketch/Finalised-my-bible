package com.example.mybible.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.mybible.model.QuoteSide

// The colours that mark the Old Testament's quotations in the New: the Reader's †, the Cross References'
// quotations, and Verse Scroll's links. Purple where the New Testament quotes, teal where the Old is
// quoted. Deeper on paper, lighter on the dark themes.
private val PaperQuote = Color(0xFF7A4BAA)
private val DarkQuote = Color(0xFFC3A2EA)
private val PaperQuoted = Color(0xFF1F7A72)
private val DarkQuoted = Color(0xFF7FCFC6)

/** A quotation's colour on a [dark] background or a light one: purple quoting, teal quoted. */
fun quoteColor(side: QuoteSide, dark: Boolean): Color = when (side) {
    QuoteSide.QUOTES -> if (dark) DarkQuote else PaperQuote
    QuoteSide.QUOTED_IN -> if (dark) DarkQuoted else PaperQuoted
}

/** A quotation's colour for the theme on screen. */
@Composable
fun quoteColor(side: QuoteSide): Color = quoteColor(side, MaterialTheme.colorScheme.background.luminance() < 0.5f)
