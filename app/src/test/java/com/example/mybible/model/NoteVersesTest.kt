package com.example.mybible.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backlog 49: the verses named in a note are its verses too, and a whole chapter can be one. */
class NoteVersesTest {
    private val books = listOf("Genesis", "Psalms", "John", "Romans")
    private val order: (String) -> Int = { b -> books.indexOf(b).let { if (it < 0) books.size else it } }
    private val counts = mapOf(
        "John" to listOf(51, 25, 36),
        "Romans" to List(16) { if (it == 7) 39 else 30 },
        "Psalms" to List(150) { if (it == 118) 176 else 20 }
    )
    private val unknown: (String, Int) -> Int? = { _, _ -> null }
    private fun places(refs: List<NoteReference>) = refs.map { "${it.book} ${it.chapter}:${it.verse}${if (it.named) " named" else ""}" }

    @Test
    fun aWholeChapterShowsAsTheChapterApartFromItsVerses() {
        val refs = listOf(NoteReference("John", 3, 16), NoteReference("John", 3, 0), NoteReference("John", 3, 1), NoteReference("John", 3, 2))
        val ranges = groupRefs(refs, order)
        assertEquals(listOf("John 3", "John 3:1–2", "John 3:16"), ranges.map { it.label })
        assertTrue(ranges[0].wholeChapter)
        // × on "John 3" takes off the chapter, not verses added on their own.
        assertTrue(ranges[0].covers(NoteReference("John", 3, 0)))
        assertFalse(ranges[0].covers(NoteReference("John", 3, 1)))
    }

    @Test
    fun versesNamedInTheTitleAndTextAreFoundEachOnItsOwn() {
        val named = namedRefs("On John 3:16", "See Rom 8:28-30, and John 3:16 again; Jn 1:1 and Psalm 119:175-180.", chapterSizes(counts))
        assertEquals(
            listOf("John 3:16 named", "Romans 8:28 named", "Romans 8:29 named", "Romans 8:30 named", "John 1:1 named", "Psalms 119:175 named", "Psalms 119:176 named"),
            places(named)
        )
    }

    @Test
    fun versesThatArentThereAreLeftOut() {
        val sizes = chapterSizes(counts)
        assertEquals(emptyList<String>(), places(namedRefs("", "John 3:99, John 30:1, Hezekiah 3:1 and John 3:0", sizes)))
        assertEquals(36, sizes("John", 3))
        assertEquals(0, sizes("John", 30))
        // Before the Bible is in, any verse is taken, a passage only so long.
        assertEquals(null, chapterSizes(emptyMap())("John", 3))
        assertEquals(listOf("John 3:99 named"), places(namedRefs("", "John 3:99", unknown)))
        assertEquals(176, namedRefs("", "Psalm 119:1-999", unknown).size)
    }

    @Test
    fun versesAddedWithPlusVerseComeFirstAndHoldTheSameVersesNamed() {
        val added = listOf(NoteReference("John", 3, 16, "For God so loved"), NoteReference("Romans", 8, 0))
        val named = namedRefs("", "John 3:16-17 and Rom 8:28, then Genesis 1:1", unknown)
        assertEquals(listOf("John 3:16", "Romans 8:0", "John 3:17 named", "Genesis 1:1 named"), places(withNamed(added, named)))
    }

    @Test
    fun aNoteOnAWholeChapterIsFoundByAnyOfItsVerses() {
        val note = NoteItem(id = 1, refs = listOf(NoteReference("John", 3, 0)))
        assertTrue(NotesFinder.matchesText(note, "John 3"))
        assertTrue(NotesFinder.matchesText(note, "Jn 3:16"))
        assertTrue(NotesFinder.matchesText(note, "John 3:16-18"))
        assertFalse(NotesFinder.matchesText(note, "John 4:1"))
        assertFalse(NotesFinder.matchesText(NoteItem(id = 2, refs = listOf(NoteReference("John", 3, 16))), "John 3:17"))
    }
}
