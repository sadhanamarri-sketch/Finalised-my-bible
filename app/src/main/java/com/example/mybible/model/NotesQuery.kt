package com.example.mybible.model

import com.example.mybible.data.resolveBookName
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** How the notes list is ordered. */
enum class NoteSort(val label: String) {
    NEWEST("Newest"),
    EDITED("Recently edited"),
    BIBLE("Bible order")
}

/** Which of the notes' dates the list shows. */
sealed interface NoteDates {
    data object Any : NoteDates
    data object ThisWeek : NoteDates
    data object ThisMonth : NoteDates
    data class Between(val from: LocalDate, val to: LocalDate) : NoteDates
}

/** What the notes list shows: its search, filters and order, kept while the app is open. */
data class NotesQuery(
    val text: String = "",
    val tags: List<String> = emptyList(),
    // All the chosen tags on a note, rather than any one of them.
    val allTags: Boolean = false,
    val dates: NoteDates = NoteDates.Any,
    val sort: NoteSort = NoteSort.NEWEST,
    // Only the notes marked special.
    val special: Boolean = false
) {
    val filtering: Boolean get() = tags.isNotEmpty() || dates != NoteDates.Any || special

    /** The same order and search, every filter off. */
    fun unfiltered(): NotesQuery = copy(tags = emptyList(), allTags = false, dates = NoteDates.Any, special = false)
}

/** A line of the notes list: a note, or in Bible order the book the notes under it are on. */
sealed interface NotesRow {
    data class Book(val name: String, val count: Int) : NotesRow
    data class Note(val note: NoteItem) : NotesRow
}

object NotesFinder {

    /**
     * The notes [q] lets through, as the list shows them: pinned ones first, then in [q]'s order;
     * in Bible order, under the book of each note's first verse ("John · 3 notes"), notes without
     * verses last. [bookIndex] gives a book's place in the Bible.
     */
    fun rows(notes: List<NoteItem>, q: NotesQuery, today: LocalDate, bookIndex: (String) -> Int): List<NotesRow> {
        val found = notes.filter {
            (!q.special || it.special) && matchesText(it, q.text) && matchesTags(it, q.tags, q.allTags) && matchesDates(it, q.dates, today)
        }
        val (pinned, rest) = found.partition { it.pinned }
        val out = ArrayList<NotesRow>(found.size + 8)
        sorted(pinned, q.sort, bookIndex).mapTo(out) { NotesRow.Note(it) }
        if (q.sort != NoteSort.BIBLE) {
            sorted(rest, q.sort, bookIndex).mapTo(out) { NotesRow.Note(it) }
        } else {
            sorted(rest, q.sort, bookIndex).groupBy { firstRef(it, bookIndex)?.book }.forEach { (book, group) ->
                out += NotesRow.Book(book ?: NO_VERSES, group.size)
                group.mapTo(out) { NotesRow.Note(it) }
            }
        }
        return out
    }

    const val NO_VERSES = "No verses"

    fun sorted(notes: List<NoteItem>, sort: NoteSort, bookIndex: (String) -> Int): List<NoteItem> = when (sort) {
        NoteSort.NEWEST -> notes.sortedByDescending { it.createdAt }
        NoteSort.EDITED -> notes.sortedByDescending { it.updatedAt }
        NoteSort.BIBLE -> notes.sortedWith(
            compareBy<NoteItem>(
                { n -> firstRef(n, bookIndex)?.let { bookIndex(it.book) } ?: Int.MAX_VALUE },
                { n -> firstRef(n, bookIndex)?.chapter ?: 0 },
                { n -> firstRef(n, bookIndex)?.verse ?: 0 }
            ).thenByDescending { it.createdAt }
        )
    }

    /** The note's first verse in Bible order. */
    fun firstRef(note: NoteItem, bookIndex: (String) -> Int): NoteReference? =
        note.refs.minWithOrNull(compareBy<NoteReference>({ bookIndex(it.book) }, { it.chapter }, { it.verse }))

    /** [query] in the note's title or text, one of its tags, or one of its verses ("John", "John 15", "Jn 15:5"). */
    fun matchesText(note: NoteItem, query: String): Boolean {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.isEmpty()) return true
        return note.title.contains(q, ignoreCase = true) ||
            note.text.contains(q, ignoreCase = true) ||
            note.tags.any { it.contains(q, ignoreCase = true) } ||
            note.refs.any { refMatches(it, q) }
    }

    // A book, then a chapter, then perhaps a verse or verses: "1 John 3", "John 3:16", "Ps 23:1-3".
    private val REFERENCE = Regex("""^(.*?\D)\s*(\d+)(?::(\d+)(?:\s*[-–]\s*(\d+))?)?$""")

    fun refMatches(ref: NoteReference, q: String): Boolean {
        val m = REFERENCE.matchEntire(q) ?: return bookMatches(ref.book, q)
        val (bookPart, chapter, verse, verseTo) = m.destructured
        if (!bookMatches(ref.book, bookPart.trim()) || ref.chapter != chapter.toInt()) return false
        // A note on the whole chapter is on each of its verses.
        val from = verse.toIntOrNull() ?: return true
        if (ref.verse == 0) return true
        return ref.verse in from..(verseTo.toIntOrNull() ?: from)
    }

    private fun bookMatches(book: String, part: String): Boolean =
        part.isNotEmpty() && (book.startsWith(part, ignoreCase = true) || resolveBookName(part) == book)

    fun matchesTags(note: NoteItem, tags: List<String>, all: Boolean): Boolean {
        if (tags.isEmpty()) return true
        fun has(tag: String) = note.tags.any { it.equals(tag, ignoreCase = true) }
        return if (all) tags.all(::has) else tags.any(::has)
    }

    fun matchesDates(note: NoteItem, dates: NoteDates, today: LocalDate): Boolean {
        if (dates == NoteDates.Any) return true
        val day = dateOf(note)
        return when (dates) {
            NoteDates.Any -> true
            NoteDates.ThisWeek -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                !day.isBefore(monday) && !day.isAfter(monday.plusDays(6))
            }
            NoteDates.ThisMonth -> day.year == today.year && day.month == today.month
            is NoteDates.Between -> !day.isBefore(dates.from) && !day.isAfter(dates.to)
        }
    }

    /** The note's date, or the day it was written if it has none. */
    fun dateOf(note: NoteItem): LocalDate =
        runCatching { LocalDate.parse(note.noteDate) }.getOrElse {
            Instant.ofEpochMilli(note.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
}
