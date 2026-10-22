package com.example.mybible.model

// The note editor's working copy of a note: its whole text as one string, paragraphs split by
// '\n', each paragraph's own look (ParaStyle), and the character styles over the text, as
// offsets into the whole. One text box holds it all, so a selection can cross paragraphs; it
// goes to and from the stored NoteDocument (a block per paragraph) when a note is opened and
// saved. Plain Kotlin, so the editing rules below are tested without a screen.

/** A character style the toolbar turns on and off. */
enum class CharStyle { BOLD, ITALIC, UNDERLINE, HIGHLIGHT }

/** How deep a list item can be nested, or a paragraph indented (Tab). */
const val MAX_INDENT = 4

/** A paragraph's own look: its kind, alignment, indent (a list item's depth) and, a checklist item's, tick. */
data class ParaStyle(
    val style: RichBlockStyle = RichBlockStyle.PARAGRAPH,
    val align: RichAlign = RichAlign.START,
    val indent: Int = 0,
    val checked: Boolean = false
) {
    val isList: Boolean
        get() = style == RichBlockStyle.BULLET_ITEM || style == RichBlockStyle.NUMBERED_ITEM || style == RichBlockStyle.CHECK_ITEM

    val isHeading: Boolean
        get() = style == RichBlockStyle.HEADING_1 || style == RichBlockStyle.HEADING_2 || style == RichBlockStyle.HEADING_3
}

data class NoteText(
    val text: String,
    val paras: List<ParaStyle>,
    val styles: Map<CharStyle, List<StyleRange>> = emptyMap(),
    // Sizes set in older notes; kept and shown, but nothing sets them now.
    val fontScale: List<ScaleRange> = emptyList()
) {
    init {
        require(paras.size == text.count { it == '\n' } + 1) { "a paragraph look for each line" }
    }

    // Where each paragraph starts.
    private val starts: IntArray by lazy {
        val list = ArrayList<Int>(paras.size)
        list.add(0)
        text.forEachIndexed { i, c -> if (c == '\n') list.add(i + 1) }
        list.toIntArray()
    }

    fun ranges(style: CharStyle): List<StyleRange> = styles[style].orEmpty()

    /** The paragraph [offset] is in (an offset at a line's end belongs to that line). */
    fun paraAt(offset: Int): Int {
        val o = offset.coerceIn(0, text.length)
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= o) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun paraStart(i: Int): Int = starts[i]

    /** Where paragraph [i] ends: at its '\n', or the end of the text. */
    fun paraEnd(i: Int): Int = if (i + 1 < starts.size) starts[i + 1] - 1 else text.length

    /** The paragraphs a selection from [start] to [end] touches. */
    fun parasIn(start: Int, end: Int): IntRange = paraAt(minOf(start, end))..paraAt(maxOf(start, end))

    fun isEmpty(): Boolean = text.isBlank()

    fun toDocument(): NoteDocument = NoteDocument(paras.indices.map { i ->
        val start = paraStart(i)
        val end = paraEnd(i)
        fun clip(rs: List<StyleRange>) = rs.mapNotNull { r ->
            val s = maxOf(r.start, start)
            val e = minOf(r.end, end)
            if (s < e) StyleRange(s - start, e - start) else null
        }
        val p = paras[i]
        RichBlock(
            text = text.substring(start, end),
            style = p.style,
            align = p.align,
            indent = p.indent,
            bold = clip(ranges(CharStyle.BOLD)),
            italic = clip(ranges(CharStyle.ITALIC)),
            underline = clip(ranges(CharStyle.UNDERLINE)),
            highlight = clip(ranges(CharStyle.HIGHLIGHT)),
            fontScale = fontScale.mapNotNull { r ->
                val s = maxOf(r.start, start)
                val e = minOf(r.end, end)
                if (s < e) ScaleRange(s - start, e - start, r.scale) else null
            },
            checked = p.checked && p.style == RichBlockStyle.CHECK_ITEM
        )
    })

    /**
     * The note as plain text, for the list of notes, search and backups: a line a paragraph, list
     * and checklist items marked as they show ("• ", "1. ", "☐ ", "☑ "), nested ones indented.
     */
    fun plainText(): String = paras.indices.joinToString("\n") { i ->
        val line = text.substring(paraStart(i), paraEnd(i))
        val p = paras[i]
        if (!p.isList) line else "  ".repeat(p.indent) + when (val m = NoteEditing.marker(this, i)) {
            is ListMarker.Bullet -> "• "
            is ListMarker.Number -> m.label + " "
            is ListMarker.Check -> if (m.checked) "☑ " else "☐ "
            ListMarker.None -> ""
        } + line
    }

    companion object {
        val Empty = NoteText("", listOf(ParaStyle()))

        fun from(doc: NoteDocument): NoteText {
            val blocks = doc.blocks.ifEmpty { listOf(RichBlock()) }
            val sb = StringBuilder()
            val paras = ArrayList<ParaStyle>(blocks.size)
            val styles = CharStyle.entries.associateWith { mutableListOf<StyleRange>() }
            val scales = mutableListOf<ScaleRange>()
            blocks.forEachIndexed { i, b ->
                if (i > 0) sb.append('\n')
                val o = sb.length
                // A block is one line; any line break in one (never written) becomes a space.
                val line = b.text.replace('\n', ' ')
                sb.append(line)
                fun shift(rs: List<StyleRange>) = rs.mapNotNull { r ->
                    val s = r.start.coerceIn(0, line.length)
                    val e = r.end.coerceIn(0, line.length)
                    if (s < e) StyleRange(s + o, e + o) else null
                }
                styles.getValue(CharStyle.BOLD) += shift(b.bold)
                styles.getValue(CharStyle.ITALIC) += shift(b.italic)
                styles.getValue(CharStyle.UNDERLINE) += shift(b.underline)
                styles.getValue(CharStyle.HIGHLIGHT) += shift(b.highlight)
                b.fontScale.forEach { r ->
                    val s = r.start.coerceIn(0, line.length)
                    val e = r.end.coerceIn(0, line.length)
                    if (s < e) scales += ScaleRange(s + o, e + o, r.scale)
                }
                paras += ParaStyle(b.style, b.align, b.indent.coerceIn(0, MAX_INDENT), b.checked && b.style == RichBlockStyle.CHECK_ITEM)
            }
            return NoteText(
                sb.toString(),
                paras,
                styles.mapValues { mergeStyleRanges(it.value) }.filterValues { it.isNotEmpty() },
                mergeScaleRanges(scales)
            )
        }

        /** A note written before formatting: a plain paragraph a line. */
        fun plain(text: String): NoteText = NoteText(text, List(text.count { it == '\n' } + 1) { ParaStyle() })
    }
}

