package com.example.mybible.ui.components

import com.example.mybible.model.NoteReference
import com.example.mybible.model.RefRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The passages a note's verse section shows, and how each is named on its row. */
class NoteVerseSectionTest {

    @Test
    fun theVersesTheNoteIsOnNotThoseNamedInItsText() {
        val refs = listOf(
            NoteReference("Proverbs", 3, 6, "In all thy ways"),
            NoteReference("John", 10, 29, "My Father, which gave them me"),
            NoteReference("Proverbs", 3, 5, "Trust in the LORD"),
            // Named in the text: a link there, not a row.
            NoteReference("1 John", 4, 19, named = true),
            NoteReference("John", 15, 0)
        )
        val passages = notePassages(refs)
        // In Bible order; side by side, one passage; a whole chapter on its own.
        assertEquals(listOf("Proverbs 3:5–6", "John 10:29", "John 15"), passages.map { it.first.label })
        assertEquals(listOf(5, 6), passages[0].second.map { it.verse })
        assertEquals("My Father, which gave them me", passages[1].second.single().verseText)
    }

    @Test
    fun aNoteOnNoVerseHasNone() {
        assertTrue(notePassages(emptyList()).isEmpty())
        assertTrue(notePassages(listOf(NoteReference("John", 3, 16, named = true))).isEmpty())
    }

    @Test
    fun howMuchEachIs() {
        assertNull(passageSize(RefRange("John", 10, 29, 29)))
        assertEquals("2 verses", passageSize(RefRange("Proverbs", 3, 5, 6)))
        assertEquals("whole chapter", passageSize(RefRange("John", 15, 0, 0)))
    }
}
