package com.example.mybible.search

import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Searches for a Greek or Hebrew word, over [TestBible]'s verses with the real bundled data. */
class OriginalSearchTest {

    private val search = SmartSearch(TestBible.index, TestBible.lexicon)

    private fun SearchHit.ref() = "${verse.book} ${verse.chapter}:${verse.number}"
    private fun SearchOutcome.refs() = hits.map { it.ref() }
    private fun SearchOutcome.hit(ref: String) = hits.first { it.ref() == ref }
    private fun SearchHit.marked() = highlights.map { text.substring(it) }
    private fun SearchOutcome.numbers() = originalWords.map { it.number }

    @Test
    fun aGreekWordWithOrWithoutItsAccents() {
        val agape = search.search("ἀγάπη")
        assertEquals(listOf("G26"), agape.numbers())
        assertEquals(listOf("1 Corinthians 13:4", "1 John 4:8"), agape.refs())
        assertEquals(agape.exactCount, agape.hits.size)
        assertEquals(listOf("Charity", "charity", "charity"), agape.hit("1 Corinthians 13:4").marked())
        val card = agape.originalWords.single()
        assertEquals("Greek", card.language)
        assertEquals("ἀγάπη", card.word)
        assertEquals("agapē", card.transliteration)
        assertEquals(listOf("love"), card.meanings)
        assertEquals(listOf("love", "charity"), card.kingJames)
        assertEquals(agape.refs(), search.search("αγαπη").refs())
        // Topics and King James wording are for English searches.
        assertTrue(agape.sources.isEmpty())
    }

    @Test
    fun anyFormTheTextHas() {
        // ἠγάπησεν: "he loved", agapaō.
        val loved = search.search("ἠγάπησεν")
        assertEquals(listOf("G25"), loved.numbers())
        assertEquals(listOf("loved"), loved.hit("John 3:16").marked())
    }

    @Test
    fun hebrewWithItsPrefixesAndPoints() {
        // "In the beginning": bə- is "in", rēʾšîṯ the word.
        val beginning = search.search("בְּרֵאשִׁית")
        assertEquals(listOf("H7225"), beginning.numbers())
        assertEquals(listOf("beginning"), beginning.hit("Genesis 1:1").marked())
        assertEquals(listOf("Genesis 1:1", "Genesis 1:17"), search.search("אלהים").refs())
        // Without its points, אהבה is love the noun (ahavah) or the verb (ahav), the word itself
        // first; pointed אַהֲבָה is the noun alone.
        assertEquals(listOf("H160", "H157"), search.search("אהבה").numbers())
        assertEquals(listOf("H160"), search.search("אַהֲבָה").numbers())
    }

    @Test
    fun strongsNumbers() {
        assertEquals(listOf("G26"), search.search("G26").numbers())
        assertEquals(search.search("G26").refs(), search.search("g0026").refs())
        // One sense of aphiēmi: "forgive", not "leave".
        val forgive = search.search("G863H")
        assertEquals(listOf("Matthew 6:14", "John 20:23"), forgive.refs())
        assertEquals(listOf("forgive"), forgive.originalWords.single().meanings)
        assertTrue(search.search("G99999").hits.isEmpty())
    }

    @Test
    fun aWordsMeaningsAreChips() {
        val aphiemi = search.search("ἀφίημι")
        assertEquals(listOf("Matthew 4:20", "Matthew 6:14", "John 20:23"), aphiemi.refs())
        assertEquals(listOf("forgive" to 2, "to release" to 1), aphiemi.sources.map { it.label to it.count })
        assertEquals(listOf("aphiēmi, meaning “to release”"), aphiemi.hit("Matthew 4:20").reasons)
        assertEquals(listOf("forgive", "forgive"), aphiemi.hit("Matthew 6:14").marked())

        val off = search.search("ἀφίημι", disabled = aphiemi.sources.first { it.label == "forgive" }.ids.toSet())
        assertEquals(listOf("Matthew 4:20"), off.refs())
        // Its chip stays, off, saying what it would bring back.
        assertFalse(off.sources.first { it.label == "forgive" }.enabled)
        assertEquals(2, off.sources.first { it.label == "forgive" }.count)
    }

    @Test
    fun everyWordOfTheQuery() {
        // God is love: agapē and theos.
        val godIsLove = search.search("ἀγάπη θεός")
        assertEquals(listOf("1 John 4:8"), godIsLove.refs())
        assertEquals(listOf("G26", "G2316"), godIsLove.numbers())
        // No word has two meanings here: nothing to switch off.
        assertTrue(godIsLove.sources.isEmpty())
    }

    @Test
    fun transliterations() {
        assertEquals(listOf("G26"), search.search("agape").numbers())
        // STEPBible writes kurios and chesed; people also write kyrios and hesed.
        assertEquals(listOf("G2962"), search.search("kyrios").numbers())
        assertEquals("H2617", search.search("hesed").numbers().first())
        // English words stay English: love is in the King James, ego is today's word for pride.
        assertTrue(search.search("love").originalWords.isEmpty())
        assertTrue(search.search("ego").originalWords.isEmpty())
    }

    @Test
    fun aMisspelledGreekWord() {
        val typo = search.search("αγαπι")
        assertTrue(typo.hits.isEmpty())
        assertEquals("ἀγάπη", typo.suggestion)
        assertNull(search.search("ἀγάπη").suggestion)
    }

    @Test
    fun theKeysOfWords() {
        assertEquals("αγαπησ", originalKey("ἀγάπης"))
        assertEquals("חסד", originalKey("חֶ֫סֶד"))
        assertEquals("שלומ", originalKey("שָׁלוֹם"))
        assertEquals("agape", latinKey("agapē"))
        assertEquals("chesed", latinKey("che.sed"))
        assertTrue(hasOriginalLetter("λόγος") && hasOriginalLetter("אמר"))
        assertFalse(hasOriginalLetter("logos") || hasOriginalLetter("ప్రేమ"))
    }
}