/** The mark before a list or checklist item: a bullet by depth, a number ("1.", "a.", "i."), or a tick box. */
sealed interface ListMarker {
    data class Bullet(val level: Int) : ListMarker
    data class Number(val label: String) : ListMarker
    data class Check(val checked: Boolean) : ListMarker
    data object None : ListMarker
}

/**
 * A note after an edit: the text, where the cursor or selection is, and the styles typing goes
 * on in ([typing], null: those of the text at the cursor). [rewritten]: the text isn't what the
 * text box reported, a rule having changed it, so the box is given it anew.
 */
data class Edited(
    val note: NoteText,
    val selStart: Int,
    val selEnd: Int,
    val typing: Set<CharStyle>? = null,
    val rewritten: Boolean = false
)

object NoteEditing {

    // Typed at the start of a plain line and followed by a space, these make the line a list,
    // a checklist item, a quote or a heading, as in Google Docs and Notion.
    private val SHORTCUTS: Map<String, ParaStyle> = mapOf(
        "-" to ParaStyle(RichBlockStyle.BULLET_ITEM),
        "*" to ParaStyle(RichBlockStyle.BULLET_ITEM),
        "•" to ParaStyle(RichBlockStyle.BULLET_ITEM),
        "1." to ParaStyle(RichBlockStyle.NUMBERED_ITEM),
        "1)" to ParaStyle(RichBlockStyle.NUMBERED_ITEM),
        "[]" to ParaStyle(RichBlockStyle.CHECK_ITEM),
        "[ ]" to ParaStyle(RichBlockStyle.CHECK_ITEM),
        "[x]" to ParaStyle(RichBlockStyle.CHECK_ITEM, checked = true),
        ">" to ParaStyle(RichBlockStyle.BLOCKQUOTE),
        "#" to ParaStyle(RichBlockStyle.HEADING_1),
        "##" to ParaStyle(RichBlockStyle.HEADING_2),
        "###" to ParaStyle(RichBlockStyle.HEADING_3)
    )

