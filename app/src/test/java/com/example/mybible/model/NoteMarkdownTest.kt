package com.example.mybible.model

import com.example.mybible.data.findVerseMentions
import com.example.mybible.model.NoteMarkdown.Bullet
import com.example.mybible.model.NoteMarkdown.Heading
import com.example.mybible.model.NoteMarkdown.Inline
import com.example.mybible.model.NoteMarkdown.Paragraph
import com.example.mybible.model.NoteMarkdown.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Markdown in a note's plain text, as the note page shows it (see NoteMarkdown). */
class NoteMarkdownTest {

    private fun Inline.bolds() = bold.map { text.substring(it.start, it.end) }
    private fun Inline.italics() = italic.map { text.substring(it.start, it.end) }

    private val sample = """
        # Giving God Control
        **My old misconception:** I thought it meant my soul was *useless*.

        > "This is the way, walk ye in it." (Isaiah 30:21)
        > "If we live in the Spirit, let us also walk in the Spirit." (Galatians 5:25)

        - **Mechanical love** cares only about the destination.
        - **Pure love** enjoys His company, as in 1 John 4:19.
    """.trimIndent()

    @Test
    fun theSampleNote() {
        val blocks = NoteMarkdown.blocks(sample)
        assertEquals(5, blocks.size)

        val heading = blocks[0] as Heading
        assertEquals(1, heading.level)
        assertEquals("Giving God Control", heading.text.text)

        // Straight after the heading, no blank line: no space above it.
        val paragraph = blocks[1] as Paragraph
        assertEquals("My old misconception: I thought it meant my soul was useless.", paragraph.text.text)
        assertEquals(listOf("My old misconception:"), paragraph.text.bolds())
        assertEquals(listOf("useless"), paragraph.text.italics())
        assertFalse(paragraph.spaced)

        // Two quote lines, one quote, each on its line.
        val quote = blocks[2] as Quote
        assertEquals(
            "\"This is the way, walk ye in it.\" (Isaiah 30:21)\n\"If we live in the Spirit, let us also walk in the Spirit.\" (Galatians 5:25)",
            quote.text.text
        )
        assertTrue(quote.spaced)

        val first = blocks[3] as Bullet
        assertEquals("Mechanical love cares only about the destination.", first.text.text)
        assertEquals(listOf("Mechanical love"), first.text.bolds())
        assertTrue(first.spaced)
        // The next item, right under it: no space between.
        val second = blocks[4] as Bullet
        assertEquals("Pure love enjoys His company, as in 1 John 4:19.", second.text.text)
        assertFalse(second.spaced)
    }

    @Test
    fun verseReferencesAreFoundInTheTextAsItShows() {
        // The links go on the text without its marks, so where each is matches what shows.
        val blocks = NoteMarkdown.blocks(sample)
        fun mentions(block: NoteMarkdown.Block) = findVerseMentions(block.text.text).map { block.text.text.substring(it.range) }
        assertEquals(listOf("Isaiah 30:21", "Galatians 5:25"), mentions(blocks[2]))
        assertEquals(listOf("1 John 4:19"), mentions(blocks[4]))
        val bold = NoteMarkdown.inline("See **John 10:29** and *Proverbs 3:5–6*.")
        assertEquals("See John 10:29 and Proverbs 3:5–6.", bold.text)
        val found = findVerseMentions(bold.text)
        // (A reference's range takes in the space before it, as the note page has always linked it.)
        assertEquals(listOf("John 10:29", "Proverbs 3:5–6"), found.map { bold.text.substring(it.range).trim() })
        assertEquals("John", found[0].book)
        assertEquals(10, found[0].chapter)
        assertEquals(29, found[0].verse)
    }

    @Test
    fun headingsQuotesAndLists() {
        val blocks = NoteMarkdown.blocks("# One\n## Two\n### Three\n#### Four ####\n#faith\n- dash\n* star\n• dot")
        assertEquals(listOf(1, 2, 3, 3), blocks.filterIsInstance<Heading>().map { it.level })
        assertEquals("Four", (blocks[3] as Heading).text.text)
        // "#faith", a tag, isn't a heading: plain text.
        assertEquals("#faith", (blocks[4] as Paragraph).text.text)
        assertEquals(listOf("dash", "star", "dot"), blocks.filterIsInstance<Bullet>().map { it.text.text })
        // Without its space, ">" still starts a quote; lines that follow, a paragraph after it.
        val quoted = NoteMarkdown.blocks(">one\n> two\nafter")
        assertEquals("one\ntwo", (quoted[0] as Quote).text.text)
        assertEquals("after", (quoted[1] as Paragraph).text.text)
    }

