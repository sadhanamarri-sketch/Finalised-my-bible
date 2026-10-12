package com.example.mybible.data

import com.example.mybible.model.WordLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordLinksTest {

    @Test
    fun aWordIsLinkedByItsNumberWithoutASense() {
        assertEquals("G0026", linkNumber("G26"))
        assertEquals("G0026", linkNumber("g0026"))
        assertEquals("H7225", linkNumber("H7225G"))
        assertEquals("H2617", linkNumber(" H2617 "))
        assertNull(linkNumber("chesed"))
        assertNull(linkNumber("G12345"))
    }

    @Test
    fun linkingTwiceIsOneLinkAndRemovingTakesItOff() {
        var links = emptyList<WordLink>()
        links = links.withLink("G0026", "H2617", now = 1)
        links = links.withLink("G0026", "H0160", now = 2)
        links = links.withLink("G0026", "H2617", now = 3)
        assertEquals(listOf("H2617", "H0160"), links.map { it.hebrew })
        assertEquals(1L, links[0].createdAt)
        links = links.withoutLink("G0026", "H2617")
        assertEquals(listOf("H0160"), links.map { it.hebrew })
    }

    @Test
    fun eachWordFindsTheWordsItsLinkedTo() {
        val links = listOf(WordLink("G0026", "H2617"), WordLink("G1656", "H2617"))
        val byNumber = links.byNumber()
        assertEquals(listOf("H2617"), byNumber["G0026"])
        assertEquals(listOf("G0026", "G1656"), byNumber["H2617"])
        assertNull(byNumber["H0160"])
    }

    @Test
    fun aBackupsLinksMergeWithoutBringingBackOnesRemoved() {
        val here = listOf(WordLink("G0026", "H2617", createdAt = 10))
        val backup = listOf(
            WordLink("G0026", "H2617", createdAt = 5),
            // Removed here at 20, after it was made: stays removed.
            WordLink("G1656", "H2617", createdAt = 15),
            // Removed at 20, but linked again on the other phone after that: back.
            WordLink("G0025", "H0157", createdAt = 25),
            WordLink("G26", "H160", createdAt = 30),
            // Not a Greek and a Hebrew word.
            WordLink("H0160", "G0026", createdAt = 30)
        )
        val tombstones = mapOf(
            wordLinkTombstoneKey("G1656", "H2617") to 20L,
            wordLinkTombstoneKey("G0025", "H0157") to 20L
        )
        val (merged, added) = mergeWordLinks(here, backup, tombstones)
        assertEquals(listOf("G0026:H2617", "G0025:H0157", "G0026:H0160"), merged.map { "${it.greek}:${it.hebrew}" })
        assertEquals(10L, merged[0].createdAt)
        assertEquals(2, added)
    }
}
