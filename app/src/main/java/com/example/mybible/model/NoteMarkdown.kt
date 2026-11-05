package com.example.mybible.model

/**
 * Markdown in a note's plain text, as the note page shows it: "# " and "## " headings, **bold** and
 * *italic*, "> " quote lines (one quote, a run of them), "- ", "* " and "• " list items, and lines
 * that are none of these, a run of them, a paragraph that keeps its line breaks. A blank line ends
 * a block. Only the note page reads it: the editor keeps the text as it was written.
 *
 * What isn't markdown stays as written: a "*" or "**" not closed on its line ("5 * 3", "Matthew
 * 5:3*"), a heading mark without its space ("#faith", a tag), and "\*" (an asterisk meant as one).
 * Plain Kotlin, so it's tested without a screen.
 */
object NoteMarkdown {

    /** A block's text without its markdown marks, and where it's bold and italic (end exclusive). */
    data class Inline(
        val text: String,
        val bold: List<StyleRange> = emptyList(),
        val italic: List<StyleRange> = emptyList()
    )

    sealed interface Block {
        val text: Inline

        /** A blank line comes before it: a space above it, where it isn't the first. */
        val spaced: Boolean
    }

    /** "# " is [level] 1, "## " 2, "### " and more 3. */
    data class Heading(val level: Int, override val text: Inline, override val spaced: Boolean = false) : Block

    /** Lines one after another, each kept on its own line. */
    data class Paragraph(override val text: Inline, override val spaced: Boolean = false) : Block

    /** "> " lines one after another, as one quote, each kept on its own line. */
    data class Quote(override val text: Inline, override val spaced: Boolean = false) : Block

    /** A "- ", "* " or "• " line. */
    data class Bullet(override val text: Inline, override val spaced: Boolean = false) : Block

    private val HEADING = Regex("^ {0,3}(#{1,6})[ \\t]+(.*?)[ \\t]*$")
    private val QUOTE = Regex("^ {0,3}>[ \\t]?(.*)$")
    private val BULLET = Regex("^ {0,3}[-*•][ \\t]+(.*)$")

    /** [source]'s blocks, in order; none for a blank or empty one. */
    fun blocks(source: String): List<Block> {
        val out = ArrayList<Block>()
        val lines = ArrayList<String>() // the paragraph or quote being gathered
        var gathering: Char? = null // 'p' a paragraph, 'q' a quote
        var gatheringSpaced = false
        var blankBefore = false

        fun flush() {
            val kind = gathering ?: return
            val text = inline(lines.joinToString("\n"))
            out += if (kind == 'q') Quote(text, gatheringSpaced) else Paragraph(text, gatheringSpaced)
            lines.clear()
            gathering = null
        }

        fun gather(kind: Char, line: String) {
            if (gathering != kind) {
                flush()
                gathering = kind
                gatheringSpaced = blankBefore && out.isNotEmpty()
            }
            lines += line
        }

        for (raw in source.split('\n')) {
            val line = raw.removeSuffix("\r")
            if (line.isBlank()) {
                flush()
                blankBefore = true
                continue
            }
            val spaced = blankBefore && (out.isNotEmpty() || gathering != null)
            HEADING.matchEntire(line)?.let { m ->
                flush()
                val level = minOf(m.groupValues[1].length, 3)
                // "## Title ##": closing marks aren't the title's. "# " alone is nothing.
                val title = m.groupValues[2].trimEnd('#').trimEnd()
                if (title.isNotEmpty()) {
                    out += Heading(level, inline(title), spaced)
                    blankBefore = false
                }
            } ?: QUOTE.matchEntire(line)?.let { m ->
                gather('q', m.groupValues[1])
                blankBefore = false
            } ?: BULLET.matchEntire(line)?.let { m ->
                flush()
                out += Bullet(inline(m.groupValues[1]), spaced)
                blankBefore = false
            } ?: run {
                gather('p', line)
                blankBefore = false
            }
        }
        flush()
        return out
    }

    /** Whether [source] has anything that [blocks] would show other than as written. */
    fun hasMarkdown(source: String): Boolean {
        for (raw in source.split('\n')) {
            val line = raw.removeSuffix("\r")
            if (HEADING.matches(line) || QUOTE.matches(line) || BULLET.matches(line)) return true
        }
        return inline(source).text != source
    }

    /** [source]'s text without its **bold** and *italic* marks, and where those are. */
    fun inline(source: String): Inline {
        val out = StringBuilder()
        val bold = ArrayList<StyleRange>()
        val italic = ArrayList<StyleRange>()
        parse(source, out, bold, italic)
        return Inline(out.toString(), bold, italic)
    }

    private fun parse(s: String, out: StringBuilder, bold: MutableList<StyleRange>, italic: MutableList<StyleRange>) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // "\*": an asterisk meant as one.
            if (c == '\\' && i + 1 < s.length && s[i + 1] == '*') {
                out.append('*')
                i += 2
                continue
            }
            if (c == '*') {
                var run = 0
                while (i + run < s.length && s[i + run] == '*') run++
                // One, two or three open italic, bold, or both; four or more are only asterisks.
                val close = if (run <= 3 && opens(s, i + run)) closer(s, i + run, run) else -1
                if (close >= 0) {
                    val from = out.length
                    parse(s.substring(i + run, close), out, bold, italic)
                    val to = out.length
                    if (to > from) {
                        if (run >= 2) bold += StyleRange(from, to)
                        if (run == 1 || run >= 3) italic += StyleRange(from, to)
                    }
                    i = close + run
                    continue
                }
                // Not opening anything: the whole run as written.
                repeat(run) { out.append('*') }
                i += run
                continue
            }
            out.append(c)
            i++
        }
    }

    // A mark opens when what follows it isn't a space.
    private fun opens(s: String, after: Int): Boolean = after < s.length && !s[after].isWhitespace()

    // Where the mark of [run] asterisks opened before [from] closes: the next run of as many, just
    // after something that isn't a space; for one, not a part of "**" (that's bold, inside it). On
    // the same line: an asterisk left open in one line doesn't turn the lines after it italic.
    private fun closer(s: String, from: Int, run: Int): Int {
        var j = from
        while (j < s.length) {
            if (s[j] == '\n') return -1
            if (s[j] == '\\' && j + 1 < s.length && s[j + 1] == '*') {
                j += 2
                continue
            }
            if (s[j] != '*') {
                j++
                continue
            }
            var n = 0
            while (j + n < s.length && s[j + n] == '*') n++
            val closes = j > from && !s[j - 1].isWhitespace()
            when {
                // "**bold *and italic***": the last two of three close the bold.
                closes && n == run -> return j
                closes && n > run && run >= 2 -> return j + (n - run)
                closes && run == 1 && n == 3 -> return j + 2
            }
            j += n
        }
        return -1
    }
}
