package com.example.mybible.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Against the real bundled data in app/src/main/assets/search. */
class SearchLexiconTest {

    private val lexicon = SearchLexicon.load { name -> File("src/main/assets/search/$name").inputStream() }

    // A stand-in for the index: these tests only need to know a few words are King James ones.
    private val kingJamesWords = setOf(
        "love", "loved", "loveth", "charity", "forgive", "healing", "being", "went", "careful", "spirit", "holy"
    )
    private val inBible: (String) -> Boolean = { it in kingJamesWords }

    @Test
    fun formsComeFromTheWordFamilies() {
        val love = lexicon.formsOf("love")
        assertTrue(love.containsAll(listOf("loved", "loveth", "loving", "lovest")))
        assertFalse("beloved" in love)
        assertFalse("lovely" in love)
        assertTrue(lexicon.formsOf("loved").contains("love"))
        // A word that's a base of its own family is still a form in another's.
        assertTrue(lexicon.formsOf("healing").containsAll(listOf("heal", "healed", "healings")))
        // Irregular: went is go's.
        assertTrue("go" in lexicon.formsOf("went"))
        // Forms of be, have and do never expand: being isn't "is" or "was".
        assertEquals(setOf("being"), lexicon.formsOf("being"))
        assertEquals(setOf("the"), lexicon.formsOf("the"))
    }

    @Test
    fun todaysWordsHaveTheirKingJamesWording() {
        assertTrue(listOf("holy", "ghost") in lexicon.kingJamesWording(listOf("holy", "spirit"), inBible)!!)
        // Endings don't matter on today's words.
        val worried = lexicon.kingJamesWording(listOf("worried"), inBible)!!
        assertTrue(listOf("careful") in worried)
        assertTrue(listOf("take", "no", "thought") in worried)
        assertTrue(listOf("thee") in lexicon.kingJamesWording(listOf("you"), inBible)!!)
        assertEquals(null, lexicon.kingJamesWording(listOf("grace"), inBible))
    }

    @Test
    fun greekAndHebrewWordsByMeaning() {
        val worry = lexicon.meaningsOf("worry", inBible).map { it.word.key }
        assertTrue("G3309" in worry) // merimnaō, "to worry"

        // aphiēmi only where it means forgive (John 20:23 "remit"), not where it means leave.
        val forgive = lexicon.meaningsOf("forgive", inBible).map { it.word.key }
        assertTrue("G0863H" in forgive)
        assertFalse("G0863G" in forgive)
        assertTrue("H5545" in forgive) // salach: "pardon"
        // charizomai is "forgive" 42% of the time, but "give" or "deliver" most of the rest.
        assertFalse("G5483" in forgive)

        // agapē is rendered charity 23% of the time, love 75%: the two are interchangeable there.
        val charity = lexicon.meaningsOf("charity", inBible)
        assertTrue(charity.any { it.word.key == "G0026" && !it.byMeaning })

        // "lovely" (naeh, "comely") isn't love, and neither is "money-loving".
        val love = lexicon.meaningsOf("love", inBible).map { it.word.key }
        assertTrue("G0026" in love)
        assertFalse("H5000" in love)
        assertFalse("G5366" in love)

        assertTrue(lexicon.meaningsOf("the", inBible).isEmpty())
    }

    @Test
    fun glossHeadsAreTheSingleWordsAGlossMeans() {
        assertEquals(setOf("worry"), glossHeads("to worry"))
        assertEquals(setOf("hope", "expect"), glossHeads("to hope/expect"))
        assertEquals(setOf("look"), glossHeads("to look up"))
        assertEquals(emptySet<String>(), glossHeads("brotherly love"))
        assertEquals(emptySet<String>(), glossHeads("money-loving"))
    }

    @Test
    fun verseListsDecode() {
        // Genesis 1:1 is 0 * 65536 + 1 * 256 + 1 = 257 = "75" in base 36; Genesis 1:2 is one more.
        assertEquals(listOf(257, 258), SearchLexicon.decodeRefs("75,1").toList())
        assertEquals(emptyList<Int>(), SearchLexicon.decodeRefs("").toList())
        val merimnao = lexicon.meaningsOf("worry", inBible).first { it.word.key == "G3309" }.word
        assertEquals(merimnao.verseCount, merimnao.verses.size)
        // Matthew (book 39) 6:25.
        assertTrue(BibleIndex.packRef(39, 6, 25) in merimnao.verses.toList())
    }
}
