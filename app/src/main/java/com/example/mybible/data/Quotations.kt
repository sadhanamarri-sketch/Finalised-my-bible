package com.example.mybible.data

/**
 * The New Testament's quotations of the Old, read and checked by hand (see tools/quotations): each a
 * New Testament verse, or a few, and the Old Testament verses it quotes. They mark the verses that quote
 * or are quoted, which of the cross references are quotations, and the quotations the cross references
 * miss (to be links of their own, so each is listed from both its sides).
 */
class Quotations(private val pairs: List<Pair<Passage, Passage>>) {

    /** A verse, or a few in a row, in one chapter. */
    data class Passage(val book: String, val chapter: Int, val first: Int, val last: Int) {
        fun has(book: String, chapter: Int, verse: Int): Boolean =
            book == this.book && chapter == this.chapter && verse in first..last

        fun meets(book: String, chapter: Int, from: Int, to: Int): Boolean =
            book == this.book && chapter == this.chapter && from <= last && to >= first
    }

    private val byChapter: Map<Pair<String, Int>, List<Passage>> =
        pairs.flatMap { listOf(it.first, it.second) }.groupBy { it.book to it.chapter }

    val size: Int get() = pairs.size

    /** The verses of [book] [chapter] that quote the Old Testament or are quoted in the New. */
    fun versesIn(book: String, chapter: Int): Set<Int> =
        byChapter[book to chapter].orEmpty().flatMapTo(HashSet()) { it.first..it.last }

    /** Whether [book] [chapter]:[verse] quotes the Old Testament or is quoted in the New. */
    fun quotes(book: String, chapter: Int, verse: Int): Boolean =
        byChapter[book to chapter].orEmpty().any { it.has(book, chapter, verse) }

    /**
     * The passages [book] [chapter]:[verse] quotes, or is quoted in, that none of [linked] (the passages its
     * cross references go to) takes in: the quotations its cross references miss, to be links of their own.
     */
    fun unlinked(book: String, chapter: Int, verse: Int, linked: List<Passage>): List<Passage> =
        pairs.mapNotNull { (nt, ot) ->
            when {
                nt.has(book, chapter, verse) -> ot
                ot.has(book, chapter, verse) -> nt
                else -> null
            }
        }.distinct().filter { p -> linked.none { p.meets(it.book, it.chapter, it.first, it.last) } }

    /** Whether the cross reference from [book] [chapter]:[verse] to [toBook] [toChapter]:[toFirst]–[toLast] is a quotation, either way round. */
    fun isQuote(book: String, chapter: Int, verse: Int, toBook: String, toChapter: Int, toFirst: Int, toLast: Int): Boolean =
        quotedPart(book, chapter, verse, toBook, toChapter, toFirst, toLast) != null

    /**
     * When the cross reference from [book] [chapter]:[verse] to [toBook] [toChapter]:[toFirst]–[toLast] is a
     * quotation (either way round), the verses of its passage that quote or are quoted: of a link to Matthew
     * 22:42-46 from Psalm 110:1, 22:44. Null when it isn't one.
     */
    fun quotedPart(book: String, chapter: Int, verse: Int, toBook: String, toChapter: Int, toFirst: Int, toLast: Int): IntRange? {
        var from = Int.MAX_VALUE
        var to = Int.MIN_VALUE
        for ((nt, ot) in pairs) {
            val other = when {
                nt.has(book, chapter, verse) -> ot
                ot.has(book, chapter, verse) -> nt
                else -> continue
            }
            if (!other.meets(toBook, toChapter, toFirst, toLast)) continue
            from = minOf(from, maxOf(other.first, toFirst))
            to = maxOf(to, minOf(other.last, toLast))
        }
        return if (from <= to) from..to else null
    }

    companion object {
        val None = Quotations(emptyList())

        /**
         * quotations.tsv: a quotation a line, the New Testament verses, a tab, and the Old Testament ones they
         * quote, each as the app names them ("Matthew 2:15", "Psalms 22:1-2"). A line starting "#" is a note.
         */
        fun parse(text: String): Quotations = Quotations(
            text.lineSequence().mapNotNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                val (nt, ot) = line.split('\t').takeIf { it.size >= 2 } ?: return@mapNotNull null
                val from = passage(nt) ?: return@mapNotNull null
                val to = passage(ot) ?: return@mapNotNull null
                from to to
            }.toList()
        )

        private fun passage(text: String): Passage? {
            val at = text.trim().lastIndexOf(' ')
            if (at <= 0) return null
            val book = text.trim().substring(0, at)
            val (chapter, verses) = text.trim().substring(at + 1).split(':').takeIf { it.size == 2 } ?: return null
            val first = verses.substringBefore('-').toIntOrNull() ?: return null
            val last = verses.substringAfter('-', verses).toIntOrNull() ?: return null
            return Passage(book, chapter.toIntOrNull() ?: return null, first, last)
        }
    }
}
