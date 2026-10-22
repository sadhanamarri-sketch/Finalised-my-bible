package com.example.mybible.ui.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.findVerseMentions
import com.example.mybible.model.NoteText
import com.example.mybible.model.RichBlockStyle
import com.example.mybible.model.ThemeMode

// The note page's look of a note, as the editor shows it (see NoteDisplay.kt): a paragraph at a
// time, so its text can be selected and copied (each line its own, the lines copied with their
// breaks), with list marks, tick boxes that tick when tapped, and quote bars at its left; verse
// references open the verse, web addresses the browser.
@Composable
internal fun RichNoteBodyView(
    note: NoteText,
    themeMode: ThemeMode,
    onMentionClick: (book: String, chapter: Int, verse: Int) -> Unit,
    onToggleCheck: ((para: Int) -> Unit)?,
    baseFontSizeSp: Float = NOTE_TEXT_SIZE,
    modifier: Modifier = Modifier
) {
    val look = rememberNoteLook(themeMode, baseFontSizeSp)
    SelectionContainer(modifier) {
        Column {
            note.paras.forEachIndexed { i, p ->
                // A note that's one empty line shows nothing.
                if (note.paras.size == 1 && note.text.isBlank()) return@forEachIndexed
                val start = note.paraStart(i)
                val end = note.paraEnd(i)
                val text = remember(note, i, look) { paraText(note, i, start, end, look, onMentionClick) }
                val textSize = paraSize(p, look)
                val lead = paraLead(p)
                val markLead = p.indent * INDENT_STEP
                val accent = look.accent
                val line = paraStyle(p, look)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (p.style == RichBlockStyle.BLOCKQUOTE) {
                                Modifier.drawBehind {
                                    val x = (markLead.sp.toPx()) + 3.dp.toPx()
                                    drawLine(accent, Offset(x, 4.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                                }
                            } else Modifier
                        )
                ) {
                    // The paragraph's room at the left, its first line high, with its mark where the
                    // editor draws it (see drawNoteMarks): a bullet at its start, a number just before
                    // the text, a tick box that the whole room ticks or unticks when tapped.
                    val density = LocalDensity.current
                    val markStart = with(density) { markLead.sp.toDp() } + 2.dp
                    Box(Modifier.width(with(density) { lead.sp.toDp() }).height(with(density) { line.lineHeight.toDp() })) {
                        when (p.style) {
                            RichBlockStyle.CHECK_ITEM -> TickBox(
                                checked = p.checked,
                                box = with(density) { (textSize * 0.95f).sp.toDp() },
                                start = markStart,
                                look = look,
                                onClick = onToggleCheck?.let { toggle -> { toggle(i) } },
                                modifier = Modifier.matchParentSize()
                            )
                            RichBlockStyle.BULLET_ITEM, RichBlockStyle.NUMBERED_ITEM -> markText(note, i)?.let { mark ->
                                Text(
                                    mark,
                                    style = noteTextStyle(look).copy(
                                        fontSize = textSize.sp,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = line.lineHeight,
                                        lineHeightStyle = line.lineHeightStyle
                                    ),
                                    modifier = if (p.style == RichBlockStyle.NUMBERED_ITEM) {
                                        Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                                    } else {
                                        Modifier.align(Alignment.CenterStart).padding(start = markStart)
                                    }
                                )
                            }
                            else -> {}
                        }
                    }
                    Text(
                        text = text,
                        style = noteTextStyle(look).merge(
                            TextStyle(
                                fontSize = textSize.sp,
                                lineHeight = line.lineHeight,
                                textAlign = line.textAlign,
                                lineHeightStyle = line.lineHeightStyle
                            )
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// Paragraph [i]'s text, styled, with its verse references and web addresses as links.
private fun paraText(
    note: NoteText,
    i: Int,
    start: Int,
    end: Int,
    look: NoteLook,
    onMentionClick: (String, Int, Int) -> Unit
): AnnotatedString = buildAnnotatedString {
    val line = note.text.substring(start, end)
    append(line)
    paraSpan(note.paras[i], look)?.let { if (line.isNotEmpty()) addStyle(it, 0, line.length) }
    addCharStyles(note, look, start, end, start)
    val linkStyle = TextLinkStyles(SpanStyle(color = linkColor(note, start, look), fontWeight = FontWeight.SemiBold))
    findVerseMentions(line).forEach { m ->
        addLink(
            LinkAnnotation.Clickable("verse", linkStyle) { onMentionClick(m.book, m.chapter, m.verse) },
            m.range.first,
            m.range.last + 1
        )
    }
    findWebLinks(line).forEach { r ->
        val address = line.substring(r.first, r.last + 1)
        val url = if (address.startsWith("www.", ignoreCase = true)) "https://$address" else address
        addLink(
            LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor(note, start, look), textDecoration = TextDecoration.Underline))),
            r.first,
            r.last + 1
        )
    }
}

// A checklist item's box, drawn [start] from the left and centred down the room it's given; the
// whole room ticks or unticks it when tapped, if [onClick] is given.
@Composable
private fun TickBox(checked: Boolean, box: Dp, start: Dp, look: NoteLook, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Checkbox, onClick = onClick) else Modifier)
            .semantics { contentDescription = if (checked) "Ticked" else "Not ticked" }
            .drawBehind {
                val side = box.toPx()
                val at = Offset(start.toPx(), (size.height - side) / 2f)
                val radius = CornerRadius(4.dp.toPx())
                if (checked) {
                    drawRoundRect(look.accent, at, Size(side, side), radius)
                    val tick = Path().apply {
                        moveTo(at.x + side * 0.24f, at.y + side * 0.52f)
                        lineTo(at.x + side * 0.43f, at.y + side * 0.70f)
                        lineTo(at.x + side * 0.77f, at.y + side * 0.32f)
                    }
                    drawPath(tick, look.paper, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    val w = 1.6.dp.toPx()
                    drawRoundRect(look.accent, at + Offset(w / 2, w / 2), Size(side - w, side - w), radius, style = Stroke(w))
                }
            }
    )
}
