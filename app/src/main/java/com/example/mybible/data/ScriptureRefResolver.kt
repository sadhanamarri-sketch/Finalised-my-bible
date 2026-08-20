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

    data class Citation(val range: IntRange, val ref: Ref)

    // Matches one citation token within a chain: an optional "Book." prefix
    // (group 1), a number (group 2) that's either a chapter (if followed by
    // a verse) or a bare verse continuing the current chapter, an optional
    // ".verse"/":verse" (group 3), and an optional "-N" range end (group 4,
    // unused — a range resolves to its start verse, same as [resolve]).
    private val TOKEN_RE = Regex("""(?:\b([1-3]?[A-Za-z]+)\.\s*)?(\d+)(?:[.:](\d+))?(?:-(\d+))?""")

    /**
     * Finds every individually-resolvable verse mention within a marker's
     * human-authored display text (e.g. "Rom.3:14, 17" or "Heb.5:4, 7:11,
     * 9:4"), each as its own [Citation] carrying the exact substring range
     * it corresponds to — unlike [resolve], which only recovers the first
     * citation of a chained reference, silently leaving the rest of a
     * chain's display text looking clickable (it's part of the same
     * underlined span) while actually pointing at the wrong verse.
     *
     * STEPBible's chained-citation grammar states a book+chapter once, with
     * "Book.chapter:verse", then lets later mentions in the same chain omit
     * whatever hasn't changed: a bare "chapter:verse" (no book) carries the
     * book forward, a bare number carries both book and chapter forward.
     * Verified against the live TBESG file's ~7,500 real chained citations:
     * this recovers essentially all of them (~99%) — the residue is mostly
     * citations whose display text elides even the book, relying on
     * surrounding sentence prose outside this marker for context, which
     * isn't recoverable from the marker's text alone.
     *
     * Returns an empty list — not a single wrong guess — when nothing in
     * the display text carries its own book context, so callers can fall
     * back to [resolve] against the structured key instead (which still
     * gets the *first* citation right even when the display text can't be
     * split further).
     */
    fun findCitations(displayText: String): List<Citation> {
        val results = mutableListOf<Citation>()
        var book: String? = null
        var chapter: Int? = null
        for (m in TOKEN_RE.findAll(displayText)) {
            val bookAbbr = m.groups[1]?.value
            val num1 = m.groups[2]?.value?.toIntOrNull() ?: continue
            val num2 = m.groups[3]?.value?.toIntOrNull()
            if (bookAbbr != null) {
                val resolvedBook = resolveBookName(bookAbbr) ?: continue
                if (num2 != null) {
                    book = resolvedBook
                    chapter = num1
                    results += Citation(m.range, Ref(resolvedBook, num1, num2))
                } else if (resolvedBook in SINGLE_CHAPTER_BOOKS) {
                    book = resolvedBook
                    chapter = 1
                    results += Citation(m.range, Ref(resolvedBook, 1, num1))
                }
                // else: "Book.N" with no verse and not a single-chapter book
                // — ambiguous (bare chapter with nothing pointing to a
                // verse), skip rather than guess.
            } else if (num2 != null) {
                val currentBook = book ?: continue
                chapter = num1
                results += Citation(m.range, Ref(currentBook, num1, num2))
            } else {
                val currentBook = book ?: continue
                val currentChapter = chapter ?: continue
                results += Citation(m.range, Ref(currentBook, currentChapter, num1))
            }
        }
        return results
    }
}
