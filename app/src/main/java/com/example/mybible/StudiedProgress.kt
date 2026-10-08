package com.example.mybible

import com.example.mybible.model.CompletedVerseItem
import java.util.Locale

/** How far through a chapter: none of it studied, some, or every verse. */
enum class ChapterState { NOT_STARTED, PARTLY, DONE }

/** One chapter's studied verses, out of its verses. */
data class ChapterProgress(val chapter: Int, val studied: Int, val verses: Int) {
    val state: ChapterState
        get() = when {
            studied == 0 -> ChapterState.NOT_STARTED
            verses in 1..studied -> ChapterState.DONE
            else -> ChapterState.PARTLY
        }

    /** The share studied, 0 to 1. */
    val fraction: Float get() = if (verses <= 0) 0f else (studied.toFloat() / verses).coerceAtMost(1f)
}

/** A book's studied verses out of its verses, and its chapters with every verse studied. */
data class BookProgress(val studied: Int, val verses: Int, val chaptersDone: Int, val chapters: Int)

/** The Studied page's progress: by chapter, by book, and as a share people can read. */
object StudiedProgress {

    /**
     * Each chapter of a book with how many of its verses are studied. [studied]: the book's studied
     * verses; [verseCounts]: its chapters' verse counts, Genesis's being 31, 25, 24… A count of 0
     * is one not known yet: that chapter's studied verses still count, so it reads as partly done.
     */
    fun chapters(studied: Collection<CompletedVerseItem>, verseCounts: List<Int>): List<ChapterProgress> {
        val count = IntArray(verseCounts.size)
        for (item in studied) {
            val i = item.chapter - 1
            if (i in count.indices && item.verse >= 1 && (verseCounts[i] == 0 || item.verse <= verseCounts[i])) count[i]++
        }
        return verseCounts.mapIndexed { i, verses -> ChapterProgress(i + 1, count[i], verses) }
    }

    fun book(studied: Collection<CompletedVerseItem>, verseCounts: List<Int>): BookProgress {
        val chapters = chapters(studied, verseCounts)
        return BookProgress(
            studied = chapters.sumOf { it.studied },
            verses = verseCounts.sum(),
            chaptersDone = chapters.count { it.state == ChapterState.DONE },
            chapters = verseCounts.size
        )
    }

    /**
     * [part] of [whole] as the page says it: "0%", "<0.1%", "0.8%", "12%"; "100%" only when it's
     * all, so a share never rounds up to done. Empty while [whole] isn't known.
     */
    fun percent(part: Int, whole: Int): String {
        if (whole <= 0) return ""
        if (part <= 0) return "0%"
        if (part >= whole) return "100%"
        // In tenths of a percent, rounded down: whole numbers, so 3 of 1,000 is 0.3% and not 0.2%.
        val tenths = part * 1000L / whole
        return when {
            tenths < 1 -> "<0.1%"
            tenths < 10 -> "0.$tenths%"
            else -> "${tenths / 10}%"
        }
    }

    /** 23145 → "23,145". */
    fun count(n: Int): String = String.format(Locale.US, "%,d", n)
}