    /**
     * The note after the text box reports [newText] with the selection [newSelStart]–[newSelEnd],
     * in place of [old]'s text with [oldSelStart]–[oldSelEnd]; what's typed takes [typing] (null:
     * the look of the text it's typed into).
     *
     * Its rules, before the edit itself: Enter on an empty list item, checklist item or quote line
     * ends it (a nested one moves out a level); Backspace at the start of one makes it a plain line
     * (then an indented line moves back, then lines join); "- ", "1. ", "[] ", "> ", "# " at the start
     * of a plain line make it a list, checklist, quote or heading. What's typed carries on the look
     * of the text before it in the line (at a line's start, of the text after it), a replacement
     * (an autocorrection) that of what it replaces. A new line after a list item is an item, after a
     * heading plain text; a line opened above another takes its look.
     */
    fun edit(
        old: NoteText,
        oldSelStart: Int,
        oldSelEnd: Int,
        newText: String,
        newSelStart: Int,
        newSelEnd: Int,
        typing: Set<CharStyle>?
    ): Edited {
        if (newText == old.text) {
            // Only the cursor or selection moved: typing styles last while it stays put.
            val stayed = newSelStart == oldSelStart && newSelEnd == oldSelEnd
            return Edited(old, newSelStart, newSelEnd, typing = if (stayed) typing else null)
        }
        val cursor = if (oldSelStart == oldSelEnd) oldSelStart else null
        val d = diffAtCursor(old.text, newText, cursor)

        // Enter on an empty list item, checklist item or quote line: out of it.
        if (d.insertText == "\n" && d.deleteStart == d.deleteEnd) {
            val p = old.paraAt(d.deleteStart)
            val para = old.paras[p]
            if (old.paraStart(p) == old.paraEnd(p) && (para.isList || para.style == RichBlockStyle.BLOCKQUOTE)) {
                val out = if (para.indent > 0 && para.isList) para.copy(indent = para.indent - 1) else ParaStyle(align = para.align)
                return Edited(old.withPara(p, out), d.deleteStart, d.deleteStart, typing, rewritten = true)
            }
        }

        // Backspace at the start of a line (the line break before it taken away, the cursor having
        // been just after it): a list item, checklist item or quote line first becomes plain, an
        // indented line moves back; only then do the lines join.
        if (d.insertText.isEmpty() && d.deleteEnd - d.deleteStart == 1 && old.text[d.deleteStart] == '\n' &&
            cursor == d.deleteEnd
        ) {
            val p = old.paraAt(d.deleteEnd)
            unindentAtStart(old.paras[p])?.let { out ->
                return Edited(old.withPara(p, out), d.deleteEnd, d.deleteEnd, typing, rewritten = true)
            }
        }

        // A shortcut typed at the start of a plain line, then a space.
        if (d.insertText == " " && d.deleteStart == d.deleteEnd && newSelStart == newSelEnd && newSelStart == d.deleteStart + 1) {
            val p = old.paraAt(d.deleteStart)
            val start = old.paraStart(p)
            val shortcut = SHORTCUTS[old.text.substring(start, d.deleteStart)]
            if (shortcut != null && old.paras[p].style == RichBlockStyle.PARAGRAPH) {
                val removed = apply(old, TextDiff(start, d.deleteStart, ""), emptySet())
                val para = removed.paras[p]
                return Edited(
                    removed.withPara(p, para.copy(style = shortcut.style, checked = shortcut.checked)),
                    start, start, typing, rewritten = true
                )
            }
        }

        val inserted: Set<CharStyle> = when {
            d.insertText.isEmpty() -> emptySet()
            typing != null -> typing
            d.deleteEnd > d.deleteStart -> stylesOf(old, d.deleteStart)
            else -> stylesAt(old, d.deleteStart)
        }
        val edited = apply(old, d, inserted)
        // A new line takes the styles typing was in; otherwise typing goes on in them while the
        // cursor stays at the end of what was just typed.
        val atEnd = newSelStart == newSelEnd && newSelStart == d.deleteStart + d.insertText.length
        val nextTyping = when {
            d.insertText.contains('\n') && atEnd -> inserted.takeIf { it.isNotEmpty() || typing != null }
            atEnd && d.insertText.isNotEmpty() -> typing
            else -> null
        }
        return Edited(edited, newSelStart, newSelEnd, nextTyping)
    }

