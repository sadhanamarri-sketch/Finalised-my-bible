package com.example.mybible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotationsTest {

    private val quotations = Quotations.parse(
        """
        # The New Testament's verses, then the Old Testament ones they quote.
        Matthew 2:15	Hosea 11:1
        Matthew 27:46	Psalms 22:1-2
        1 Corinthians 15:54-55	Isaiah 25:8
        not a quotation
        """.trimIndent()
    )

    @Test
    fun readsTheListLeavingOutNotesAndAnythingElse() {
        assertEquals(3, quotations.size)
    }

    @Test
    fun marksTheVersesThatQuoteAndAreQuoted() {
        assertEquals(setOf(15), quotations.versesIn("Matthew", 2))
        assertEquals(setOf(1), quotations.versesIn("Hosea", 11))
        assertEquals(setOf(1, 2), quotations.versesIn("Psalms", 22))
        assertEquals(setOf(54, 55), quotations.versesIn("1 Corinthians", 15))
        assertTrue(quotations.versesIn("Matthew", 3).isEmpty())
    }

    @Test
    fun aCrossReferenceIsAQuotationEitherWayRound() {
        assertTrue(quotations.isQuote("Matthew", 2, 15, "Hosea", 11, 1, 1))
        assertTrue(quotations.isQuote("Hosea", 11, 1, "Matthew", 2, 15, 15))
        // A link to a passage that takes in the verse quoted, or one of a passage quoted.
        assertTrue(quotations.isQuote("Matthew", 27, 46, "Psalms", 22, 2, 5))
        assertTrue(quotations.isQuote("Isaiah", 25, 8, "1 Corinthians", 15, 55, 55))
        // The same verses, but not the quotation's other side.
        assertFalse(quotations.isQuote("Matthew", 2, 15, "Hosea", 11, 2, 3))
        assertFalse(quotations.isQuote("Matthew", 2, 14, "Hosea", 11, 1, 1))
        assertFalse(Quotations.None.isQuote("Matthew", 2, 15, "Hosea", 11, 1, 1))
    }
}
