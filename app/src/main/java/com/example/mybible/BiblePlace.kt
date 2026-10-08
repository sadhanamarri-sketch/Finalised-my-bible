package com.example.mybible

import com.example.mybible.ui.components.BIBLE_BOOKS

/**
 * The part of the Bible a list is narrowed to: Highlighted Verses' and Search's filters.
 * ThisChapter and ThisBook follow the Reader, so they always mean wherever you're reading now.
 */
sealed interface BiblePlace {
    data object WholeBible : BiblePlace
    data object ThisChapter : BiblePlace
    data object ThisBook : BiblePlace
    data object OldTestament : BiblePlace
    data object NewTestament : BiblePlace
    data class Book(val name: String) : BiblePlace
}

private const val OLD_TESTAMENT_BOOKS = 39

fun BiblePlace.includes(book: String, chapter: Int, currentBook: String, currentChapter: Int): Boolean = when (this) {
    BiblePlace.WholeBible -> true
    BiblePlace.ThisChapter -> book == currentBook && chapter == currentChapter
    BiblePlace.ThisBook -> book == currentBook
    BiblePlace.OldTestament -> BIBLE_BOOKS.indexOf(book) in 0 until OLD_TESTAMENT_BOOKS
    BiblePlace.NewTestament -> BIBLE_BOOKS.indexOf(book) >= OLD_TESTAMENT_BOOKS
    is BiblePlace.Book -> book == name
}

fun BiblePlace.label(currentBook: String, currentChapter: Int): String = when (this) {
    BiblePlace.WholeBible -> "Whole Bible"
    BiblePlace.ThisChapter -> "$currentBook $currentChapter"
    BiblePlace.ThisBook -> currentBook
    BiblePlace.OldTestament -> "Old Testament"
    BiblePlace.NewTestament -> "New Testament"
    is BiblePlace.Book -> name
}