    /**
     * [quote] (a verse, say) put in as a quote line of its own after the cursor's line, or in it if
     * it's empty, with an empty line after it to go on writing in, where the cursor goes.
     */
    fun insertQuote(note: NoteText, at: Int, quote: String): Edited {
        val p = note.paraAt(at)
        val empty = note.paraStart(p) == note.paraEnd(p)
        val end = note.paraEnd(p)
        val insert = (if (empty) "" else "\n") + quote.replace('\n', ' ') + "\n"
        val quoted = if (empty) p else p + 1
        val n = apply(note, TextDiff(end, end, insert), emptySet())
            .withPara(quoted, ParaStyle(RichBlockStyle.BLOCKQUOTE))
            .withPara(quoted + 1, ParaStyle())
        val cursor = end + insert.length
        return Edited(n, cursor, cursor, typing = null, rewritten = true)
    }

    /**
     * Backspace at the very start of the note (nothing before it to take away): a list item,
     * checklist item or quote line becomes plain, an indented one moves back. Null: nothing to do.
     */
    fun backspaceAtStart(note: NoteText): NoteText? = unindentAtStart(note.paras[0])?.let { note.withPara(0, it) }

    private fun unindentAtStart(para: ParaStyle): ParaStyle? = when {
        para.isList || para.style == RichBlockStyle.BLOCKQUOTE -> ParaStyle(align = para.align, indent = para.indent)
        para.indent > 0 -> para.copy(indent = para.indent - 1)
        else -> null
    }

    /** The styles typing at [pos] takes: those of the character before it in its line, or at a line's start, after it. */
    fun stylesAt(note: NoteText, pos: Int): Set<CharStyle> {
        val p = note.paraAt(pos)
        val start = note.paraStart(p)
        val end = note.paraEnd(p)
        val probe = when {
            pos > start -> pos - 1
            pos < end -> pos
            else -> return emptySet()
        }
        return stylesOf(note, probe)
    }

    private fun stylesOf(note: NoteText, at: Int): Set<CharStyle> =
        CharStyle.entries.filter { st -> note.ranges(st).any { at >= it.start && at < it.end } }.toSet()

    /** Whether [style] is on for the selection: every character of it (line breaks aside), or at a cursor, typing there. */
    fun hasStyle(note: NoteText, start: Int, end: Int, style: CharStyle): Boolean {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        if (s == e) return style in stylesAt(note, s)
        val ranges = note.ranges(style)
        return (s until e).all { i -> note.text[i] == '\n' || ranges.any { i >= it.start && i < it.end } }
    }

    /** [style] on over the selection, or off if it's on all of it already. */
    fun toggleStyle(note: NoteText, start: Int, end: Int, style: CharStyle): NoteText {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        if (s == e) return note
        val cleared = removeRange(note.ranges(style), s, e)
        val ranges = if (hasStyle(note, s, e, style)) cleared else mergeStyleRanges(cleared + StyleRange(s, e))
        return note.withStyle(style, ranges)
    }

    /** The paragraphs of the selection made [style] (a heading, a quote, plain text). */
    fun setParaStyle(note: NoteText, start: Int, end: Int, style: RichBlockStyle): NoteText =
        note.mapParas(start, end) { it.copy(style = style, checked = false) }

    /** The paragraphs of the selection made [style] (a list, a checklist, a quote), or plain again if they all are. */
    fun toggleParaStyle(note: NoteText, start: Int, end: Int, style: RichBlockStyle): NoteText {
        val all = note.parasIn(start, end).all { note.paras[it].style == style }
        return note.mapParas(start, end) { it.copy(style = if (all) RichBlockStyle.PARAGRAPH else style, checked = false) }
    }

    /** Tab ([by] 1) and Shift-Tab (-1): a list item nested deeper or shallower, a paragraph indented or not. */
    fun indent(note: NoteText, start: Int, end: Int, by: Int): NoteText =
        note.mapParas(start, end) { it.copy(indent = (it.indent + by).coerceIn(0, MAX_INDENT)) }

    fun setAlign(note: NoteText, start: Int, end: Int, align: RichAlign): NoteText =
        note.mapParas(start, end) { it.copy(align = align) }

    fun toggleChecked(note: NoteText, para: Int): NoteText {
        val p = note.paras.getOrNull(para)?.takeIf { it.style == RichBlockStyle.CHECK_ITEM } ?: return note
        return note.withPara(para, p.copy(checked = !p.checked))
    }

