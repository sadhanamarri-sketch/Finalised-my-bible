package com.example.mybible.search

import com.example.mybible.model.TopicItem
import com.example.mybible.model.TopicRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nave's topics, from the real bundled topics.tsv, over [TestBible]'s verses. */
class TopicSearchTest {

    private val topics = TopicSearch(TestBible.topics, TestBible.lexicon, TestBible.index)
    private fun id(name: String) = TestBible.topics.topics.first { it.name == name }.id

    @Test
    fun theBookParses() {
        val book = TestBible.topics
        assertEquals(5319, book.topics.size)
        val care = book[id("Care")]
        assertEquals(listOf("Worldly", "Remedy for", "Instances of"), care.sections.map { it.label })
        // Anxiety only says "see Care".
        val anxiety = book[id("Anxiety")]
        assertTrue(anxiety.isRedirect)
        assertEquals(id("Care"), anxiety.links.single().topic)
        // Ranges: Genesis 1:1 alone is 257 ("75" in base 36); "75-9" runs on to 1:10.
        assertEquals(listOf(257, 257, 257, 266), TopicBook.decodeRanges("75,75-9").toList())
    }

    @Test
    fun aTopicByNameInAnyForm() {
        assertEquals("Forgiveness", topics.find("forgive").first().name)
        assertEquals("Prayer", topics.find("pray").first().name)
        assertEquals("Happiness", topics.find("happy").first().name)
        assertEquals("Love", topics.find("love").first().name)
        // love's King James wording is charity, but Nave's Charity only says "see Alms".
        assertFalse(topics.find("love").any { it.via == "Charity" })
        assertTrue(topics.find("the").isEmpty())
        assertTrue(topics.find("xyzzy").isEmpty())
    }

    @Test
    fun aTopicThroughWhatItPointsToOrTheKingJamesWording() {
        val anxiety = topics.find("anxiety").first()
        assertEquals("Care", anxiety.name)
        assertEquals("Anxiety", anxiety.via)
        // worry: King James "cares".
        val worry = topics.find("worry").first()
        assertEquals("Care", worry.name)
        assertNull(worry.via)
        assertEquals("Worldly · Remedy for · Instances of", worry.summary)
    }

    @Test
    fun aHeadingOfATopic() {
        val card = topics.find("forgive enemies").first()
        assertEquals("Forgiveness", card.name)
        assertEquals("Of enemies", card.sectionLabel)
        assertEquals(0, card.section)
    }

    @Test
    fun linesBelowATopicsHeadings() {
        // "Paul intercession": no heading of Intercession names Paul, but four lines further down do
        // (backlog 54): one card for them, with their references alone, not the topic's 236.
        val paul = topics.find("Paul intercession").single { it.name == "Intercession" }
        assertEquals("Paul", paul.sectionLabel)
        assertEquals(10, paul.referenceCount)
        assertEquals(
            "Paul for Onesimus · Paul, for God's people · For Paul, by the congregations · By Paul, of the congregations",
            paul.summary
        )
        // Opened: those lines alone, and a link to all of the topic.
        val page = topics.page(paul.topicId, paul.lines, paul.sectionLabel)
        assertEquals("Intercession › Paul", page.name)
        assertEquals(
            listOf("Paul for Onesimus", "Paul, for God's people", "For Paul, by the congregations", "By Paul, of the congregations"),
            page.items.filterIsInstance<TopicItem.Section>().map { it.label }
        )
        val all = page.items.last() as TopicItem.Link
        assertEquals("All of Intercession (236 references)", all.text)
        assertEquals(paul.topicId, all.topicId)
        // Moses: his line with what's under it (for Israel, for Miriam), and the others naming him.
        val moses = topics.find("Moses intercession").single { it.name == "Intercession" }
        assertTrue(moses.summary.split(" · ").contains("Moses"))
        val mosesLine = topics.page(moses.topicId, moses.lines, "Moses").items.filterIsInstance<TopicItem.Section>().first { it.label == "Moses" }
        assertEquals(listOf("For Israel", "For Miriam"), mosesLine.rows.filterIsInstance<TopicRow.Heading>().map { it.label })
        // Abraham: his line (Sodom, Abimelech) and his servant's, three references.
        val abraham = topics.find("Abraham intercession").single { it.name == "Intercession" }
        assertEquals("Abraham · Abraham's servant, in behalf of his master", abraham.summary)
        assertEquals(3, abraham.referenceCount)
        // A long heading is cut on its card: Paul's "Visits Samothracia and Neapolis; ... a place of prayer ...".
        val paulPrayer = topics.find("Paul prayer")
        assertTrue(paulPrayer.any { it.name == "Paul" && it.sectionLabel!!.endsWith("…") })
        // A heading's card counts its references alone: Forgiveness "Of enemies".
        val enemies = topics.find("forgive enemies").first()
        assertEquals("Of enemies", enemies.sectionLabel)
        assertTrue(enemies.referenceCount in 1 until TestBible.topics[id("Forgiveness")].referenceCount)
        assertTrue(enemies.lines.isEmpty())
    }

    @Test
    fun theTopicsPage() {
        val page = topics.page(id("Care"))
        assertEquals("Care", page.name)
        val sections = page.items.filterIsInstance<TopicItem.Section>()
        assertEquals(listOf("Worldly", "Remedy for", "Instances of"), sections.map { it.label })
        // Only the verses TestBible has are listed.
        val worldly = sections[0].rows.filterIsInstance<TopicRow.Reference>()
        assertEquals(listOf("Matthew 6:25–34", "Philippians 4:6"), worldly.map { it.label })
        assertTrue(worldly[0].preview.startsWith("Therefore I say unto you"))
        assertEquals(25, worldly[0].verse.number)
        val links = sections[0].rows.filterIsInstance<TopicRow.Link>().map { it.text }
        assertEquals(listOf("See Carnal mindedness", "See Riches", "See Worldliness"), links)
        // A subheading under Instances of, with what it lists.
        assertTrue(sections[2].rows.any { it is TopicRow.Heading && it.label == "Martha" })
        // Nave's "PHP 4:6,7" is one passage. (Its "1PE 5:6,7" starts at a verse TestBible hasn't.)
        assertEquals(listOf("Philippians 4:6–7"), sections[1].rows.filterIsInstance<TopicRow.Reference>().map { it.label })
    }
}
