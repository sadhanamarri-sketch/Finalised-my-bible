package com.example.mybible

import com.example.mybible.ui.components.BIBLE_BOOKS
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Where the Highlighted Verses page looks. ThisChapter and ThisBook follow
 * the Reader, so they always mean wherever you're reading now.
 */
sealed interface HighlightPlace {
    data object WholeBible : HighlightPlace
    data object ThisChapter : HighlightPlace
    data object ThisBook : HighlightPlace
    data object OldTestament : HighlightPlace
    data object NewTestament : HighlightPlace
    data class Book(val name: String) : HighlightPlace
}

/**
 * What the Highlighted Verses page is narrowed to. Its order (newest first
 * or Bible order) is a saved preference instead, kept apart from this.
 */
data class HighlightsFilter(
    val place: HighlightPlace = HighlightPlace.WholeBible,
    // By color, not label: a label can be renamed while the filter is on.
    // Lowercase, like HighlightedVerseItem.colorHex.
    val colorHexes: Set<String> = emptySet(),
    val query: String = "",
    val searchOpen: Boolean = false
) {
    /** A place or a color is picked — what the filter button's dot and the summary line show. */
    val narrowed: Boolean get() = place != HighlightPlace.WholeBible || colorHexes.isNotEmpty()
}

private const val OLD_TESTAMENT_BOOKS = 39

fun HighlightPlace.includes(item: HighlightedVerseItem, currentBook: String, currentChapter: Int): Boolean = when (this) {
    HighlightPlace.WholeBible -> true
    HighlightPlace.ThisChapter -> item.book == currentBook && item.chapter == currentChapter
    HighlightPlace.ThisBook -> item.book == currentBook
    HighlightPlace.OldTestament -> BIBLE_BOOKS.indexOf(item.book) in 0 until OLD_TESTAMENT_BOOKS
    HighlightPlace.NewTestament -> BIBLE_BOOKS.indexOf(item.book) >= OLD_TESTAMENT_BOOKS
    is HighlightPlace.Book -> item.book == name
}

private val whitespace = Regex("\\s+")

private fun HighlightedVerseItem.hasAll(words: List<String>): Boolean {
    if (words.isEmpty()) return true
    val searchable = "$book $chapter:$verse $text ${noteText.orEmpty()} $colorName".lowercase()
    return words.all { it in searchable }
}

/**
 * The highlights in [place] with one of [colorHexes] (any color when empty)
 * that have every word of [query] somewhere in the verse, its reference, its
 * note or its color's name. The filter sheet also calls this with another
 * place or color in place of the current one, to count what each choice
 * would show.
 */
fun List<HighlightedVerseItem>.filtered(
    place: HighlightPlace,
    colorHexes: Set<String>,
    query: String,
    currentBook: String,
    currentChapter: Int
): List<HighlightedVerseItem> {
    val words = query.lowercase().split(whitespace).filter { it.isNotEmpty() }
    return filter {
        place.includes(it, currentBook, currentChapter) &&
            (colorHexes.isEmpty() || it.colorHex in colorHexes) &&
            it.hasAll(words)
    }
}

data class HighlightGroup(val title: String, val items: List<HighlightedVerseItem>)

private val bibleOrder = compareBy<HighlightedVerseItem>({ BIBLE_BOOKS.indexOf(it.book) }, { it.chapter }, { it.verse })

/**
 * Newest first: Today, Yesterday, Previous 7 days, Previous 30 days, then a
 * group per month ("August", or "August 2025" from an earlier year).
 * Bible order: a group per book.
 */
fun groupHighlights(
    items: List<HighlightedVerseItem>,
    newestFirst: Boolean,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): List<HighlightGroup> {
    if (!newestFirst) {
        return items.sortedWith(bibleOrder).groupBy { it.book }.map { (book, list) -> HighlightGroup(book, list) }
    }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return items
        .sortedWith(compareByDescending<HighlightedVerseItem> { it.updatedAt }.then(bibleOrder))
        .groupBy { dateGroup(Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate(), today) }
        .map { (title, list) -> HighlightGroup(title, list) }
}

private fun dateGroup(day: LocalDate, today: LocalDate): String {
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    // English like the rest of the app's labels (Notes and Studied use Locale.US too).
    val month = day.month.getDisplayName(TextStyle.FULL, Locale.US)
    return when {
        // A highlight dated after today (the clock was changed) still counts as today's.
        daysAgo <= 0 -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7 -> "Previous 7 days"
        daysAgo < 30 -> "Previous 30 days"
        day.year == today.year -> month
        else -> "$month ${day.year}"
    }
}
