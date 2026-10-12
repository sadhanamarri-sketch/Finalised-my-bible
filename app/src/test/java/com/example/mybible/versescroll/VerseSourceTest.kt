package com.example.mybible.versescroll

import com.example.mybible.model.MatchedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VerseSourceTest {

    private fun refs(deck: VerseDeck, n: Int) = (1..n).map { deck.next() }

    @Test
    fun modesAreKeptButNotASearchsVerses() {
        val kept = listOf(
            VerseSource.Full, VerseSource.Focused, VerseSource.Book("1 John"), VerseSource.Chapter("Romans", 8),
            VerseSource.Highlighted, VerseSource.Studied
        )
        for (source in kept) assertEquals(source, VerseSource.decode(VerseSource.encode(source)))
        assertNull(VerseSource.encode(VerseSource.Found("“love”", "Search", listOf(VerseRef("John", 3, 16)))))
        assertNull(VerseSource.decode(null))
        assertNull(VerseSource.decode("chapter|Romans|x"))
        assertNull(VerseSource.decode("something else"))
    }

    @Test
    fun labelsSayWhereTheVersesComeFrom() {
        assertEquals("Full", VerseSource.Full.label)
        assertEquals("Romans 8", VerseSource.Chapter("Romans", 8).label)
        assertEquals("Romans 8, in order", VerseSource.Chapter("Romans", 8).blurb)
        assertEquals("3 verses from Search, shuffled", VerseSource.Found("“love”", "Search", List(3) { VerseRef("John", 3, it + 1) }).blurb)
        assertTrue(VerseSource.Book("Ruth").inOrder)
        assertTrue(!VerseSource.Highlighted.inOrder)
    }

    @Test
    fun aSearchsVersesByTheWordsThatFoundThem() {
        val love = MatchedWord("love", MatchedWord.Kind.WORD)
        val loveth = MatchedWord("loveth", MatchedWord.Kind.WORD)
        val charity = MatchedWord("charity", MatchedWord.Kind.WORDING)
        val agapao = MatchedWord("Greek agapaō", MatchedWord.Kind.ORIGINAL)
        val lev = VerseRef("Leviticus", 19, 18)
        val john = VerseRef("1 John", 4, 8)
        val cor = VerseRef("1 Corinthians", 13, 4)
        val ps = VerseRef("Psalms", 18, 1)
        val mk = VerseRef("Mark", 12, 30)
        val matched = mapOf(
            lev.key to listOf(love), john.key to listOf(loveth, love), cor.key to listOf(charity),
            ps.key to listOf(agapao), mk.key to listOf(love)
        )
        val found = VerseSource.Found("“love”", "Search", listOf(lev, john, cor, ps, mk), matched)

        // The word searched for first, then the King James wording, then Greek and Hebrew; most verses first.
        assertEquals(listOf(love to 3, loveth to 1, charity to 1, agapao to 1), found.words)
        assertEquals(listOf(loveth, love), found.wordsOf(john))
        // Nothing picked: all of them.
        assertEquals(found.refs, found.shown)
        assertEquals("5 verses from Search, shuffled", found.blurb)

        // A verse is shown when any word that found it is picked.
        val picked = found.copy(only = setOf(loveth, charity))
        assertEquals(listOf(john, cor), picked.shown)
        assertEquals("2 of 5 verses from Search, shuffled", picked.blurb)
        // The chips stay, with their counts over all of the verses.
        assertEquals(found.words, picked.words)

        // One word for every verse, or none (a topic's): nothing to pick, and no chips on the cards.
        val one = VerseSource.Found("“LORD”", "Search", listOf(lev, mk), mapOf(lev.key to listOf(love), mk.key to listOf(love)))
        assertTrue(one.words.isEmpty())
        assertTrue(one.wordsOf(lev).isEmpty())
        val topic = VerseSource.Found("Faith", "Nave’s topic", listOf(lev, mk))
        assertTrue(topic.words.isEmpty())
        assertEquals(topic.refs, topic.shown)
    }

    @Test
    fun aBookGoesThroughInOrderAndEnds() {
        val deck = ReadingOrderDeck("Ruth", listOf(1 to 2, 2 to 3))
        assertEquals(5, deck.size)
        assertEquals(
            listOf(VerseRef("Ruth", 1, 1), VerseRef("Ruth", 1, 2), VerseRef("Ruth", 2, 1), VerseRef("Ruth", 2, 2), VerseRef("Ruth", 2, 3), null),
            refs(deck, 6)
        )
        assertNull(deck.next())
    }

    @Test
    fun aBookPicksUpWhereItWasLeft() {
        val deck = ReadingOrderDeck("Ruth", listOf(1 to 2, 2 to 3), startAt = 2 to 2)
        assertEquals(listOf(VerseRef("Ruth", 2, 2), VerseRef("Ruth", 2, 3), null), refs(deck, 3))
        // A place that isn't in it starts at the beginning.
        assertEquals(VerseRef("Ruth", 1, 1), ReadingOrderDeck("Ruth", listOf(1 to 2), startAt = 9 to 9).next())
    }

    @Test
    fun aBookCarriesOnFromTheLastVerseStillShown() {
        val deck = ReadingOrderDeck("Ruth", listOf(1 to 3, 2 to 2))
        refs(deck, 4) // 1:1, 1:2, 1:3, 2:1 dealt; only up to 1:2 kept
        deck.continueAfter(1, 2)
        assertEquals(listOf(VerseRef("Ruth", 1, 3), VerseRef("Ruth", 2, 1)), refs(deck, 2))
        deck.continueAfter(null, null)
        assertEquals(VerseRef("Ruth", 1, 1), deck.next())
        // With none of its verses left, one picked up from last time goes back to where it picked up.
        val resumed = ReadingOrderDeck("Ruth", listOf(1 to 3, 2 to 2), startAt = 1 to 3)
        refs(resumed, 2)
        resumed.continueAfter(null, null)
        assertEquals(VerseRef("Ruth", 1, 3), resumed.next())
    }

    @Test
    fun aShuffledListShowsEveryVerseEachRoundAndNeverOneTwiceRunning() {
        val all = (1..7).map { VerseRef("Psalms", 23, it) }
        repeat(50) { seed ->
            val deck = ShuffledDeck(all + all.take(2), Random(seed))
            assertEquals(7, deck.size)
            val shown = refs(deck, 70)
            shown.chunked(7).forEach { round -> assertEquals(all.toSet(), round.toSet()) }
            shown.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        }
        assertNull(ShuffledDeck(emptyList()).next())
        val one = ShuffledDeck(listOf(VerseRef("John", 11, 35)))
        assertEquals(listOf(VerseRef("John", 11, 35), VerseRef("John", 11, 35)), refs(one, 2))
    }

    @Test
    fun focusedDealsOnlyTheBestKnown() {
        val familiar = (1..20).map { DiscoverEntry(VerseRef("Psalms", 1, it), 1.0, familiar = true) }
        val others = (1..200).map { DiscoverEntry(VerseRef("Numbers", 1, it), 1.0, familiar = false) }
        val pool = DiscoverPool(familiar + others, Random(7), recentLimit = 5)
        assertEquals(220, pool.size)
        assertEquals(20, pool.familiarSize)
        repeat(100) { assertEquals("Psalms", pool.next(familiarOnly = true)!!.book) }
        // Full still deals both.
        assertTrue((1..100).map { pool.next()!!.book }.toSet() == setOf("Psalms", "Numbers"))
    }
}
