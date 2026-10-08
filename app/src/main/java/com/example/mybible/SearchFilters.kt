package com.example.mybible

import com.example.mybible.model.SearchOutcome
import com.example.mybible.ui.components.BIBLE_BOOKS

/**
 * A search's results in one part of the Bible: its verses there, and its chips counting what they
 * bring there (a chip bringing nothing there is left out, unless it's switched off: that's how to
 * switch it back on). Its topics and Greek and Hebrew words stay: they're about the words searched
 * for, not where they are.
 */
fun SearchOutcome.within(place: BiblePlace, currentBook: String = "", currentChapter: Int = 0): SearchOutcome {
    if (place == BiblePlace.WholeBible) return this
    fun inPlace(book: String, chapter: Int = 0) = place.includes(book, chapter, currentBook, currentChapter)
    val exact = hits.take(exactCount).filter { inPlace(it.verse.book, it.verse.chapter) }
    val related = hits.drop(exactCount).filter { inPlace(it.verse.book, it.verse.chapter) }
    val counted = sources.map { source ->
        source.copy(count = source.countByBook.entries.sumOf { (book, n) -> if (inPlace(book)) n else 0 })
    }
    return copy(hits = exact + related, exactCount = exact.size, sources = counted.filter { it.count > 0 || !it.enabled })
}

/** The books a search found verses in, in Bible order, with how many each: the filter's book list. */
fun SearchOutcome.versesByBook(): List<Pair<String, Int>> =
    hits.groupingBy { it.verse.book }.eachCount().toList().sortedBy { BIBLE_BOOKS.indexOf(it.first) }
