package com.example.mybible.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** A note's many verses folded: the first three, and how many wait behind "+N more". */
class NoteRefsFoldTest {

    private val eight = (1..8).toList()

    @Test
    fun manyFoldToTheFirstThree() {
        assertEquals(listOf(1, 2, 3) to 5, foldedList(eight, showAll = false))
        // Opened: all of them, none waiting.
        assertEquals(eight to 0, foldedList(eight, showAll = true))
    }

    @Test
    fun threeOrFewerNeverFold() {
        assertEquals(listOf(1, 2, 3) to 0, foldedList(listOf(1, 2, 3), showAll = false))
        assertEquals(listOf(1) to 0, foldedList(listOf(1), showAll = false))
        assertEquals(emptyList<Int>() to 0, foldedList(emptyList<Int>(), showAll = false))
        // Four is the first that folds: three, and one more.
        assertEquals(listOf(1, 2, 3) to 1, foldedList(listOf(1, 2, 3, 4), showAll = false))
    }

    @Test
    fun countedByPassageNotByVerse() {
        // John 15:1–8 is one pill: eight verses, one passage, nothing folded.
        val refs = (1..8).map { NoteReference("John", 15, it) } + NoteReference("Psalms", 23, 1)
        val ranges = groupRefs(refs) { if (it == "Psalms") 18 else 42 }
        assertEquals(listOf("Psalms 23:1", "John 15:1–8"), ranges.map { it.label })
        assertEquals(0, foldedList(ranges, showAll = false).second)
    }
}
