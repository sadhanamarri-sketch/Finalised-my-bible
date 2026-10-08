package com.example.mybible

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HighlightFiltersTest {

    private val zone = ZoneId.of("UTC")
    private val now = LocalDateTime.of(2026, 10, 8, 15, 0).atZone(zone).toInstant().toEpochMilli()
    private fun daysAgo(days: Long) = now - days * 24 * 60 * 60 * 1000

    private val promise = "#f1b1b1"
    private val prayer = "#b1d1f1"
    private val keyVerse = "#f1f1b1"

    private fun item(
        book: String, chapter: Int, verse: Int, colorHex: String, label: String,
        text: String = "", note: String? = null, updatedAt: Long = now
    ) = HighlightedVerseItem(
        key = "$book:$chapter:$verse", book = book, chapter = chapter, verse = verse, text = text,
        colorName = label, colorHex = colorHex, updatedAt = updatedAt, noteText = note
    )

    private val psalm = item("Psalms", 23, 1, promise, "Promise", "The LORD is my shepherd; I shall not want.", updatedAt = daysAgo(3))
    private val isaiah = item("Isaiah", 41, 10, promise, "Promise", "Fear thou not; for I am with thee", updatedAt = daysAgo(40))
    private val romans826 = item("Romans", 8, 26, prayer, "Prayer", "the Spirit itself maketh intercession", "Pray with no words", daysAgo(0))
    private val romans828 = item("Romans", 8, 28, promise, "Promise", "all things work together for good", updatedAt = daysAgo(1))
    private val romans122 = item("Romans", 12, 2, keyVerse, "Key Verse", "be not conformed to this world", updatedAt = daysAgo(400))
    private val peter = item("1 Peter", 5, 7, prayer, "Prayer", "Casting all your care upon him", updatedAt = daysAgo(12))
    private val all = listOf(psalm, isaiah, romans826, romans828, romans122, peter)

    private fun List<HighlightedVerseItem>.keys() = map { it.key }

    private fun shown(place: BiblePlace = BiblePlace.WholeBible, colors: Set<String> = emptySet(), query: String = "") =
        all.filtered(place, colors, query, currentBook = "Romans", currentChapter = 8).keys()

    @Test
    fun placesFollowTheReaderAndSplitTheTestaments() {
        assertEquals(all.keys(), shown())
        assertEquals(listOf(romans826.key, romans828.key), shown(BiblePlace.ThisChapter))
        assertEquals(listOf(romans826.key, romans828.key, romans122.key), shown(BiblePlace.ThisBook))
        assertEquals(listOf(psalm.key, isaiah.key), shown(BiblePlace.OldTestament))
        assertEquals(listOf(romans826.key, romans828.key, romans122.key, peter.key), shown(BiblePlace.NewTestament))
        assertEquals(listOf(peter.key), shown(BiblePlace.Book("1 Peter")))
    }

    @Test
    fun severalColorsCombineAndNoneMeansEvery() {
        assertEquals(listOf(romans826.key, peter.key), shown(colors = setOf(prayer)))
        assertEquals(
            listOf(psalm.key, isaiah.key, romans826.key, romans828.key, peter.key),
            shown(colors = setOf(prayer, promise))
        )
        assertEquals(listOf(romans826.key, romans828.key), shown(BiblePlace.ThisChapter, setOf(prayer, promise)))
    }

    @Test
    fun searchNeedsEveryWordInTheVerseReferenceNoteOrColorName() {
        assertEquals(listOf(psalm.key), shown(query = "shepherd"))
        assertEquals(listOf(romans826.key), shown(query = "  NO   words "))
        assertEquals(listOf(romans826.key, romans828.key), shown(query = "romans 8:"))
        assertEquals(listOf(romans826.key, peter.key), shown(query = "prayer"))
        assertEquals(emptyList<String>(), shown(query = "shepherd prayer"))
        assertEquals(listOf(isaiah.key), shown(BiblePlace.OldTestament, setOf(promise), "fear"))
    }

    @Test
    fun newestFirstGroupsByHowLongAgo() {
        val groups = groupHighlights(all, newestFirst = true, now = now, zone = zone)
        assertEquals(
            listOf("Today", "Yesterday", "Previous 7 days", "Previous 30 days", "August", "September 2025"),
            groups.map { it.title }
        )
        assertEquals(
            listOf(romans826.key, romans828.key, psalm.key, peter.key, isaiah.key, romans122.key),
            groups.flatMap { it.items }.keys()
        )
    }

    @Test
    fun bibleOrderGroupsByBook() {
        val groups = groupHighlights(all.shuffled(java.util.Random(7)), newestFirst = false, now = now, zone = zone)
        assertEquals(listOf("Psalms", "Isaiah", "Romans", "1 Peter"), groups.map { it.title })
        assertEquals(listOf(romans826.key, romans828.key, romans122.key), groups[2].items.keys())
    }
}
