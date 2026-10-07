package com.example.mybible.versescroll

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RabbitHoleTest {

    private fun v(book: String, chapter: Int, verse: Int) = VerseRef(book, chapter, verse)

    /** A small link graph; every verse's text is one word of its own unless given. */
    private class FakeSource(
        private val links: Map<VerseRef, List<Link>>,
        private val texts: Map<VerseRef, String> = emptyMap()
    ) : LinkSource {
        override suspend fun links(ref: VerseRef) = links[ref].orEmpty().sortedByDescending { it.votes }
        override suspend fun words(refs: List<VerseRef>) =
            RabbitHole.wordsOf(refs.joinToString(" ") { texts[it] ?: uniqueWord(it) })

        // Letters only, since words drop digits: "John 3:16" -> "johndbg".
        private fun uniqueWord(ref: VerseRef) =
            ref.key.lowercase().filter { it.isLetterOrDigit() }.map { if (it.isDigit()) 'a' + (it - '0') else it }.joinToString("")
    }

    private val a = v("John", 3, 16)

    @Test
    fun staysOnTopicThreeLinksAtATimeThenFollowsTheStrongest() = runBlocking {
        val b = v("Romans", 5, 8)
        val c = v("1 John", 4, 9)
        val d = v("Ephesians", 2, 4)
        val b1 = v("Romans", 8, 32)
        val source = FakeSource(
            mapOf(
                a to listOf(Link(b, 50), Link(c, 40), Link(d, 30)),
                b to listOf(Link(b1, 20))
            )
        )
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), emptyList())
        val steps = (1..4).map { hole.next(thread, it)!! }
        assertEquals(listOf(b, c, d, b1), steps.map { it.link.ref })
        assertEquals(listOf(1, 2, 3), steps.take(3).map { it.nth })
        assertTrue(steps.take(3).all { it.of == 3 && it.from == a })
        // Then the strongest of the three becomes the next verse whose links are followed.
        assertEquals(b, steps[3].from)
    }

    @Test
    fun neverRepeatsAndOnlyFollowsStrongLinks() = runBlocking {
        val b = v("Romans", 5, 8)
        val weak = v("Jude", 1, 21)
        val seenBefore = v("Psalms", 23, 1)
        val source = FakeSource(
            mapOf(
                a to listOf(Link(b, 50), Link(seenBefore, 45), Link(weak, MIN_AUTO_VOTES - 1)),
                b to listOf(Link(a, 60))
            )
        )
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), seenKeys = listOf(seenBefore.key))
        assertEquals(b, hole.next(thread, 1)?.link?.ref)
        // Back to a (already shown) isn't allowed, the weak link isn't followed, and Psalm 23:1 was seen
        // before the thread began: nothing is left.
        assertNull(hole.next(thread, 2))
    }

    @Test
    fun skipsNearIdenticalParallels() = runBlocking {
        val matthew = v("Matthew", 3, 3)
        val mark = v("Mark", 1, 3)
        val other = v("Isaiah", 40, 3)
        val source = FakeSource(
            links = mapOf(a to listOf(Link(matthew, 50), Link(mark, 40), Link(other, 30))),
            texts = mapOf(
                matthew to "For this is he that was spoken of by the prophet Esaias, saying, The voice of one crying " +
                    "in the wilderness, Prepare ye the way of the Lord, make his paths straight.",
                mark to "The voice of one crying in the wilderness, Prepare ye the way of the Lord, make his paths straight.",
                other to "Make straight in the desert a highway for our God."
            )
        )
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), emptyList())
        val picked = listOfNotNull(hole.next(thread, 1), hole.next(thread, 2)).map { it.link.ref }
        assertEquals(listOf(matthew, other), picked)
    }

    @Test
    fun atMostTwoOfTheLastFourFromOneChapter() = runBlocking {
        val psalm = (1..4).map { v("Psalms", 119, it) }
        val elsewhere = v("Isaiah", 40, 31)
        val source = FakeSource(mapOf(a to psalm.map { Link(it, 40) } + Link(elsewhere, 10)))
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), emptyList())
        val picked = (1..3).mapNotNull { hole.next(thread, it)?.link?.ref }
        assertEquals(listOf(psalm[0], psalm[1], elsewhere), picked)
    }

    @Test
    fun backsUpToAnEarlierVerseWhenATrailRunsDry() = runBlocking {
        val b = v("Romans", 5, 8)
        val c = v("1 John", 4, 9)
        val e = v("Ephesians", 2, 4)
        val f = v("Titus", 3, 4)
        val g = v("Galatians", 2, 20)
        val source = FakeSource(
            mapOf(
                a to listOf(Link(b, 50)),
                b to listOf(Link(c, 20), Link(e, 15), Link(f, 12), Link(g, 11))
            )
        )
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), emptyList())
        val steps = (1..5).map { hole.next(thread, it)!! }
        assertEquals(listOf(b, c, e, f, g), steps.map { it.link.ref })
        // c (the strongest of b's three) has no links of its own, so the trail backs up to b for its fourth.
        assertEquals(b, steps[4].from)
        // And once nothing is left anywhere on the trail, the thread is done.
        assertNull(hole.next(thread, 6))
    }

    @Test
    fun aChosenLinkComesNextAndTheThreadCarriesOnFromIt() = runBlocking {
        val b = v("Romans", 5, 8)
        val chosen = v("Isaiah", 53, 5)
        val after = v("1 Peter", 2, 24)
        val source = FakeSource(
            mapOf(
                a to listOf(Link(b, 50), Link(chosen, 20)),
                chosen to listOf(Link(after, 30))
            )
        )
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), emptyList())
        val step = hole.take(thread, Link(chosen, 20), from = a, index = 1)
        assertEquals(a, step.from)
        hole.continueFrom(thread, chosen)
        assertEquals(after, hole.next(thread, 2)?.link?.ref)
    }

    @Test
    fun restartingFromAnEarlierCardForgetsWhatCameAfter() = runBlocking {
        val b = v("Romans", 5, 8)
        val c = v("1 John", 4, 9)
        val source = FakeSource(mapOf(a to listOf(Link(b, 50), Link(c, 40))))
        val hole = RabbitHole(source)
        val thread = hole.start(0, listOf(a), emptyList())
        hole.next(thread, 1)
        hole.next(thread, 2)
        hole.restartFrom(thread, index = 0, ref = a)
        // b and c are available again.
        assertEquals(b, hole.next(thread, 1)?.link?.ref)
    }

    @Test
    fun passagesCoverAllTheirVerses() {
        assertEquals(
            listOf(v("Isaiah", 40, 29), v("Isaiah", 40, 30), v("Isaiah", 40, 31)),
            Link(v("Isaiah", 40, 29), 30, count = 3).refs
        )
        assertEquals("Isaiah 40:29–31", labelOf(Link(v("Isaiah", 40, 29), 30, count = 3).refs))
    }

    @Test
    fun wordsLeaveOutTheCommonestOnes() {
        assertEquals(setOf("lord", "shepherd", "want"), RabbitHole.wordsOf("The LORD is my shepherd; I shall not want."))
        assertEquals(0.5, RabbitHole.similarity(setOf("a", "b", "c"), setOf("b", "c", "d")), 1e-9)
    }
}
