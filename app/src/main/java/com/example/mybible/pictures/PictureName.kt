package com.example.mybible.pictures

import com.example.mybible.ui.components.BIBLE_BOOKS

/**
 * Where a picture in the pictures folder shows, from its name: a verse or a run of verses in a chapter
 * ("Genesis 1.3-5", "John 3.16"), a chapter or a run of chapters ("Psalms 23", "Genesis 6-9"), or a whole
 * book ("Psalms"). Phones don't allow ":" in a name, so a verse follows its chapter after a dot. Another
 * picture for the same place ends " (2)", " (3)"… and they take turns. One named "Any…" ("Any sunrise")
 * is for any card ([forAnyCard]); a picture with any other name isn't one of these.
 */
data class PictureName(
    val book: String,
    /** The chapters it's for; null for the whole book. */
    val chapters: IntRange?,
    /** The verses it's for, in its one chapter; null for the whole chapter (or chapters). */
    val verses: IntRange?,
    val variant: Int = 1
) {
    /** How closely it fits: a verse's own picture comes before its chapter's, and that before its book's. */
    val rank: Int
        get() = when {
            verses != null -> 0
            chapters != null -> 1
            else -> 2
        }

    fun covers(book: String, chapter: Int, verse: Int): Boolean =
        book == this.book && (chapters == null || chapter in chapters) && (verses == null || verse in verses)

    /** The place it's for, without the variant: pictures for the same place share it. */
    val place: String
        get() = buildString {
            append(book)
            if (chapters != null) {
                append(' ').append(chapters.first)
                if (chapters.last != chapters.first) append('-').append(chapters.last)
            }
            if (verses != null) {
                append('.').append(verses.first)
                if (verses.last != verses.first) append('-').append(verses.last)
            }
        }

    companion object {
        // A book's name, then maybe chapters ("23", "6-9"), then maybe verses after a dot (".3", ".3-5"),
        // then maybe a variant ("(2)").
        private val SHAPE = Regex("""^(.*?[A-Za-z])(?:\s+(\d+)(?:\s*-\s*(\d+))?(?:\.(\d+)(?:\s*-\s*(\d+))?)?)?(?:\s*\((\d+)\))?$""")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
        private val ALIASES = mapOf("psalm" to "Psalms", "song of songs" to "Song of Solomon")

        /** What a file in the folder is a picture for, from its name; null when the name says no place in the Bible. */
        fun parse(fileName: String): PictureName? {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val base = (if (extension in IMAGE_EXTENSIONS) fileName.substringBeforeLast('.') else fileName).trim()
            val match = SHAPE.matchEntire(base) ?: return null
            val (bookText, fromChapter, toChapter, fromVerse, toVerse, variant) = match.destructured
            val book = bookOf(bookText.trim()) ?: return null
            val chapters = fromChapter.toIntOrNull()?.let { from -> from..(toChapter.toIntOrNull() ?: from) }
            val verses = fromVerse.toIntOrNull()?.let { from -> from..(toVerse.toIntOrNull() ?: from) }
            // Verses belong to one chapter.
            if (verses != null && (chapters == null || chapters.first != chapters.last)) return null
            if (chapters != null && chapters.isEmpty() || verses != null && verses.isEmpty()) return null
            return PictureName(book, chapters, verses, variant.toIntOrNull() ?: 1)
        }

        /**
         * Whether a picture is for any card, now and then: its name starts with "Any" ("Any sunrise.jpg",
         * "Any 2.png"). Other names that say no place are other pictures, left alone (the folder may be
         * one the phone saves all sorts to).
         */
        fun forAnyCard(fileName: String): Boolean = ANY.containsMatchIn(fileName)

        private val ANY = Regex("^any(?![a-z])", RegexOption.IGNORE_CASE)

        /** The name a scene's picture is saved under: "Genesis 1.3-5", "Genesis 2.4". */
        fun of(book: String, chapter: Int, first: Int, last: Int): String =
            "$book $chapter.$first" + if (last != first) "-$last" else ""

        private fun bookOf(text: String): String? {
            val lower = text.lowercase().replace(Regex("\\s+"), " ")
            return BIBLE_BOOKS.firstOrNull { it.lowercase() == lower } ?: ALIASES[lower]
        }
    }
}
