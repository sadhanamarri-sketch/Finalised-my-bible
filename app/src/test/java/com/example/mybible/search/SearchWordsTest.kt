package com.example.mybible.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWordsTest {

    @Test
    fun plainFormsUndoEndings() {
        assertTrue("worry" in plainForms("worried"))
        assertTrue("worry" in plainForms("worries"))
        assertTrue("worry" in plainForms("worrying"))
        assertTrue("love" in plainForms("loving"))
        assertTrue("run" in plainForms("running"))
        assertTrue("wife" in plainForms("wives"))
        assertTrue("demon" in plainForms("demons"))
        assertTrue("lie" in plainForms("lying"))
        // Never shorter than the word's own sense: "less" isn't "les".
        assertFalse("les" in plainForms("less"))
    }

    @Test
    fun editDistanceCountsASwapAsOne() {
        assertEquals(1, editDistance("fiath", "faith", 2))
        assertEquals(1, editDistance("hpoe", "hope", 1))
        assertEquals(1, editDistance("lov", "love", 1))
        assertEquals(0, editDistance("grace", "grace", 2))
        assertEquals(3, editDistance("kitten", "sitting", 3))
        // Past the limit it stops early and says limit + 1.
        assertEquals(2, editDistance("kitten", "sitting", 1))
        assertEquals(2, editDistance("a", "abcdef", 1))
    }

    @Test
    fun wordsAreRunsOfLettersLowercased() {
        val found = ArrayList<Triple<String, Int, Int>>()
        forEachWord("God's love, O LORD.") { word, start, end -> found += Triple(word, start, end) }
        assertEquals(
            listOf(Triple("god", 0, 3), Triple("s", 4, 5), Triple("love", 6, 10), Triple("o", 12, 13), Triple("lord", 14, 18)),
            found
        )
        assertTrue(hasLatinLetter("love"))
        assertFalse(hasLatinLetter("ప్రేమ"))
    }
}