    /** Every character style off over the selection, and its headings and quotes plain text again. */
    fun clearFormatting(note: NoteText, start: Int, end: Int): NoteText {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        var out = note.mapParas(s, e) {
            if (it.isHeading || it.style == RichBlockStyle.BLOCKQUOTE) it.copy(style = RichBlockStyle.PARAGRAPH) else it
        }
        if (s < e) {
            CharStyle.entries.forEach { st -> out = out.withStyle(st, removeRange(out.ranges(st), s, e)) }
            out = out.copy(fontScale = out.fontScale.flatMap { r ->
                listOfNotNull(
                    if (r.start < s) ScaleRange(r.start, minOf(r.end, s), r.scale) else null,
                    if (r.end > e) ScaleRange(maxOf(r.start, e), r.end, r.scale) else null
                )
            })
        }
        return out
    }

    /**
     * The mark before list or checklist item [para]: bullets • ◦ ▪ by depth; numbers 1. a. i. by
     * depth, counted along the list and starting again under each item a level up, or after a
     * line that isn't in the list.
     */
    fun marker(note: NoteText, para: Int): ListMarker {
        val p = note.paras[para]
        return when (p.style) {
            RichBlockStyle.BULLET_ITEM -> ListMarker.Bullet(p.indent)
            RichBlockStyle.CHECK_ITEM -> ListMarker.Check(p.checked)
            RichBlockStyle.NUMBERED_ITEM -> {
                var n = 1
                var i = para - 1
                while (i >= 0) {
                    val q = note.paras[i]
                    if (!q.isList || q.indent < p.indent) break
                    if (q.indent == p.indent) {
                        if (q.style != RichBlockStyle.NUMBERED_ITEM) break
                        n++
                    }
                    i--
                }
                ListMarker.Number(numberLabel(n, p.indent))
            }
            else -> ListMarker.None
        }
    }

    private fun numberLabel(n: Int, level: Int): String = when (level % 3) {
        1 -> letters(n)
        2 -> roman(n)
        else -> n.toString()
    } + "."

    private fun letters(n: Int): String {
        var k = n
        val sb = StringBuilder()
        while (k > 0) {
            k--
            sb.append('a' + k % 26)
            k /= 26
        }
        return sb.reverse().toString()
    }

    private fun roman(n: Int): String {
        if (n <= 0 || n >= 4000) return n.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")
        var k = n
        val sb = StringBuilder()
        for (i in values.indices) while (k >= values[i]) { sb.append(symbols[i]); k -= values[i] }
        return sb.toString()
    }

    // The edit itself: the text replaced, the character styles moved along with it (the inserted
    // text taking [inserted]), and the paragraphs it joins or splits given their looks.
    private fun apply(old: NoteText, d: TextDiff, inserted: Set<CharStyle>): NoteText {
        val newText = old.text.substring(0, d.deleteStart) + d.insertText + old.text.substring(d.deleteEnd)
        val insLen = d.insertText.length
        val styles = CharStyle.entries.associateWith { st ->
            val moved = reindexStyleRanges(old.ranges(st), d.deleteStart, d.deleteEnd, insLen, false)
            if (st in inserted && insLen > 0) mergeStyleRanges(moved + StyleRange(d.deleteStart, d.deleteStart + insLen)) else moved
        }.filterValues { it.isNotEmpty() }
        val scales = reindexScaleRanges(old.fontScale, d.deleteStart, d.deleteEnd, insLen, true)

        val a = old.paraAt(d.deleteStart)
        val b = old.paraAt(d.deleteEnd)
        // Lines joined keep the first one's look, unless the first went whole from its start.
        val joined = if (b > a && d.deleteStart == old.paraStart(a)) old.paras[b] else old.paras[a]
        val breaks = d.insertText.count { it == '\n' }
        val paras = ArrayList<ParaStyle>(old.paras.size + breaks)
        paras.addAll(old.paras.subList(0, a))
        if (breaks == 0) {
            paras.add(joined)
        } else {
            val opensAbove = d.deleteStart == d.deleteEnd && d.deleteStart == old.paraStart(a) &&
                old.paraEnd(a) > old.paraStart(a) && d.insertText.all { it == '\n' }
            if (opensAbove) {
                // Lines opened above a line: it keeps its look, and the new ones take it too (a
                // heading's become plain text).
                val above = if (joined.isHeading) ParaStyle(align = joined.align, indent = joined.indent) else joined.copy(checked = false)
                repeat(breaks) { paras.add(above) }
                paras.add(joined)
            } else {
                paras.add(joined)
                val next = when {
                    joined.isList -> joined.copy(checked = false)
                    joined.isHeading -> ParaStyle(align = joined.align, indent = joined.indent)
                    else -> joined
                }
                repeat(breaks) { paras.add(next) }
            }
        }
        paras.addAll(old.paras.subList(b + 1, old.paras.size))
        return NoteText(newText, paras, styles, scales)
    }

