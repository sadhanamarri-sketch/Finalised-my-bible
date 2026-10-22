package com.example.mybible.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NotesListTest {
    private val books = listOf("Genesis", "Psalms", "Matthew", "John", "1 John")
    private val order: (String) -> Int = { b -> books.indexOf(b).let { if (it < 0) books.size else it } }
    private fun ref(book: String, chapter: Int, verse: Int, text: String = "") = NoteReference(book, chapter, verse, text)
    private fun note(
        id: Long,
        title: String = "",
        text: String = "",
        refs: List<NoteReference> = emptyList(),
        tags: List<String> = emptyList(),
        date: String = "2026-10-20",
        created: Long = id,
        updated: Long = id,
        pinned: Boolean = false
    ) = NoteItem(id = id, title = title, text = text, refs = refs, tags = tags, noteDate = date, createdAt = created, updatedAt = updated, pinned = pinned)

    @Test
    fun versesSideBySideShowAsOnePassageInBibleOrder() {
        val refs = listOf(ref("John", 15, 3), ref("Genesis", 1, 1), ref("John", 15, 1), ref("John", 15, 2), ref("John", 15, 5), ref("John", 16, 1))
        assertEquals(listOf("Genesis 1:1", "John 15:1\u20133", "John 15:5", "John 16:1"), groupRefs(refs, order).map { it.label })
        assertTrue(groupRefs(refs, order)[1].covers(ref("John", 15, 2)))
        assertFalse(groupRefs(refs, order)[1].covers(ref("John", 15, 5)))
    }

    @Test
    fun searchFindsVersesAndTagsAsWellAsWords() {
        val n = note(1, title = "Abiding", text = "Stay close", refs = listOf(ref("John", 15, 5)), tags = listOf("Prayer"))
        assertTrue(NotesFinder.matchesText(n, "abid"))
        assertTrue(NotesFinder.matchesText(n, "close"))
        assertTrue(NotesFinder.matchesText(n, "pray"))
        assertTrue(NotesFinder.matchesText(n, "John"))
        assertTrue(NotesFinder.matchesText(n, "John 15"))
        assertTrue(NotesFinder.matchesText(n, "john 15:5"))
        assertTrue(NotesFinder.matchesText(n, "John 15:1-8"))
        assertTrue(NotesFinder.matchesText(n, "Jn 15"))
        assertFalse(NotesFinder.matchesText(n, "John 1"))
        assertFalse(NotesFinder.matchesText(n, "John 15:6"))
        assertFalse(NotesFinder.matchesText(n, "1 John 15"))
        assertTrue(NotesFinder.matchesText(note(2, refs = listOf(ref("1 John", 3, 16))), "1 John 3"))
        assertTrue(NotesFinder.matchesText(n, "  "))
    }

    @Test
    fun tagsMatchAnyOrAllAndDatesTheirWeekMonthOrRange() {
        val n = note(1, tags = listOf("Prayer", "Sermons"), date = "2026-10-20")
        assertTrue(NotesFinder.matchesTags(n, listOf("prayer", "Faith"), all = false))
        assertFalse(NotesFinder.matchesTags(n, listOf("prayer", "Faith"), all = true))
        assertTrue(NotesFinder.matchesTags(n, listOf("prayer", "sermons"), all = true))
        val thursday = LocalDate.parse("2026-10-22")
        assertTrue(NotesFinder.matchesDates(n, NoteDates.ThisWeek, thursday))  // Monday 19 Oct to Sunday 25 Oct
        assertFalse(NotesFinder.matchesDates(note(2, date = "2026-10-18"), NoteDates.ThisWeek, thursday))
        assertTrue(NotesFinder.matchesDates(note(3, date = "2026-10-01"), NoteDates.ThisMonth, thursday))
        assertFalse(NotesFinder.matchesDates(note(4, date = "2025-10-01"), NoteDates.ThisMonth, thursday))
        val range = NoteDates.Between(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-20"))
        assertTrue(NotesFinder.matchesDates(n, range, thursday))
        assertFalse(NotesFinder.matchesDates(note(5, date = "2026-10-21"), range, thursday))
    }

    @Test
    fun pinnedComeFirstThenTheChosenOrder() {
        val a = note(1, created = 100, updated = 900)
        val b = note(2, created = 300, updated = 200)
        val c = note(3, created = 200, updated = 100, pinned = true)
        fun ids(sort: NoteSort) = NotesFinder.rows(listOf(a, b, c), NotesQuery(sort = sort), LocalDate.parse("2026-10-22"), order)
            .map { (it as NotesRow.Note).note.id }
        assertEquals(listOf(3L, 2L, 1L), ids(NoteSort.NEWEST))
        assertEquals(listOf(3L, 1L, 2L), ids(NoteSort.EDITED))
    }

    @Test
    fun bibleOrderGoesUnderEachBookWithNotesWithoutVersesLast() {
        val notes = listOf(
            note(1, refs = listOf(ref("John", 15, 5))),
            note(2),
            note(3, refs = listOf(ref("Genesis", 1, 1))),
            note(4, refs = listOf(ref("John", 3, 16), ref("Psalms", 23, 1)))
        )
        val rows = NotesFinder.rows(notes, NotesQuery(sort = NoteSort.BIBLE), LocalDate.parse("2026-10-22"), order)
        assertEquals(
            listOf("Genesis 1", "#3", "Psalms 1", "#4", "John 1", "#1", "${NotesFinder.NO_VERSES} 1", "#2"),
            rows.map { r -> if (r is NotesRow.Book) "${r.name} ${r.count}" else "#${(r as NotesRow.Note).note.id}" }
        )
    }

    @Test
    fun aNoteSharesAsTextWithItsVersesWords() {
        val n = note(
            1, title = "Abiding", text = "Stay close.", tags = listOf("Prayer"), date = "2026-10-18",
            refs = listOf(ref("John", 15, 4, "Abide in me."), ref("John", 15, 5, "I am the vine."))
        )
        assertEquals(
            "Abiding\n18 Oct 2026 \u00b7 Prayer\n\nJohn 15:4\u20135 \u2014 Abide in me. I am the vine.\n\nStay close.",
            NoteSharing.asText(n, order)
        )
        val all = NoteSharing.allAsText(listOf(n, note(2, text = "Second")), LocalDate.parse("2026-10-22"), order)
        assertTrue(all.startsWith("Notes\n2 notes, 22 Oct 2026\n"))
        assertTrue(all.trimEnd().endsWith("Second"))
    }

    @Test
    fun aQuoteGoesOnItsOwnLineAfterTheCursorsWithALineToGoOnIn() {
        val n = NoteText("Thoughts\nMore", listOf(ParaStyle(), ParaStyle()))
        val e = NoteEditing.insertQuote(n, 3, "\u201cI am the vine\u201d \u2014 John 15:5")
        assertEquals("Thoughts\n\u201cI am the vine\u201d \u2014 John 15:5\n\nMore", e.note.text)
        assertEquals(
            listOf(RichBlockStyle.PARAGRAPH, RichBlockStyle.BLOCKQUOTE, RichBlockStyle.PARAGRAPH, RichBlockStyle.PARAGRAPH),
            e.note.paras.map { it.style }
        )
        assertEquals(e.note.paraStart(2), e.selStart)
        // On an empty line, the quote goes in it.
        val empty = NoteText("", listOf(ParaStyle(RichBlockStyle.BULLET_ITEM)))
        val q = NoteEditing.insertQuote(empty, 0, "Abide")
        assertEquals("Abide\n", q.note.text)
        assertEquals(listOf(RichBlockStyle.BLOCKQUOTE, RichBlockStyle.PARAGRAPH), q.note.paras.map { it.style })
    }
}
