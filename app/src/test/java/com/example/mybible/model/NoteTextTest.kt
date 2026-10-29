package com.example.mybible.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextTest {
    private val bullet = ParaStyle(RichBlockStyle.BULLET_ITEM)
    private val plain = ParaStyle()

    private fun note(vararg lines: Pair<String, ParaStyle>, bold: List<StyleRange> = emptyList()) = NoteText(
        lines.joinToString("\n") { it.first },
        lines.map { it.second },
        if (bold.isEmpty()) emptyMap() else mapOf(CharStyle.BOLD to bold)
    )

    // The text box's report of [s] typed at [at] (a single character at a time is how a keyboard sends it).
    private fun type(n: NoteText, at: Int, s: String, typing: Set<CharStyle>? = null): Edited {
        val text = n.text.substring(0, at) + s + n.text.substring(at)
        return NoteEditing.edit(n, at, at, text, at + s.length, at + s.length, typing)
    }

    private fun backspace(n: NoteText, at: Int): Edited =
        NoteEditing.edit(n, at, at, n.text.removeRange(at - 1, at), at - 1, at - 1, null)

    private fun styles(n: NoteText) = n.paras.map { it.style }

    @Test
    fun aDocumentComesBackAsItWent() {
        val doc = NoteDocument(listOf(
            RichBlock("Title", RichBlockStyle.HEADING_1),
            RichBlock("Pray without ceasing", bold = listOf(StyleRange(0, 4)), highlight = listOf(StyleRange(5, 12))),
            RichBlock("Milk", RichBlockStyle.CHECK_ITEM, checked = true),
            RichBlock("Eggs", RichBlockStyle.CHECK_ITEM, indent = 1),
            RichBlock("Centred", align = RichAlign.CENTER, italic = listOf(StyleRange(0, 7)))
        ))
        val back = NoteText.from(doc).toDocument()
        assertEquals(doc, back)
    }

    @Test
    fun enterInAListItemAddsAnItemAndOnAnEmptyOneEndsTheList() {
        val n = note("Milk" to bullet)
        val e1 = type(n, 4, "\n")
        assertEquals(listOf(RichBlockStyle.BULLET_ITEM, RichBlockStyle.BULLET_ITEM), styles(e1.note))
        val e2 = type(e1.note, e1.selStart, "\n")
        assertTrue(e2.rewritten)
        assertEquals("Milk\n", e2.note.text)
        assertEquals(listOf(RichBlockStyle.BULLET_ITEM, RichBlockStyle.PARAGRAPH), styles(e2.note))
    }

    @Test
    fun enterOnAnEmptyNestedItemMovesItOutALevel() {
        val n = note("Milk" to bullet, "" to bullet.copy(indent = 1))
        val e = type(n, 5, "\n")
        assertEquals("Milk\n", e.note.text)
        assertEquals(listOf(0, 0), e.note.paras.map { it.indent })
        assertEquals(RichBlockStyle.BULLET_ITEM, e.note.paras[1].style)
    }

    @Test
    fun backspaceAtALinesStartMakesAListItemPlainThenMovesItBackThenJoins() {
        val n = note("One" to plain, "Two" to bullet.copy(indent = 1))
        val e1 = backspace(n, 4)
        assertEquals("One\nTwo", e1.note.text)
        assertEquals(ParaStyle(indent = 1), e1.note.paras[1])
        val e2 = backspace(e1.note, 4)
        assertEquals(ParaStyle(), e2.note.paras[1])
        val e3 = backspace(e2.note, 4)
        assertEquals("OneTwo", e3.note.text)
        assertEquals(1, e3.note.paras.size)
    }

    @Test
    fun backspaceHeldDownGoesThroughALinesStartAsTheKeyboardDidIt() {
        // Held, a checklist item's start is gone through at once, the lines joined: no line break put
        // back (which would restart the keyboard and end the key's repeat).
        val n = note("Milk" to plain, "Eggs" to ParaStyle(RichBlockStyle.CHECK_ITEM))
        val held = NoteEditing.edit(n, 5, 5, n.text.removeRange(4, 5), 4, 4, null, held = true)
        assertEquals("MilkEggs", held.note.text)
        assertEquals(listOf(plain), held.note.paras)
        assertFalse(held.rewritten)
        // A single press still makes the item plain first.
        val pressed = backspace(n, 5)
        assertEquals("Milk\nEggs", pressed.note.text)
        assertEquals(plain, pressed.note.paras[1])
        assertTrue(pressed.rewritten)
    }

    @Test
    fun backspaceAtTheVeryStartMakesTheFirstItemPlain() {
        val n = note("Milk" to bullet)
        assertEquals(ParaStyle(), NoteEditing.backspaceAtStart(n)!!.paras[0])
        assertNull(NoteEditing.backspaceAtStart(note("Milk" to plain)))
    }

    @Test
    fun shortcutsAtTheStartOfAPlainLineMakeListsChecklistsQuotesAndHeadings() {
        fun after(prefix: String): Edited {
            var e = Edited(NoteText.Empty, 0, 0)
            for (c in prefix) e = type(e.note, e.selStart, c.toString())
            return type(e.note, e.selStart, " ")
        }
        assertEquals(RichBlockStyle.BULLET_ITEM, after("-").note.paras[0].style)
        assertEquals(RichBlockStyle.NUMBERED_ITEM, after("1.").note.paras[0].style)
        assertEquals(RichBlockStyle.CHECK_ITEM, after("[]").note.paras[0].style)
        assertTrue(after("[x]").note.paras[0].checked)
        assertEquals(RichBlockStyle.BLOCKQUOTE, after(">").note.paras[0].style)
        assertEquals(RichBlockStyle.HEADING_2, after("##").note.paras[0].style)
        val e = after("-")
        assertEquals("", e.note.text)
        assertEquals(0, e.selStart)
        // Inside a line, or in a line that's already a list item, it's just text.
        assertEquals("a - ", type(note("a -" to plain), 3, " ").note.text)
        assertEquals("- ", type(note("-" to bullet), 1, " ").note.text)
    }

    @Test
    fun typingCarriesOnTheStyleBeforeItButNotTheStyleAfterIt() {
        // "Grace" bold, then " and peace".
        val n = note("Grace and peace" to plain, bold = listOf(StyleRange(0, 5)))
        val after = type(n, 5, "s")
        assertTrue(NoteEditing.hasStyle(after.note, 5, 6, CharStyle.BOLD))
        // Just before bold text, mid-line: not bold.
        val m = note("all Grace" to plain, bold = listOf(StyleRange(4, 9)))
        val before = type(m, 4, "x")
        assertFalse(NoteEditing.hasStyle(before.note, 4, 5, CharStyle.BOLD))
        assertTrue(NoteEditing.hasStyle(before.note, 5, 10, CharStyle.BOLD))
        // At a line's start, the text after it.
        val start = type(note("Grace" to plain, bold = listOf(StyleRange(0, 5))), 0, "x")
        assertTrue(NoteEditing.hasStyle(start.note, 0, 1, CharStyle.BOLD))
    }

    @Test
    fun typingStylesApplyToWhatsTypedUntilTheCursorMoves() {
        val n = note("Amen" to plain)
        val e = type(n, 4, "!", typing = setOf(CharStyle.BOLD, CharStyle.ITALIC))
        assertTrue(NoteEditing.hasStyle(e.note, 4, 5, CharStyle.BOLD))
        assertTrue(NoteEditing.hasStyle(e.note, 4, 5, CharStyle.ITALIC))
        assertEquals(setOf(CharStyle.BOLD, CharStyle.ITALIC), e.typing)
        val moved = NoteEditing.edit(e.note, 5, 5, e.note.text, 2, 2, e.typing)
        assertNull(moved.typing)
        // Typing with everything turned off in the middle of bold text: not bold.
        val bold = note("Grace" to plain, bold = listOf(StyleRange(0, 5)))
        val off = type(bold, 2, "x", typing = emptySet())
        assertFalse(NoteEditing.hasStyle(off.note, 2, 3, CharStyle.BOLD))
        assertTrue(NoteEditing.hasStyle(off.note, 3, 6, CharStyle.BOLD))
    }

    @Test
    fun anAutocorrectionKeepsTheLookOfTheWordItReplaces() {
        val n = note("say teh word" to plain, bold = listOf(StyleRange(4, 7)))
        val e = NoteEditing.edit(n, 7, 7, "say the word", 7, 7, null)
        assertEquals(listOf(StyleRange(4, 7)), e.note.ranges(CharStyle.BOLD))
    }

    @Test
    fun enterAtAHeadingsStartOpensAPlainLineAboveIt() {
        val n = note("Psalm 23" to ParaStyle(RichBlockStyle.HEADING_1))
        val e = type(n, 0, "\n")
        assertEquals("\nPsalm 23", e.note.text)
        assertEquals(listOf(RichBlockStyle.PARAGRAPH, RichBlockStyle.HEADING_1), styles(e.note))
        // Enter at its end: plain text after it.
        val f = type(n, 8, "\n")
        assertEquals(listOf(RichBlockStyle.HEADING_1, RichBlockStyle.PARAGRAPH), styles(f.note))
    }

    @Test
    fun aTickedItemSplitGivesAnUntickedOne() {
        val n = note("Milk" to ParaStyle(RichBlockStyle.CHECK_ITEM, checked = true))
        val e = type(n, 4, "\n")
        assertEquals(listOf(true, false), e.note.paras.map { it.checked })
    }

    @Test
    fun numbersCountAlongTheListByDepthAndStartAgainAfterAPlainLine() {
        val num = ParaStyle(RichBlockStyle.NUMBERED_ITEM)
        val n = note(
            "a" to num, "b" to num.copy(indent = 1), "c" to num.copy(indent = 1), "d" to num,
            "e" to num.copy(indent = 2), "x" to plain, "f" to num
        )
        val labels = n.paras.indices.map { (NoteEditing.marker(n, it) as? ListMarker.Number)?.label }
        assertEquals(listOf("1.", "a.", "b.", "2.", "i.", null, "1."), labels)
    }

    @Test
    fun boldOverTwoLinesTurnsOnForBothAndOffAgain() {
        val n = note("one" to plain, "two" to plain)
        val on = NoteEditing.toggleStyle(n, 0, 7, CharStyle.BOLD)
        assertTrue(NoteEditing.hasStyle(on, 0, 7, CharStyle.BOLD))
        assertEquals(listOf(StyleRange(0, 3)), on.toDocument().blocks[0].bold)
        assertEquals(listOf(StyleRange(0, 3)), on.toDocument().blocks[1].bold)
        val off = NoteEditing.toggleStyle(on, 0, 7, CharStyle.BOLD)
        assertTrue(off.ranges(CharStyle.BOLD).isEmpty())
    }

    @Test
    fun listsTabAndAlignmentApplyToEveryLineSelected() {
        val n = note("one" to plain, "two" to plain, "three" to plain)
        val listed = NoteEditing.toggleParaStyle(n, 1, 5, RichBlockStyle.BULLET_ITEM)
        assertEquals(listOf(RichBlockStyle.BULLET_ITEM, RichBlockStyle.BULLET_ITEM, RichBlockStyle.PARAGRAPH), styles(listed))
        assertEquals(styles(n), styles(NoteEditing.toggleParaStyle(listed, 1, 5, RichBlockStyle.BULLET_ITEM)))
        var deep = listed
        repeat(6) { deep = NoteEditing.indent(deep, 0, 0, 1) }
        assertEquals(MAX_INDENT, deep.paras[0].indent)
        assertEquals(0, NoteEditing.indent(listed, 0, 0, -1).paras[0].indent)
        assertEquals(RichAlign.CENTER, NoteEditing.setAlign(n, 0, 9, RichAlign.CENTER).paras[2].align)
    }

    @Test
    fun clearingFormattingTakesOffStylesAndHeadings() {
        val n = note("Title" to ParaStyle(RichBlockStyle.HEADING_2), "body" to plain, bold = listOf(StyleRange(0, 10)))
        val cleared = NoteEditing.clearFormatting(n, 0, 8)
        assertEquals(RichBlockStyle.PARAGRAPH, cleared.paras[0].style)
        assertEquals(listOf(StyleRange(8, 10)), cleared.ranges(CharStyle.BOLD))
    }

    @Test
    fun deletingWholeLinesFromTheirStartKeepsTheLookOfTheLineLeft() {
        val n = note("Gone" to bullet, "Kept" to ParaStyle(RichBlockStyle.HEADING_1))
        val e = NoteEditing.edit(n, 0, 5, "Kept", 0, 0, null)
        assertEquals(listOf(RichBlockStyle.HEADING_1), styles(e.note))
        // Joined by deleting across a line break from inside the first: the first's look.
        val f = NoteEditing.edit(n, 2, 6, "Goept", 2, 2, null)
        assertEquals(listOf(RichBlockStyle.BULLET_ITEM), styles(f.note))
    }

    @Test
    fun aRepeatedLineBreakGoesWhereTheCursorWas() {
        // Enter on the empty line between "a" and "b": the new line is that one's, not "b"'s.
        val n = note("a" to plain, "" to bullet, "b" to plain)
        val e = type(n, 2, "\n")
        assertTrue(e.rewritten)
        assertEquals(listOf(RichBlockStyle.PARAGRAPH, RichBlockStyle.PARAGRAPH, RichBlockStyle.PARAGRAPH), styles(e.note))
    }

    @Test
    fun plainTextShowsListMarks() {
        val n = note(
            "Shopping" to plain, "Milk" to ParaStyle(RichBlockStyle.CHECK_ITEM, checked = true),
            "Eggs" to ParaStyle(RichBlockStyle.CHECK_ITEM), "One" to ParaStyle(RichBlockStyle.NUMBERED_ITEM),
            "Sub" to bullet.copy(indent = 1)
        )
        assertEquals("Shopping\n☑ Milk\n☐ Eggs\n1. One\n  • Sub", n.plainText())
    }

    @Test
    fun typingIsOneStepBackUntilAPauseOrANewLine() {
        val h = EditHistory()
        val s0 = EditHistory.State(NoteText.plain(""), 0, 0)
        val s1 = EditHistory.State(NoteText.plain("a"), 1, 1)
        val s2 = EditHistory.State(NoteText.plain("ab"), 2, 2)
        h.record(s0, EditHistory.Kind.TYPING, 1000)
        h.record(s1, EditHistory.Kind.TYPING, 1500)
        val s3 = EditHistory.State(NoteText.plain("abc"), 3, 3)
        assertEquals(s0, h.undo(s2))
        assertFalse(h.canUndo)
        assertEquals(s2, h.redo(s0))
        h.record(s2, EditHistory.Kind.TYPING, 9000)
        assertEquals(s2, h.undo(s3))
        assertEquals(EditHistory.Kind.OTHER, EditHistory.kindOf("ab", "a\nb"))
        assertEquals(EditHistory.Kind.TYPING, EditHistory.kindOf("ab", "abc"))
        assertEquals(EditHistory.Kind.DELETING, EditHistory.kindOf("abc", "ab"))
    }
}
