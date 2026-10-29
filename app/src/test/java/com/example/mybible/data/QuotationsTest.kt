package com.example.mybible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun theQuotationsAVersesCrossReferencesMissAreLinksOfTheirOwn() {
        assertTrue(quotations.quotes("Psalms", 22, 2))
        assertFalse(quotations.quotes("Psalms", 22, 3))
        // Linked to neither side: both, each from the verse on the other.
        assertEquals(listOf(Quotations.Passage("Hosea", 11, 1, 1)), quotations.unlinked("Matthew", 2, 15, emptyList()))
        assertEquals(listOf(Quotations.Passage("Matthew", 27, 46, 46)), quotations.unlinked("Psalms", 22, 2, emptyList()))
        // A cross reference to the passage, or a longer one taking it in: none missed.
        assertTrue(quotations.unlinked("Matthew", 2, 15, listOf(Quotations.Passage("Hosea", 11, 1, 3))).isEmpty())
        assertTrue(quotations.unlinked("Isaiah", 25, 8, listOf(Quotations.Passage("1 Corinthians", 15, 55, 58))).isEmpty())
        // Cross references elsewhere don't count, and a verse quoting nothing misses nothing.
        assertEquals(1, quotations.unlinked("Matthew", 2, 15, listOf(Quotations.Passage("Hosea", 11, 2, 4))).size)
        assertTrue(quotations.unlinked("Matthew", 2, 14, emptyList()).isEmpty())
    }

    @Test
    fun aLinkToALongerPassageShowsTheVersesHoldingTheQuotation() {
        assertEquals(15..15, quotations.quotedPart("Hosea", 11, 1, "Matthew", 2, 13, 18))
        assertEquals(1..2, quotations.quotedPart("Matthew", 27, 46, "Psalms", 22, 1, 8))
        assertEquals(55..55, quotations.quotedPart("Isaiah", 25, 8, "1 Corinthians", 15, 55, 58))
        assertNull(quotations.quotedPart("Hosea", 11, 1, "Matthew", 2, 16, 18))
    }
}
