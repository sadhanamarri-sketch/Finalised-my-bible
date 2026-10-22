package com.example.mybible.ui.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.findVerseMentions
import com.example.mybible.model.CharStyle
import com.example.mybible.model.ListMarker
import com.example.mybible.model.NoteEditing
import com.example.mybible.model.NoteText
import com.example.mybible.model.ParaStyle
import com.example.mybible.model.RichAlign
import com.example.mybible.model.RichBlockStyle
import com.example.mybible.model.ThemeMode
import com.example.mybible.ui.theme.NotoSerifFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

/** How a note's text looks: its size, and its colours from the theme. */
@Immutable
data class NoteLook(
    val bodySize: Float,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val highlightBg: Color,
    val highlightInk: Color,
    val paper: Color
)

@Composable
fun rememberNoteLook(themeMode: ThemeMode, bodySize: Float = 19f): NoteLook {
    val scheme = MaterialTheme.colorScheme
    val (bg, ink) = highlightColors(themeMode)
    return remember(scheme, themeMode, bodySize) {
        NoteLook(bodySize, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, bg, ink, scheme.surface)
    }
}

// A paragraph's room on the left, in sp: a step for each level of indent, then room for its mark.
private const val INDENT_STEP = 24f
private const val BULLET_ROOM = 24f
private const val NUMBER_ROOM = 30f
private const val CHECK_ROOM = 32f
private const val QUOTE_ROOM = 18f

// Line height for text, a multiple of its size (the Notes Font Lab's 1.81 for body text).
private const val BODY_LINE = 1.81f
private const val HEADING_LINE = 1.4f

/** Paragraph [p]'s text size, in sp: Title, Heading and Subheading above the body's. */
fun paraSize(p: ParaStyle, look: NoteLook): Float = when (p.style) {
    RichBlockStyle.HEADING_1 -> look.bodySize + 7f
    RichBlockStyle.HEADING_2 -> look.bodySize + 3f
    RichBlockStyle.HEADING_3 -> look.bodySize + 1f
    else -> look.bodySize
}

/** How far paragraph [p]'s text starts from the left, in sp. */
fun paraLead(p: ParaStyle): Float = p.indent * INDENT_STEP + when (p.style) {
    RichBlockStyle.BULLET_ITEM -> BULLET_ROOM
    RichBlockStyle.NUMBERED_ITEM -> NUMBER_ROOM
    RichBlockStyle.CHECK_ITEM -> CHECK_ROOM
    RichBlockStyle.BLOCKQUOTE -> QUOTE_ROOM
    else -> 0f
}

/** The paragraph's own text style: a heading's size, weight and colour, a quote's italic, a ticked item struck through. */
fun paraSpan(p: ParaStyle, look: NoteLook): SpanStyle? = when {
    p.style == RichBlockStyle.HEADING_1 || p.style == RichBlockStyle.HEADING_2 ->
        SpanStyle(fontSize = paraSize(p, look).sp, fontWeight = FontWeight.Bold, color = look.accent)
    p.style == RichBlockStyle.HEADING_3 ->
        SpanStyle(fontSize = paraSize(p, look).sp, fontWeight = FontWeight.SemiBold, fontFamily = WorkSansFontFamily, color = look.ink)
    p.style == RichBlockStyle.BLOCKQUOTE -> SpanStyle(fontStyle = FontStyle.Italic)
    p.style == RichBlockStyle.CHECK_ITEM && p.checked -> SpanStyle(color = look.muted, textDecoration = TextDecoration.LineThrough)
    else -> null
}

