package com.example.mybible.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// The purple that marks the Old Testament's quotations in the New: the Reader's †, the Cross
// References' quotations, and Verse Scroll's links. Deeper on paper, lighter on the dark themes.
private val PaperQuote = Color(0xFF7A4BAA)
private val DarkQuote = Color(0xFFC3A2EA)

/** The quotations' purple on a [dark] background or a light one. */
fun quotePurple(dark: Boolean): Color = if (dark) DarkQuote else PaperQuote

/** The quotations' purple for the theme on screen. */
@Composable
fun quotePurple(): Color = quotePurple(MaterialTheme.colorScheme.background.luminance() < 0.5f)
