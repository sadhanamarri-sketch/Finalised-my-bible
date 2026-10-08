package com.example.mybible

import com.example.mybible.model.CompletedVerseItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StudiedProgressTest {

    private fun studied(book: String, chapter: Int, verses: IntRange) =
        verses.map { CompletedVerseItem(book, chapter, it, 0L) }

    // Ruth's chapters: 22, 23, 18 and 22 verses.
    private val ruth = listOf(22, 23, 18, 22)

    @Test
    fun percentNeverRoundsUpToDoneOrDownToNothing() {
        assertEquals("0%", StudiedProgress.percent(0, 23145))
        assertEquals("<0.1%", StudiedProgress.percent(1, 23145))
        assertEquals("<0.1%", StudiedProgress.percent(23, 23145))
        assertEquals("0.1%", StudiedProgress.percent(24, 23145))
        assertEquals("0.3%", StudiedProgress.percent(3, 1000))
        assertEquals("0.5%", StudiedProgress.percent(1, 200))
        assertEquals("0.8%", StudiedProgress.percent(200, 23145))
        assertEquals("0.9%", StudiedProgress.percent(229, 23145))
        assertEquals("1%", StudiedProgress.percent(232, 23145))
        assertEquals("12%", StudiedProgress.percent(12, 100))
        assertEquals("99%", StudiedProgress.percent(23144, 23145))
        assertEquals("100%", StudiedProgress.percent(23145, 23145))
    }

    @Test
    fun percentIsEmptyUntilTheTotalIsKnown() {
        assertEquals("", StudiedProgress.percent(5, 0))
    }

    @Test
    fun countGroupsThousands() {
        assertEquals("7", StudiedProgress.count(7))
        assertEquals("7,957", StudiedProgress.count(7957))
        assertEquals("23,145", StudiedProgress.count(23145))
    }

    @Test
    fun chaptersAreDonePartlyOrNotStarted() {
        val marks = studied("Ruth", 1, 1..22) + studied("Ruth", 2, 1..5) + studied("Ruth", 4, 22..22)
        val chapters = StudiedProgress.chapters(marks, ruth)

        assertEquals(listOf(1, 2, 3, 4), chapters.map { it.chapter })
        assertEquals(
            listOf(ChapterState.DONE, ChapterState.PARTLY, ChapterState.NOT_STARTED, ChapterState.PARTLY),
            chapters.map { it.state }
        )
        assertEquals(listOf(22, 5, 0, 1), chapters.map { it.studied })
        assertEquals(5f / 23, chapters[1].fraction, 0.0001f)
    }

    @Test
    fun aVerseOutsideTheChapterOrBookIsNotCounted() {
        val marks = studied("Ruth", 3, 19..19) + studied("Ruth", 5, 1..1) + studied("Ruth", 1, 0..0)
        val chapters = StudiedProgress.chapters(marks, ruth)
        assertEquals(listOf(0, 0, 0, 0), chapters.map { it.studied })
    }

    @Test
    fun aChapterWithItsCountNotKnownYetReadsAsPartlyDone() {
        val chapters = StudiedProgress.chapters(studied("Ruth", 2, 1..23), listOf(0, 0, 0, 0))
        assertEquals(ChapterState.PARTLY, chapters[1].state)
        assertEquals(ChapterState.NOT_STARTED, chapters[0].state)
    }

    @Test
    fun bookAddsUpItsChapters() {
        val marks = studied("Ruth", 1, 1..22) + studied("Ruth", 2, 1..5) + studied("Ruth", 4, 1..22)
        assertEquals(BookProgress(studied = 49, verses = 85, chaptersDone = 2, chapters = 4), StudiedProgress.book(marks, ruth))
        assertEquals("57%", StudiedProgress.percent(49, 85))
    }
}
