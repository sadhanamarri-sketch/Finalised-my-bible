package com.example.mybible.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A note as plain text, to share or keep: its title, its date and tags, each of its verses (or
 * passages) with their words, then its text. [bookIndex] gives a book's place in the Bible.
 */
object NoteSharing {
    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

    fun asText(note: NoteItem, bookIndex: (String) -> Int): String = buildString {
        if (note.title.isNotBlank()) appendLine(note.title.trim())
        val details = listOfNotNull(
            runCatching { LocalDate.parse(note.noteDate).format(DATE) }.getOrNull(),
            note.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
        if (details.isNotEmpty()) appendLine(details.joinToString(" · "))
        groupRefs(note.refs, bookIndex).forEach { range ->
            val words = note.refs.filter { range.covers(it) }.sortedBy { it.verse }
                .map { it.verseText.trim() }.filter { it.isNotEmpty() }
            appendLine()
            append(range.label)
            if (words.isNotEmpty()) append(" — ").append(words.joinToString(" "))
            appendLine()
        }
        val body = note.text.trim()
        if (body.isNotEmpty()) {
            appendLine()
            appendLine(body)
        }
    }.trim()

    /** [notes] as one document to read, each after a rule. */
    fun allAsText(notes: List<NoteItem>, today: LocalDate, bookIndex: (String) -> Int): String = buildString {
        appendLine("Notes")
        appendLine("${notes.size} ${if (notes.size == 1) "note" else "notes"}, ${today.format(DATE)}")
        notes.forEach { note ->
            appendLine()
            appendLine("— — —")
            appendLine()
            appendLine(asText(note, bookIndex))
        }
    }.trimEnd() + "\n"
}
