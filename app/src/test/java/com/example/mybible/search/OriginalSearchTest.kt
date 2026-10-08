package com.example.mybible.search

import com.example.mybible.model.RelatedKind
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
    private fun SearchOutcome.exact() = hits.take(exactCount).map { it.ref() }
    private fun SearchOutcome.related() = hits.drop(exactCount).map { it.ref() }
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
        assertEquals(listOf("Genesis 1:1", "Genesis 1:17"), search.search("אלהים").exact())
        // Without its points, אהבה is love the noun (ahavah) or the verb (ahav), the word itself
        // first; pointed אַהֲבָה is the noun alone.
        val unpointed = search.search("אהבה")
        assertEquals(listOf("H160", "H157"), unpointed.numbers())
        assertEquals(listOf(false, true), unpointed.originalWords.map { it.alternative })
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
        // Two words of the query, each the word it is.
        assertTrue(godIsLove.originalWords.none { it.alternative })
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
    fun theOtherTestamentInItsLanguage() {
        // agapaō "loved" in John 3:16 and "loveth" in 1 John 4:8; then the Old Testament verses
        // with ahav, which the Septuagint translates with agapaō.
        val loved = search.search("ἠγάπησεν")
        assertEquals(listOf("John 3:16", "1 John 4:8"), loved.exact())
        assertEquals(listOf("Leviticus 19:18", "Judges 16:15"), loved.related())
        assertEquals(RelatedKind.OLD_TESTAMENT, loved.relatedKind)
        assertEquals("Where the Hebrew has אָהֵב (ahav), which the Septuagint, the Greek Old Testament, translates with ἀγαπάω.", loved.relatedNote)
        assertEquals(listOf("Hebrew ahav, meaning “to love”"), loved.hit("Leviticus 19:18").reasons)
        assertEquals(listOf("love"), loved.hit("Leviticus 19:18").marked())
        val ahav = loved.sources.single()
        assertEquals("Hebrew ahav" to 2, ahav.label to ahav.count)

        // Switched off: the New Testament only.
        val off = search.search("ἠγάπησεν", disabled = ahav.ids.toSet())
        assertEquals(listOf("John 3:16", "1 John 4:8"), off.refs())
        assertFalse(off.sources.single().enabled)

        // And the other way: ahav, then the New Testament's agapaō.
        val hebrew = search.search("H157")
        assertEquals(listOf("Leviticus 19:18", "Judges 16:15"), hebrew.exact())
        assertEquals(listOf("John 3:16", "1 John 4:8"), hebrew.related())
        assertEquals(RelatedKind.NEW_TESTAMENT, hebrew.relatedKind)
    }

    @Test
    fun whatFindingEveryVerseWithAWordFinds() {
        // As the Reader's interlinear tags agapaō.
        val preview = search.preview("G0025")!!
        assertEquals("G25", preview.query)
        assertEquals(2, preview.verseCount)
        assertEquals(2, preview.otherVerseCount)
        assertEquals("Old Testament", preview.otherTestament)
        assertEquals(listOf("אָהֵב (ahav)"), preview.otherWords)
        assertEquals("H7225", search.preview("H7225G")!!.query)
        assertNull(search.preview("G99999"))
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

    // ---- a word's use, for its page ----

    private fun ref(r: String) = TestBible.verses.first { "${it.book} ${it.chapter}:${it.number}" == r }
        .let { BibleIndex.packRef(it.bookIndex, it.chapter, it.number) }

    @Test
    fun aWordsMeaningHereAmongItsOthers() {
        // aphiēmi where it's "forgive", by its sense letter.
        val study = search.study("G0863H", ref("Matthew 6:14"))!!
        assertEquals("G863", study.query)
        assertEquals("forgive", study.meaningHere)
        assertEquals(listOf("forgive" to 2, "to release" to 1), study.meanings.map { it.label to it.count })
        // Without a sense letter, by the verse.
        assertEquals("to release", search.study("G0863", ref("Matthew 4:20"))!!.meaningHere)
        // Search with the other meanings' chips off, and the Old Testament's words: this one's verses.
        assertTrue("other:H5545" in study.meanings[0].offChips)
        val forgive = search.search("G863", disabled = study.meanings[0].offChips)
        assertEquals(listOf("Matthew 6:14", "John 20:23"), forgive.refs())
        // One meaning: nothing to choose between.
        val agape = search.study("G0026", ref("1 John 4:8"))!!
        assertNull(agape.meaningHere)
        assertTrue(agape.meanings.isEmpty())
    }

    @Test
    fun howTheKingJamesTranslatesIt() {
        assertEquals(listOf("love" to 75, "charity" to 23), search.study("G0026", null)!!.kingJames.map { it.label to it.count })
        // Forms of a word are one word: love, loved.
        assertEquals(listOf("love"), search.study("G0025", null)!!.kingJames.map { it.label })
    }

    @Test
    fun theBooksItsIn() {
        val study = search.study("G0863H", null)!!
        assertEquals(listOf("Matthew" to 2, "John" to 1), study.books.map { it.label to it.count })
        assertEquals(2, study.bookCount)
        assertNull(search.study("G9999", null))
    }
}
