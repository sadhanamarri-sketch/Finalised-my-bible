package com.example.mybible

import com.example.mybible.model.HighlightColorDef
import com.example.mybible.model.HighlightItem
import com.example.mybible.model.NoteItem
import com.example.mybible.ui.components.BIBLE_BOOKS

/**
 * Joins raw highlight records against the labeled-color defs and resolves
 * each verse's text, producing the list HighlightedVersesScreen renders: one
 * item per verse, with every color it's highlighted in (a record per color,
 * in the order they were put on), its latest date and its colors' notes.
 *
 * [getVerseText] is injected (rather than calling BibleRepository directly)
 * so this stays a plain, testable mapping function — MainViewModel is the
 * one place that knows how to actually fetch verse text.
 *
 * A highlight whose colorHex has no matching def (e.g. seeded before a
 * color was renamed/deleted) falls back to "Uncategorized" rather than
 * disappearing, matching how the color picker itself treats orphaned hexes.
 */
suspend fun buildHighlightedVerseItems(
    highlights: List<HighlightItem>,
    colorDefs: List<HighlightColorDef>,
    notes: List<NoteItem> = emptyList(),
    getVerseText: suspend (book: String, chapter: Int, verse: Int) -> String?
): List<HighlightedVerseItem> {
    val labelsByHex = colorDefs.associate { it.colorHex.lowercase() to it.label }
    val notesById = notes.associateBy { it.id }

    return highlights.groupBy { Triple(it.book, it.chapter, it.verse) }.map { (place, colors) ->
        val (book, chapter, verse) = place
        val text = getVerseText(book, chapter, verse).orEmpty()
        val tints = colors.map { HighlightTint(it.colorHex.lowercase(), labelsByHex[it.colorHex.lowercase()] ?: "Uncategorized") }
        val notes = colors.mapNotNull { highlight -> highlight.noteId?.let { notesById[it]?.text } }
        HighlightedVerseItem(
            key = "$book:$chapter:$verse",
            book = book,
            chapter = chapter,
            verse = verse,
            text = text,
            colorName = tints.first().name,
            colorHex = tints.first().hex,
            updatedAt = colors.maxOf { it.updatedAt },
            noteText = notes.joinToString(" \u00b7 ").ifEmpty { null },
            colors = tints
        )
    }.sortedWith(compareBy({ BIBLE_BOOKS.indexOf(it.book) }, { it.chapter }, { it.verse }))
}