fun paraStyle(p: ParaStyle, look: NoteLook): ParagraphStyle {
    val lead = paraLead(p).sp
    return ParagraphStyle(
        textAlign = when (p.align) {
            RichAlign.CENTER -> TextAlign.Center
            RichAlign.END -> TextAlign.End
            RichAlign.START -> TextAlign.Start
        },
        textIndent = TextIndent(lead, lead),
        lineHeight = (paraSize(p, look) * if (p.isHeading) HEADING_LINE else BODY_LINE).sp,
        // Every line its full height, a paragraph's first and last too.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )
}

/** The base style the editor's text starts from. */
fun noteTextStyle(look: NoteLook): TextStyle = TextStyle(
    fontFamily = NotoSerifFontFamily,
    fontSize = look.bodySize.sp,
    color = look.ink,
    lineHeight = (look.bodySize * BODY_LINE).sp
)

/** Character styles over a stretch of text: bold, italic, underline, the highlighter. */
private fun charSpan(style: CharStyle, look: NoteLook): SpanStyle = when (style) {
    CharStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    CharStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    CharStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    CharStyle.HIGHLIGHT -> SpanStyle(background = look.highlightBg, color = look.highlightInk)
}

// Web addresses in a note's text, for links.
private val WEB_LINK = Regex("""\b(?:https?://|www\.)[^\s​]+""", RegexOption.IGNORE_CASE)

/** Web addresses in [text], trailing punctuation left out. */
fun findWebLinks(text: String): List<IntRange> = WEB_LINK.findAll(text).map { m ->
    var end = m.range.last
    while (end > m.range.first && text[end] in ".,;:!?)]}\"'") end--
    m.range.first..end
}.toList()

/**
 * The note as the editor shows it. Compose gives every paragraph style its own paragraph and adds
 * a blank line where one ends with a line break, so on screen each line break is a zero-width
 * space (the same length, so every offset is the same) and each paragraph's style covers it.
 * Verse references and web addresses are marked as they're written.
 */
fun editorText(note: NoteText, look: NoteLook): AnnotatedString = buildAnnotatedString {
    append(note.text.replace('\n', '​'))
    addParagraphs(note, look)
    addCharStyles(note, look, 0)
    val text = note.text
    findVerseMentions(text).forEach { m -> addStyle(SpanStyle(color = linkColor(note, m.range.first, look)), m.range.first, m.range.last + 1) }
    findWebLinks(text).forEach { r ->
        addStyle(SpanStyle(color = linkColor(note, r.first, look), textDecoration = TextDecoration.Underline), r.first, r.last + 1)
    }
}

/** A link's colour: the accent, or grey in a ticked checklist item like the rest of its text. */
internal fun linkColor(note: NoteText, offset: Int, look: NoteLook): Color =
    note.paras[note.paraAt(offset)].let { if (it.style == RichBlockStyle.CHECK_ITEM && it.checked) look.muted else look.accent }

private fun AnnotatedString.Builder.addParagraphs(note: NoteText, look: NoteLook) {
    note.paras.forEachIndexed { i, p ->
        val start = note.paraStart(i)
        val end = if (i + 1 < note.paras.size) note.paraStart(i + 1) else note.text.length
        addStyle(paraStyle(p, look), start, end)
        val textEnd = note.paraEnd(i)
        if (textEnd > start) paraSpan(p, look)?.let { addStyle(it, start, textEnd) }
    }
}

// The character styles, and sizes kept from older notes, of the stretch of the note from [from].
internal fun AnnotatedString.Builder.addCharStyles(note: NoteText, look: NoteLook, from: Int, to: Int = note.text.length, shift: Int = from) {
    fun add(span: SpanStyle, s: Int, e: Int) {
        val a = maxOf(s, from)
        val b = minOf(e, to)
        if (a < b) addStyle(span, a - shift, b - shift)
    }
    CharStyle.entries.forEach { st -> note.ranges(st).forEach { add(charSpan(st, look), it.start, it.end) } }
    note.fontScale.forEach { r ->
        val size = paraSize(note.paras[note.paraAt(r.start)], look) * r.scale
        add(SpanStyle(fontSize = size.sp), r.start, r.end)
    }
}

/** The editor's look for its text: [editorText], offset for offset. */
class NoteVisual(private val note: NoteText, private val look: NoteLook) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = if (text.text == note.text) editorText(note, look) else AnnotatedString(text.text.replace('\n', '​'))
        return TransformedText(shown, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is NoteVisual && other.note == note && other.look == look
    override fun hashCode() = note.hashCode() * 31 + look.hashCode()
}

/** The mark of list item [para] as text ("•", "◦", "▪", "1.", "a."), or null for a tick box or plain line. */
fun markText(note: NoteText, para: Int): String? = when (val m = NoteEditing.marker(note, para)) {
    is ListMarker.Bullet -> when (m.level % 3) { 1 -> "◦"; 2 -> "▪"; else -> "•" }
    is ListMarker.Number -> m.label
    else -> null
}

/** Where paragraph [para]'s tick box is, in the text layout's coordinates. */
fun checkBoxRect(note: NoteText, para: Int, layout: TextLayoutResult, look: NoteLook, density: Density): Rect = with(density) {
    val p = note.paras[para]
    val line = layout.getLineForOffset(note.paraStart(para))
    val top = layout.getLineTop(line)
    val bottom = layout.getLineBottom(line)
    val box = (paraSize(p, look) * 0.95f).sp.toPx()
    val x = (p.indent * INDENT_STEP).sp.toPx() + 2.dp.toPx()
    val y = (top + bottom) / 2f - box / 2f
    Rect(Offset(x, y), Size(box, box))
}

/** The tick box tapped at [at], if any: its paragraph. Taps a little around a box count, for a finger's size. */
fun checkBoxAt(note: NoteText, layout: TextLayoutResult, look: NoteLook, density: Density, at: Offset): Int? {
    val slop = with(density) { 10.dp.toPx() }
    return note.paras.indices.firstOrNull { i ->
        note.paras[i].style == RichBlockStyle.CHECK_ITEM && checkBoxRect(note, i, layout, look, density).inflate(slop).contains(at)
    }
}

/** List marks, tick boxes and quote bars, in each paragraph's room on the left. */
fun DrawScope.drawNoteMarks(note: NoteText, layout: TextLayoutResult, look: NoteLook, measurer: TextMeasurer) {
    note.paras.forEachIndexed { i, p ->
        if (!p.isList && p.style != RichBlockStyle.BLOCKQUOTE) return@forEachIndexed
        val start = note.paraStart(i)
        if (start > layout.layoutInput.text.length) return@forEachIndexed
        val line = layout.getLineForOffset(start)
        val x0 = (p.indent * INDENT_STEP).sp.toPx()
        when (p.style) {
            RichBlockStyle.BLOCKQUOTE -> {
                val last = layout.getLineForOffset(note.paraEnd(i))
                val x = x0 + 3.dp.toPx()
                drawLine(look.accent, Offset(x, layout.getLineTop(line) + 4.dp.toPx()), Offset(x, layout.getLineBottom(last) - 4.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            RichBlockStyle.CHECK_ITEM -> {
                val r = checkBoxRect(note, i, layout, look, this)
                val radius = CornerRadius(4.dp.toPx())
                if (p.checked) {
                    drawRoundRect(look.accent, r.topLeft, r.size, radius)
                    val tick = Path().apply {
                        moveTo(r.left + r.width * 0.24f, r.top + r.height * 0.52f)
                        lineTo(r.left + r.width * 0.43f, r.top + r.height * 0.70f)
                        lineTo(r.left + r.width * 0.77f, r.top + r.height * 0.32f)
                    }
                    drawPath(tick, look.paper, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    val w = 1.6.dp.toPx()
                    drawRoundRect(look.accent, r.topLeft + Offset(w / 2, w / 2), Size(r.width - w, r.height - w), radius, style = Stroke(w))
                }
            }
            else -> {
                val mark = markText(note, i) ?: return@forEachIndexed
                val size = paraSize(p, look)
                val measured = measurer.measure(
                    mark,
                    TextStyle(fontFamily = NotoSerifFontFamily, fontSize = size.sp, color = look.accent, fontWeight = FontWeight.SemiBold)
                )
                val room = paraLead(p).sp.toPx() - x0
                // Bullets at the start of their room; numbers end just before the text.
                val x = if (p.style == RichBlockStyle.NUMBERED_ITEM) maxOf(x0, x0 + room - 6.dp.toPx() - measured.size.width) else x0 + 2.dp.toPx()
                val y = layout.getLineBaseline(line) - measured.firstBaseline
                drawText(measured, topLeft = Offset(x, y))
            }
        }
    }
}

/**
 * Where a tap that put the cursor at [offset] meant it: a tap right of a line's end lands, in
 * the text's own layout, just after that paragraph's (zero-width) line break, which is the start
 * of the next line. If the tap was above that next line, the cursor goes back to the end of the
 * line tapped.
 */
fun cursorForTap(note: NoteText, layout: TextLayoutResult, tapY: Float, offset: Int): Int {
    if (offset <= 0 || offset > note.text.length || note.text[offset - 1] != '\n') return offset
    val line = layout.getLineForOffset(offset)
    return if (tapY < layout.getLineTop(line)) offset - 1 else offset
}
