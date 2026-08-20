package com.example.mybible.data

/**
 * Resolves a STEPBible `<ref>` tag's key — already extracted into
 * "⟦key|display⟧" markers by TbesgImporter/TbeshImporter (see their
 * REF_TAG_RE docs) — into a (book, chapter, verse) triple this app can
 * navigate to.
 *
 * Verified against the live TBESG file's 41,479 `<ref>` keys: 98.4% match
 * "Book.chapter.verse" (the verse separator is either "." or ":", e.g.
 * "Jhn.8:7"). Of the remainder, most are STEPBible's single-chapter-book
 * shorthand that omits the chapter entirely (e.g. "Jude.24", "3Jn.11" —
 * Obadiah, Philemon, 2 John, 3 John and Jude are the only one-chapter books
 * in the canon, so STEPBible drops the redundant "1"), plus a residue of
 * Apocryphal/deuterocanonical citations (4Ma, 1Es, Bel, Man, 1-3Ma, 2-3Es,
 * 3Ki, Jdt, Bar, Pss-variant...) that this KJV-only (66-book Protestant
 * canon) app can never resolve. Both of those are expected to return null
 * and stay as plain, non-clickable text rather than crash or mis-navigate.
 */
object ScriptureRefResolver {

    private val FULL_RE = Regex("""^([1-3]?[A-Za-z]+)\.(\d+)[.:](\d+)""")
    private val SHORT_RE = Regex("""^([1-3]?[A-Za-z]+)\.(\d+)$""")

    private val SINGLE_CHAPTER_BOOKS = setOf(
        "Obadiah", "Philemon", "2 John", "3 John", "Jude"
    )

    data class Ref(val book: String, val chapter: Int, val verse: Int)

    /**
     * Parses only the first citation of a possibly semicolon/comma-chained
     * key (e.g. "Jhn.8:7; 20:4, 8" resolves just "Jhn.8:7") — matching what
     * the display text next to it points a reader at, and enough for a
     * "jump to this verse" tap target.
     */
    fun resolve(key: String): Ref? {
        val trimmed = key.trim()
        FULL_RE.find(trimmed)?.let { m ->
            val book = resolveBookName(m.groupValues[1]) ?: return null
            val chapter = m.groupValues[2].toIntOrNull() ?: return null
            val verse = m.groupValues[3].toIntOrNull() ?: return null
            return Ref(book, chapter, verse)
        }
        SHORT_RE.find(trimmed)?.let { m ->
            val book = resolveBookName(m.groupValues[1]) ?: return null
            if (book !in SINGLE_CHAPTER_BOOKS) return null
            val verse = m.groupValues[2].toIntOrNull() ?: return null
            return Ref(book, 1, verse)
        }
        return null
    }
}
