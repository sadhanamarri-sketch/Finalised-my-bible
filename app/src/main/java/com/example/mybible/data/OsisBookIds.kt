package com.example.mybible.data

import com.example.mybible.ui.components.BIBLE_BOOKS

/**
 * Standard OSIS book abbreviations, in the same order as [BIBLE_BOOKS], so a
 * single OSIS XML file can be parsed into every chapter of the KJV instead of
 * fetching one chapter at a time. Ported verbatim from the Capacitor app's
 * `OSIS_BOOK_IDS` (index-for-index), which is already proven against the
 * exact XML file [KjvImporter] downloads.
 */
val OSIS_BOOK_IDS = listOf(
    "Gen", "Exod", "Lev", "Num", "Deut", "Josh", "Judg", "Ruth", "1Sam", "2Sam",
    "1Kgs", "2Kgs", "1Chr", "2Chr", "Ezra", "Neh", "Esth", "Job", "Ps", "Prov",
    "Eccl", "Song", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Joel", "Amos",
    "Obad", "Jonah", "Mic", "Nah", "Hab", "Zeph", "Hag", "Zech", "Mal",
    "Matt", "Mark", "Luke", "John", "Acts", "Rom", "1Cor", "2Cor", "Gal", "Eph",
    "Phil", "Col", "1Thess", "2Thess", "1Tim", "2Tim", "Titus", "Phlm", "Heb", "Jas",
    "1Pet", "2Pet", "1John", "2John", "3John", "Jude", "Rev"
)

/** OSIS book id (e.g. "Gen", "Matt") -> full book name (e.g. "Genesis", "Matthew"). */
val OSIS_ID_TO_BOOK: Map<String, String> =
    BIBLE_BOOKS.indices.associate { i -> OSIS_BOOK_IDS[i] to BIBLE_BOOKS[i] }