    // What the text box changed: anchored at the cursor when it was typing or deleting there (so
    // a repeated letter or line break is placed where it really went), otherwise the stretch
    // between what's the same at both ends.
    internal fun diffAtCursor(old: String, new: String, cursor: Int?): TextDiff {
        if (cursor != null && cursor in 0..old.length) {
            val grow = new.length - old.length
            if (grow > 0 && new.regionMatches(0, old, 0, cursor) &&
                new.regionMatches(cursor + grow, old, cursor, old.length - cursor)
            ) {
                return TextDiff(cursor, cursor, new.substring(cursor, cursor + grow))
            }
            if (grow < 0) {
                val k = -grow
                if (cursor - k >= 0 && new.regionMatches(0, old, 0, cursor - k) &&
                    new.regionMatches(cursor - k, old, cursor, old.length - cursor)
                ) {
                    return TextDiff(cursor - k, cursor, "")
                }
                if (cursor + k <= old.length && new.regionMatches(0, old, 0, cursor) &&
                    new.regionMatches(cursor, old, cursor + k, old.length - cursor - k)
                ) {
                    return TextDiff(cursor, cursor + k, "")
                }
            }
        }
        return computeTextDiff(old, new)
    }

    private fun removeRange(ranges: List<StyleRange>, s: Int, e: Int): List<StyleRange> = mergeStyleRanges(ranges.flatMap { r ->
        if (r.end <= s || r.start >= e) listOf(r)
        else listOfNotNull(
            if (r.start < s) StyleRange(r.start, s) else null,
            if (r.end > e) StyleRange(e, r.end) else null
        )
    })

    private fun NoteText.withPara(i: Int, p: ParaStyle): NoteText = copy(paras = paras.toMutableList().also { it[i] = p })

    private fun NoteText.withStyle(style: CharStyle, ranges: List<StyleRange>): NoteText =
        copy(styles = (styles - style).let { if (ranges.isEmpty()) it else it + (style to ranges) })

    private fun NoteText.mapParas(start: Int, end: Int, f: (ParaStyle) -> ParaStyle): NoteText {
        val range = parasIn(start, end)
        return copy(paras = paras.mapIndexed { i, p -> if (i in range) f(p) else p })
    }
}

/**
 * The note editor's undo and redo: the note and selection before each change. Typing (or
 * deleting) without a pause of more than [GROUP_MS] is one step, as in other editors; a new
 * line, a paste or a formatting change is a step of its own.
 */
class EditHistory(private val limit: Int = 200) {
    data class State(val note: NoteText, val selStart: Int, val selEnd: Int)

    enum class Kind { TYPING, DELETING, OTHER }

    private val undos = ArrayDeque<State>()
    private val redos = ArrayDeque<State>()
    private var lastKind: Kind? = null
    private var lastAt = 0L

    val canUndo: Boolean get() = undos.isNotEmpty()
    val canRedo: Boolean get() = redos.isNotEmpty()

    /** Before a change from [before]: kept as a step back, unless it carries on the typing or deleting just done. */
    fun record(before: State, kind: Kind, now: Long) {
        val carriesOn = kind != Kind.OTHER && kind == lastKind && now - lastAt < GROUP_MS
        if (!carriesOn) {
            undos.addLast(before)
            while (undos.size > limit) undos.removeFirst()
        }
        redos.clear()
        lastKind = kind
        lastAt = now
    }

    fun undo(current: State): State? {
        val back = undos.removeLastOrNull() ?: return null
        redos.addLast(current)
        lastKind = null
        return back
    }

    fun redo(current: State): State? {
        val forward = redos.removeLastOrNull() ?: return null
        undos.addLast(current)
        lastKind = null
        return forward
    }

    companion object {
        const val GROUP_MS = 1500L

        /** What kind of change the text box made, for grouping. */
        fun kindOf(old: String, new: String): Kind {
            val d = computeTextDiff(old, new)
            return when {
                d.insertText.isNotEmpty() && d.deleteEnd == d.deleteStart && '\n' !in d.insertText && d.insertText.length <= 2 -> Kind.TYPING
                d.insertText.isEmpty() && d.deleteEnd - d.deleteStart <= 1 -> Kind.DELETING
                else -> Kind.OTHER
            }
        }
    }
}
