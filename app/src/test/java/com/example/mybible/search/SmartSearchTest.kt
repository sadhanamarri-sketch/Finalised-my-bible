package com.example.mybible.search

import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The search pipeline on a handful of real King James verses (see [TestBible]). */
class SmartSearchTest {

    private val search = SmartSearch(TestBible.index, TestBible.lexicon)

    private fun SearchHit.ref() = "${verse.book} ${verse.chapter}:${verse.number}"
    private fun SearchOutcome.exact() = hits.take(exactCount).map { it.ref() }
    private fun SearchOutcome.related() = hits.drop(exactCount).map { it.ref() }
    private fun SearchOutcome.hit(ref: String) = hits.first { it.ref() == ref }
    private fun SearchHit.marked() = highlights.map { text.substring(it) }

    @Test
    fun todaysWordFindsTheKingJamesWording() {
        val worry = search.search("worry")
        assertEquals(emptyList<String>(), worry.exact())
        assertTrue(worry.related().containsAll(listOf("Matthew 6:25", "Matthew 6:34", "Luke 10:41", "Philippians 4:6")))
        assertEquals(listOf("KJV wording: “take no thought”"), worry.hit("Matthew 6:25").reasons)
        assertEquals(listOf("Take no thought"), worry.hit("Matthew 6:25").marked())
        assertEquals(listOf("careful"), worry.hit("Philippians 4:6").marked())
        assertTrue(worry.sources.any { it.label == "KJV: careful" && it.count == 2 })
        assertTrue(worry.sources.any { it.label == "Greek merimnaō" })
        assertNull(worry.suggestion)
    }

    @Test
    fun aSwitchedOffSourceDropsItsVerses() {
        val careful = search.search("worry", disabled = setOf("kjv:careful"))
        // Philippians 4:6 is still merimnaō in Greek.
        assertEquals(listOf("Greek merimnaō, meaning “to worry”"), careful.hit("Philippians 4:6").reasons)
        assertFalse(careful.sources.first { it.label == "KJV: careful" }.enabled)

        val neither = search.search("worry", disabled = setOf("kjv:careful", "orig:G3309"))
        assertFalse("Philippians 4:6" in neither.related())
        // Its chip stays, saying what it would bring back.
        assertEquals(2, neither.sources.first { it.label == "KJV: careful" }.count)
    }

    @Test
    fun wordFormsAndTheSameGreekWord() {
        val love = search.search("love")
        assertTrue(love.exact().containsAll(listOf("Leviticus 19:18", "John 3:16", "1 John 4:8")))
        assertEquals(listOf("loved"), love.hit("John 3:16").marked())
        assertEquals(listOf("loveth", "love"), love.hit("1 John 4:8").marked())
        // Charity: the King James wording for love, and the same Greek word, agapē.
        assertTrue("1 Corinthians 13:4" in love.related())
        assertEquals(listOf("Charity", "charity", "charity"), love.hit("1 Corinthians 13:4").marked())
        // Of these verses only John 3:16 has love in another form alone.
        assertTrue(love.sources.first().let { it.label == "Word forms" && it.enabled && it.count == 1 })

        val literal = search.search("love", disabled = setOf(SmartSearch.FORMS))
        assertFalse("John 3:16" in literal.exact())
    }

    @Test
    fun theSenseOfAGreekWord() {
        val forgive = search.search("forgive")
        assertEquals(listOf("Matthew 6:14"), forgive.exact())
        // aphiēmi where it means forgive: "ye remit".
        assertEquals(listOf("John 20:23"), forgive.related())
        assertEquals(listOf("Greek aphiēmi, meaning “forgive”"), forgive.hit("John 20:23").reasons)
    }

    @Test
    fun pronounsAndPhrases() {
        val iLoveYou = search.search("I love you")
        val judges = iLoveYou.hit("Judges 16:15")
        assertTrue(judges.related)
        assertEquals(listOf("KJV wording: “thee”"), judges.reasons)
        assertTrue("love thee" in judges.marked())

        val spirit = search.search("holy spirit")
        assertEquals(listOf("Luke 11:13"), spirit.exact())
        assertEquals(listOf("Matthew 1:18"), spirit.related())
        assertEquals(listOf("Holy Ghost"), spirit.hit("Matthew 1:18").marked())
    }

    @Test
    fun functionWordsAreOptionalButRank() {
        // "to" isn't needed, and "unto" stands in for it in the ranking: Come unto me first.
        val come = search.search("come to me")
        assertEquals("Matthew 11:28", come.exact().first())
        assertTrue("Come unto me" in come.hit("Matthew 11:28").marked())

        val shepherd = search.search("the lord is my shepherd")
        assertEquals(listOf("Psalms 23:1"), shepherd.exact())
        assertEquals(listOf("The LORD is my shepherd"), shepherd.hit("Psalms 23:1").marked())
    }

    @Test
    fun nothingFound() {
        assertEquals("faith", search.search("fiath").suggestion)
        assertTrue(search.search("fiath").hits.isEmpty())

        val close = search.search("the truth will set you free")
        assertTrue(close.closeMatches)
        assertEquals(listOf("John 8:32"), close.hits.map { it.ref() })
        assertEquals(listOf("Without “set”"), close.hits[0].reasons)

        assertTrue(search.search("xyzzy plugh").hits.isEmpty())
        assertTrue(search.search("a").hits.isEmpty())
    }

    @Test
    fun caseSensitiveIsLiteral() {
        val lord = search.search("LORD", caseSensitive = true)
        assertEquals(listOf("Leviticus 19:18", "Psalms 18:1", "Psalms 23:1"), lord.exact())
        assertTrue(lord.sources.isEmpty())
        assertTrue(search.search("Lord", caseSensitive = true).hits.isEmpty())
        // No King James wording either.
        assertTrue(search.search("worry", caseSensitive = true).hits.isEmpty())
    }
}
