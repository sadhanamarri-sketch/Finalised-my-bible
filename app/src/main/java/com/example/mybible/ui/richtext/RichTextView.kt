package com.example.mybible.ui.richtext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.VERSE_MENTION_TAG
import com.example.mybible.data.findVerseMentions
import com.example.mybible.model.NoteDocument
import com.example.mybible.model.RichAlign
import com.example.mybible.model.RichBlock
import com.example.mybible.model.RichBlockStyle
import com.example.mybible.model.ThemeMode
import com.example.mybible.ui.theme.NotoSerifFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

// Read-only rendering of a note's rich document — used by NoteReaderScreen
// once a note has been through the rich editor (richText non-blank).
// Mirrors RichNoteBodyEditor's visual treatment (same heading/blockquote/
// list/highlight look) but as plain Text/ClickableText, and layers in the
// same Bible-reference-mention linkification the plain-text note view
// already had (see VerseMentionLinkifier.kt) so upgrading a note to rich
// text never regresses that.
@Composable
internal fun RichNoteBodyView(
    document: NoteDocument,
    themeMode: ThemeMode,
    onMentionClick: (book: String, chapter: Int, verse: Int) -> Unit,
    baseFontSizeSp: Float = 19f,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val (highlightBg, highlightInk) = highlightColors(themeMode)

    Column(modifier = modifier) {
        document.blocks.forEachIndexed { index, block ->
            val fontFamily = if (block.style == RichBlockStyle.HEADING_3) WorkSansFontFamily else NotoSerifFontFamily
            val fontWeight = when (block.style) {
                RichBlockStyle.HEADING_1, RichBlockStyle.HEADING_2 -> FontWeight.Bold
                RichBlockStyle.HEADING_3 -> FontWeight.SemiBold
                else -> FontWeight.Normal
            }
            val fontStyle = if (block.style == RichBlockStyle.BLOCKQUOTE) FontStyle.Italic else FontStyle.Normal
            val fontSizeSp = when (block.style) {
                RichBlockStyle.HEADING_1 -> baseFontSizeSp + 5f
                RichBlockStyle.HEADING_2 -> baseFontSizeSp + 2f
                RichBlockStyle.HEADING_3 -> (baseFontSizeSp - 6f).coerceAtLeast(11f)
                else -> baseFontSizeSp
            }
            val textColor = when (block.style) {
                RichBlockStyle.HEADING_1, RichBlockStyle.HEADING_2 -> accentColor
                RichBlockStyle.HEADING_3 -> mutedColor
                else -> bodyColor
            }
            val textAlign = when (block.align) {
                RichAlign.CENTER -> TextAlign.Center
                RichAlign.END -> TextAlign.End
                RichAlign.START -> TextAlign.Start
            }

            val annotated = remember(
                block.text, block.bold, block.italic, block.underline,
                block.highlight, block.fontScale, highlightBg, highlightInk, accentColor, fontSizeSp
            ) {
                val base = buildStyledAnnotatedString(
                    block.text, block.bold, block.italic, block.underline,
                    block.highlight, block.fontScale, fontSizeSp, highlightBg, highlightInk
                )
                val mentions = findVerseMentions(block.text)
                if (mentions.isEmpty()) base
                else buildAnnotatedString {
                    append(base)
                    mentions.forEach { m ->
                        val start = m.range.first
                        val end = m.range.last + 1
                        addStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold), start, end)
                        addStringAnnotation(VERSE_MENTION_TAG, "${m.book}|${m.chapter}|${m.verse}", start, end)
                    }
                }
            }

            if (block.text.isBlank() && document.blocks.size == 1) return@forEachIndexed

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (block.indent * 20).dp,
                        bottom = if (block.style == RichBlockStyle.HEADING_1 || block.style == RichBlockStyle.HEADING_2) 10.dp else 6.dp
                    )
                    .then(if (block.style == RichBlockStyle.BLOCKQUOTE) blockquoteBarModifier(accentColor) else Modifier)
            ) {
                when (block.style) {
                    RichBlockStyle.BULLET_ITEM -> Text(
                        "•",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSizeSp.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    RichBlockStyle.NUMBERED_ITEM -> Text(
                        "${numberedPositionInDoc(document.blocks, index)}.",
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fontSizeSp.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    else -> {}
                }
                ClickableText(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        fontSize = fontSizeSp.sp,
                        color = textColor,
                        textAlign = textAlign,
                        lineHeight = (fontSizeSp * 1.55f).sp
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = { offset ->
                        annotated.getStringAnnotations(VERSE_MENTION_TAG, offset, offset).firstOrNull()?.let { hit ->
                            val parts = hit.item.split("|")
                            if (parts.size == 3) {
                                val chapter = parts[1].toIntOrNull()
                                val verse = parts[2].toIntOrNull()
                                if (chapter != null && verse != null) onMentionClick(parts[0], chapter, verse)
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun numberedPositionInDoc(blocks: List<RichBlock>, index: Int): Int {
    var n = 1
    var i = index - 1
    while (i >= 0 && blocks[i].style == RichBlockStyle.NUMBERED_ITEM) {
        n++
        i--
    }
    return n
}
