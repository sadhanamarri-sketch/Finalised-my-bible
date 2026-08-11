package com.example.mybible

import com.example.mybible.model.HighlightColorDef
import com.example.mybible.model.HighlightItem
import com.example.mybible.ui.components.BIBLE_BOOKS

/**
 * Joins raw highlight records against the labeled-color defs and resolves
 * each verse's text, producing the list HighlightedVersesScreen renders.
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
    getVerseText: suspend (book: String, chapter: Int, verse: Int) -> String?
): List<HighlightedVerseItem> {
    val labelsByHex = colorDefs.associate { it.colorHex.lowercase() to it.label }

    return highlights.map { highlight ->
        val text = getVerseText(highlight.book, highlight.chapter, highlight.verse).orEmpty()
        HighlightedVerseItem(
            key = "${highlight.book}:${highlight.chapter}:${highlight.verse}",
            book = highlight.book,
            chapter = highlight.chapter,
            verse = highlight.verse,
            text = text,
            colorName = labelsByHex[highlight.colorHex.lowercase()] ?: "Uncategorized"
        )
    }.sortedWith(compareBy({ BIBLE_BOOKS.indexOf(it.book) }, { it.chapter }, { it.verse }))
}
