package com.example.mybible.ui.richtext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteMarkdown
import com.example.mybible.model.ParaStyle
import com.example.mybible.model.RichBlockStyle

// A blank line between blocks: this much of the text's size, as space.
private const val BLOCK_GAP = 0.75f

/**
 * A note's plain lines written in markdown, on the note page (see NoteMarkdown): headings as the
 * editor's Title, Heading and Subheading, quotes with its quote bar and in italic, list items with
 * its bullet, all in the note's own type and colours; verse references and web addresses as links,
 * found in the text as it shows (its marks gone), so they open what they name.
 */
@Composable
internal fun MarkdownBlocks(
    blocks: List<NoteMarkdown.Block>,
    look: NoteLook,
    onMentionClick: (book: String, chapter: Int, verse: Int) -> Unit
) {
    val density = LocalDensity.current
    blocks.forEachIndexed { k, block ->
        if (k > 0 && block.spaced) Spacer(Modifier.height(with(density) { (look.bodySize * BLOCK_GAP).sp.toDp() }))
        when (block) {
            is NoteMarkdown.Heading -> MarkdownText(
                block.text,
                ParaStyle(
                    style = when (block.level) {
                        1 -> RichBlockStyle.HEADING_1
                        2 -> RichBlockStyle.HEADING_2
                        else -> RichBlockStyle.HEADING_3
                    }
                ),
                look,
                onMentionClick,
                Modifier.fillMaxWidth()
            )
            is NoteMarkdown.Paragraph -> MarkdownText(block.text, ParaStyle(), look, onMentionClick, Modifier.fillMaxWidth())
            is NoteMarkdown.Quote -> {
                // The editor's quote: a bar in the accent colour at the left, the text in italic.
                val p = ParaStyle(style = RichBlockStyle.BLOCKQUOTE)
                val accent = look.accent
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val x = 3.dp.toPx()
                            drawLine(accent, Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                        }
                ) {
                    Spacer(Modifier.width(with(density) { paraLead(p).sp.toDp() }))
                    MarkdownText(block.text, p, look, onMentionClick, Modifier.weight(1f))
                }
            }
            is NoteMarkdown.Bullet -> {
                // The editor's list item: its bullet in the room at the left, centred on the first line.
                val p = ParaStyle(style = RichBlockStyle.BULLET_ITEM)
                val line = paraStyle(p, look)
                Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(with(density) { paraLead(p).sp.toDp() })
                            .height(with(density) { line.lineHeight.toDp() })
                    ) {
                        Text(
                            "•",
                            style = noteTextStyle(look).copy(
                                fontSize = look.bodySize.sp,
                                color = look.accent,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = line.lineHeight,
                                lineHeightStyle = line.lineHeightStyle
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 2.dp)
                        )
                    }
                    MarkdownText(block.text, p, look, onMentionClick, Modifier.weight(1f))
                }
            }
        }
    }
}

// A block's text in paragraph [p]'s look, with its bold, italic and links.
@Composable
private fun MarkdownText(
    inline: NoteMarkdown.Inline,
    p: ParaStyle,
    look: NoteLook,
    onMentionClick: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val text = remember(inline, p, look) { markdownText(inline, p, look, onMentionClick) }
    val line = paraStyle(p, look)
    Text(
        text = text,
        style = noteTextStyle(look).merge(
            TextStyle(
                fontSize = paraSize(p, look).sp,
                lineHeight = line.lineHeight,
                lineHeightStyle = line.lineHeightStyle
            )
        ),
        modifier = modifier
    )
}

/**
 * [inline]'s text, its marks gone: paragraph [p]'s look over it all (a heading's size and colour, a
 * quote's italic), its **bold** and *italic*, then its verse references and web addresses as links,
 * found in this text, so where they are matches what shows.
 */
internal fun markdownText(
    inline: NoteMarkdown.Inline,
    p: ParaStyle,
    look: NoteLook,
    onMentionClick: (String, Int, Int) -> Unit
): AnnotatedString = buildAnnotatedString {
    append(inline.text)
    if (inline.text.isNotEmpty()) paraSpan(p, look)?.let { addStyle(it, 0, inline.text.length) }
    inline.bold.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.start, it.end) }
    inline.italic.forEach { addStyle(SpanStyle(fontStyle = FontStyle.Italic), it.start, it.end) }
    addNoteLinks(inline.text, look.accent, onMentionClick)
}
