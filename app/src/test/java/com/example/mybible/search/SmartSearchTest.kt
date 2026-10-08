package com.example.mybible.search

import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The search pipeline on a handful of real King James verses, with the real bundled data —
 * whose Greek and Hebrew verse lists name real verses, so the ones here are found through them.
 */
class SmartSearchTest {

    private val books = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
        "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
        "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
        "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah",
        "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
        "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
        "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians",
        "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter",
        "1 John", "2 John", "3 John", "Jude", "Revelation"
    )

    private val verses = listOf(
        Triple("Genesis 1:1", 0, "In the beginning God created the heaven and the earth."),
        Triple("Genesis 1:17", 0, "And God set them in the firmament of the heaven to give light upon the earth,"),
        Triple("Leviticus 19:18", 0, "Thou shalt not avenge, nor bear any grudge against the children of thy people, but thou shalt love thy neighbour as thyself: I am the LORD."),
        Triple("Judges 16:15", 0, "And she said unto him, How canst thou say, I love thee, when thine heart is not with me? thou hast mocked me these three times, and hast not told me wherein thy great strength lieth."),
        Triple("Psalms 18:1", 0, "I will love thee, O LORD, my strength."),
        Triple("Psalms 23:1", 0, "The LORD is my shepherd; I shall not want."),
        Triple("Matthew 1:18", 0, "Now the birth of Jesus Christ was on this wise: When as his mother Mary was espoused to Joseph, before they came together, she was found with child of the Holy Ghost."),
        Triple("Matthew 6:14", 0, "For if ye forgive men their trespasses, your heavenly Father will also forgive you:"),
        Triple("Matthew 6:25", 0, "Therefore I say unto you, Take no thought for your life, what ye shall eat, or what ye shall drink; nor yet for your body, what ye shall put on. Is not the life more than meat, and the body than raiment?"),
        Triple("Matthew 6:34", 0, "Take therefore no thought for the morrow: for the morrow shall take thought for the things of itself. Sufficient unto the day is the evil thereof."),
        Triple("Matthew 11:28", 0, "Come unto me, all ye that labour and are heavy laden, and I will give you rest."),
        Triple("Luke 10:41", 0, "And Jesus answered and said unto her, Martha, Martha, thou art careful and troubled about many things:"),
        Triple("Luke 11:13", 0, "If ye then, being evil, know how to give good gifts unto your children: how much more shall your heavenly Father give the Holy Spirit to them that ask him?"),
        Triple("John 3:16", 0, "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."),
        Triple("John 8:32", 0, "And ye shall know the truth, and the truth shall make you free."),
        Triple("John 20:23", 0, "Whose soever sins ye remit, they are remitted unto them; and whose soever sins ye retain, they are retained."),
        Triple("1 Corinthians 13:4", 0, "Charity suffereth long, and is kind; charity envieth not; charity vaunteth not itself, is not puffed up,"),
        Triple("Philippians 4:6", 0, "Be careful for nothing; but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God."),
        Triple("Hebrews 11:1", 0, "Now faith is the substance of things hoped for, the evidence of things not seen."),
        Triple("1 Peter 5:7", 0, "Casting all your care upon him; for he careth for you."),
        Triple("1 John 4:8", 0, "He that loveth not knoweth not God; for God is love.")
    ).map { (ref, _, text) ->
        val book = ref.substringBeforeLast(' ')
        val (chapter, verse) = ref.substringAfterLast(' ').split(':').map(String::toInt)
        IndexedVerse(book, books.indexOf(book), chapter, verse, text)
    }

    private val lexicon = SearchLexicon.load { name -> File("src/main/assets/search/$name").inputStream() }
    private val search = SmartSearch(BibleIndex(verses), lexicon)

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
