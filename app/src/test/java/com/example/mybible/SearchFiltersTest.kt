package com.example.mybible

import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.SearchSource
import com.example.mybible.model.Verse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Search's filter: its results in one part of the Bible. */
class SearchFiltersTest {

    private fun hit(book: String, chapter: Int, verse: Int) = SearchHit(Verse(book, chapter, verse, "…"), "…")

    // "worry": the word itself nowhere, the King James wording and merimnaō's verses in other words.
    private val worry = SearchOutcome(
        hits = listOf(
            hit("Psalms", 37, 1),
            hit("Matthew", 6, 25), hit("Luke", 10, 41), hit("Philippians", 4, 6)
        ),
        exactCount = 1,
        sources = listOf(
            SearchSource(listOf("kjv:careful"), "KJV: careful", 2, true, mapOf("Luke" to 1, "Philippians" to 1)),
            SearchSource(listOf("kjv:fret"), "KJV: fret", 1, true, mapOf("Psalms" to 1)),
            SearchSource(listOf("orig:G3309"), "Greek merimnaō", 3, false, mapOf("Matthew" to 1, "Luke" to 1, "Philippians" to 1))
        )
    )

    private fun SearchOutcome.refs() = hits.map { "${it.verse.book} ${it.verse.chapter}:${it.verse.number}" }

    @Test
    fun theWholeBibleIsTheResultsAsTheyAre() {
        assertSame(worry, worry.within(BiblePlace.WholeBible))
    }

    @Test
    fun aTestament() {
        val nt = worry.within(BiblePlace.NewTestament)
        assertEquals(listOf("Matthew 6:25", "Luke 10:41", "Philippians 4:6"), nt.refs())
        assertEquals(0, nt.exactCount)
        // Fret brings nothing here, so it goes; merimnaō is off, so it stays to be switched back on.
        assertEquals(listOf("KJV: careful" to 2, "Greek merimnaō" to 3), nt.sources.map { it.label to it.count })

        val ot = worry.within(BiblePlace.OldTestament)
        assertEquals(listOf("Psalms 37:1"), ot.refs())
        assertEquals(1, ot.exactCount)
        assertEquals(listOf("KJV: fret" to 1, "Greek merimnaō" to 0), ot.sources.map { it.label to it.count })
    }

    @Test
    fun aBook() {
        val luke = worry.within(BiblePlace.Book("Luke"))
        assertEquals(listOf("Luke 10:41"), luke.refs())
        assertEquals(listOf("KJV: careful" to 1, "Greek merimnaō" to 1), luke.sources.map { it.label to it.count })
        assertEquals(listOf("Psalms" to 1, "Matthew" to 1, "Luke" to 1, "Philippians" to 1), worry.versesByBook())
    }
}
