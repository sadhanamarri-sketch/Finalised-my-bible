package com.example.mybible.ui.richtext

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.ScaleRange
import com.example.mybible.model.StyleRange
import com.example.mybible.model.ThemeMode

// Font size is a relative scale on top of a block's own base size (rather
// than an absolute point size) so the same note keeps reading correctly
// whatever base size the Reader/Notes font setting is on. 1f (index 2) is
// "no change" and is never actually stored — see setScaleRange.
internal val FONT_SCALE_STEPS = floatArrayOf(0.8f, 0.9f, 1f, 1.15f, 1.3f, 1.5f)

// The highlighter mark has no existing Material3 role to borrow (unlike
// heading/blockquote/list-marker accents, which just use colorScheme.primary
// and are already theme-correct for free) — these pairs match the ones
// validated in the formatting preview, brighter yellow on the light themes,
// a deeper amber with light ink on the dark ones so the mark stays legible
// either way.
// A blockquote's left accent bar, drawn with drawBehind rather than a
// separate fillMaxHeight() sibling Box — a Row living inside a scrolling
// (unbounded-height) column can't give a fillMaxHeight() child a real
// constraint to fill and crashes at runtime, whereas drawBehind always sees
// the Row's own final, finite measured size regardless of how that height
// was determined, and reserves the same space via padding so the bar never
// overlaps the block's actual content.
internal fun blockquoteBarModifier(accentColor: Color): Modifier = Modifier
    .drawBehind {
        val barWidth = 3.dp.toPx()
        drawLine(
            color = accentColor,
            start = Offset(barWidth / 2f, 0f),
            end = Offset(barWidth / 2f, size.height),
            strokeWidth = barWidth
        )
    }
    .padding(start = 15.dp)

internal fun highlightColors(themeMode: ThemeMode): Pair<Color, Color> = when (themeMode) {
    ThemeMode.PAPER -> Color(0xFFF6D978) to Color(0xFF3A342C)
    ThemeMode.SEPIA -> Color(0xFFF0CE72) to Color(0xFF3E332A)
    ThemeMode.LIGHT -> Color(0xFFFBE28A) to Color(0xFF1F1F1F)
    ThemeMode.DARK -> Color(0xFF6B5A22) to Color(0xFFFFF6DC)
    ThemeMode.CLASSIC_DARK -> Color(0xFF6E4A2A) to Color(0xFFFFF1E0)
}

// Layers each style category onto the plain text independently, so e.g.
// bold+highlight on the same run compose without needing a combined style
// enum. Ranges are clamped defensively — they're expected to always fall
// within [0, text.length] given how the editor maintains them, but a
// crashed note editor is a much worse failure mode than a silently dropped
// stray range, so this never trusts that blindly.
internal fun buildStyledAnnotatedString(
    text: String,
    bold: List<StyleRange>,
    italic: List<StyleRange>,
    underline: List<StyleRange>,
    highlight: List<StyleRange>,
    fontScale: List<ScaleRange>,
    baseFontSizeSp: Float,
    highlightBg: Color,
    highlightInk: Color
): AnnotatedString {
    val len = text.length
    fun clampOk(start: Int, end: Int) = start.coerceIn(0, len) to end.coerceIn(0, len)
    return buildAnnotatedString {
        append(text)
        bold.forEach {
            val (s, e) = clampOk(it.start, it.end)
            if (s < e) addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
        }
        italic.forEach {
            val (s, e) = clampOk(it.start, it.end)
            if (s < e) addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
        }
        underline.forEach {
            val (s, e) = clampOk(it.start, it.end)
            if (s < e) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
        }
        highlight.forEach {
            val (s, e) = clampOk(it.start, it.end)
            if (s < e) addStyle(SpanStyle(background = highlightBg, color = highlightInk), s, e)
        }
        fontScale.forEach {
            val (s, e) = clampOk(it.start, it.end)
            if (s < e) addStyle(SpanStyle(fontSize = (baseFontSizeSp * it.scale).sp), s, e)
        }
    }
}