    @Test
    fun linesTogetherAreOneParagraphAndBlankLinesSeparate() {
        val blocks = NoteMarkdown.blocks("first line\nsecond line\n\n\nnext paragraph\r\nits second line")
        assertEquals(2, blocks.size)
        assertEquals("first line\nsecond line", blocks[0].text.text)
        assertEquals("next paragraph\nits second line", blocks[1].text.text)
        assertTrue(blocks[1].spaced)
    }

    @Test
    fun notMarkdownStaysAsWritten() {
        // Never closed.
        assertEquals("**not bold", NoteMarkdown.inline("**not bold").text)
        assertEquals("*not italic", NoteMarkdown.inline("*not italic").text)
        assertEquals("a ** b", NoteMarkdown.inline("a ** b").text)
        // Asterisks in ordinary text.
        val sums = NoteMarkdown.inline("5 * 3 = 15, and 2 * 4 * 1 = 8")
        assertEquals("5 * 3 = 15, and 2 * 4 * 1 = 8", sums.text)
        assertTrue(sums.italic.isEmpty())
        assertEquals("Matthew 5:3* (a note)", NoteMarkdown.inline("Matthew 5:3* (a note)").text)
        assertEquals("****", NoteMarkdown.inline("****").text)
        assertEquals("*", NoteMarkdown.inline("*").text)
        // Written as one: an asterisk.
        assertEquals("*not italic*", NoteMarkdown.inline("\\*not italic\\*").text)
        // A heading mark alone is nothing; a list mark alone, an empty item.
        assertTrue(NoteMarkdown.blocks("#").single() is Paragraph)
        assertTrue(NoteMarkdown.blocks("# ").isEmpty())
    }

    @Test
    fun boldAndItalicTogether() {
        val both = NoteMarkdown.inline("***both***")
        assertEquals("both", both.text)
        assertEquals(listOf("both"), both.bolds())
        assertEquals(listOf("both"), both.italics())
        val nested = NoteMarkdown.inline("**bold *and italic***")
        assertEquals("bold and italic", nested.text)
        assertEquals(listOf("bold and italic"), nested.bolds())
        assertEquals(listOf("and italic"), nested.italics())
        val inside = NoteMarkdown.inline("*italic **bold** more*")
        assertEquals("italic bold more", inside.text)
        assertEquals(listOf("bold"), inside.bolds())
        assertEquals(listOf("italic bold more"), inside.italics())
        // Not across a line break: a mark left open in one line leaves the next as it is.
        assertEquals("**one\ntwo**", NoteMarkdown.inline("**one\ntwo**").text)
        val open = NoteMarkdown.inline("*this too\n5 * 3 = 15, Matthew 5:3*")
        assertEquals("*this too\n5 * 3 = 15, Matthew 5:3*", open.text)
        assertTrue(open.italic.isEmpty())
        // Each line its own: bold in the first, italic in the second.
        val lines = NoteMarkdown.inline("**one**\n*two*")
        assertEquals(listOf("one"), lines.bolds())
        assertEquals(listOf("two"), lines.italics())
    }

    @Test
    fun emptyAndQuoteOnlyNotes() {
        assertTrue(NoteMarkdown.blocks("").isEmpty())
        assertTrue(NoteMarkdown.blocks("\n\n  \n").isEmpty())
        val quote = NoteMarkdown.blocks("> only a quote")
        assertEquals(1, quote.size)
        assertEquals("only a quote", (quote.single() as Quote).text.text)
        assertFalse(quote.single().spaced)
        assertEquals("", (NoteMarkdown.blocks(">").single() as Quote).text.text)
    }

    @Test
    fun whatHasMarkdown() {
        assertTrue(NoteMarkdown.hasMarkdown(sample))
        assertTrue(NoteMarkdown.hasMarkdown("a **bold** word"))
        assertTrue(NoteMarkdown.hasMarkdown("- an item"))
        assertFalse(NoteMarkdown.hasMarkdown("An ordinary note about John 3:16."))
        assertFalse(NoteMarkdown.hasMarkdown("#faith #hope"))
        assertFalse(NoteMarkdown.hasMarkdown("5 * 3 = 15"))
        assertFalse(NoteMarkdown.hasMarkdown(""))
    }
}
