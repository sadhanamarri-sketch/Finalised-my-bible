package com.example.mybible.search

import com.example.mybible.model.RelatedKind
import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.SearchSource
import com.example.mybible.model.Verse

/**
 * Search in the Telugu text. Telugu joins endings onto its words, so each word of the query is
 * looked for where a word starts: దేవ finds దేవుడు and దేవుని, but దయ ("mercy") isn't found inside
 * హృదయము ("heart"). A longer word is also looked for inside longer ones (యేసు in క్రీస్తుయేసు),
 * and verses with it only so come after the rest, a chip that switches them off. Verses with the
 * words as typed, one after another, come first.
 */
object TeluguSearch {

    /** Id of the "Inside longer words" chip. */
    const val INSIDE = "telugu:inside"
    /** Words shorter than this (in Unicode characters) are only looked for where a word starts. */
    const val MIN_INSIDE_LENGTH = 4

    /** The query's Telugu words, as [matchKey] spells them. */
    fun wordsOf(query: String): List<String> = tokensOf(query).map { it.key }.filter { it.isNotEmpty() }.distinct()

    /** The longest of [words], what to find candidate verses by. */
    fun longest(words: List<String>): String = words.maxBy { it.length }

    /**
     * [verses] (each with its Telugu text, in Bible order) that have every word of [query]:
     * those with each where a word starts, the ones with them in a row first; then, unless
     * [disabled] holds [INSIDE], those with some only inside a longer word.
     */
    fun search(query: String, verses: List<Verse>, disabled: Set<String> = emptySet()): SearchOutcome {
        val words = wordsOf(query)
        if (words.isEmpty()) return SearchOutcome()
        val inRow = ArrayList<SearchHit>()
        val atStart = ArrayList<SearchHit>()
        val inside = ArrayList<SearchHit>()
        for (verse in verses) {
            val text = verse.teluguText ?: continue
            val tokens = tokensOf(text)
            val starts = words.map { w -> tokens.filter { it.key.startsWith(w) } }
            if (starts.all { it.isNotEmpty() }) {
                val hit = SearchHit(verse, text, starts.flatten().distinct().sortedBy { it.start }.map { it.start until it.end })
                if (words.size > 1 && hasRun(tokens, words)) inRow += hit else atStart += hit
                continue
            }
            val within = words.map { w ->
                if (w.codePointCount(0, w.length) < MIN_INSIDE_LENGTH) emptyList() else tokens.filter { w in it.key && !it.key.startsWith(w) }
            }
            if (words.indices.all { starts[it].isNotEmpty() || within[it].isNotEmpty() }) {
                val marked = (starts.flatten() + within.flatten()).distinct().sortedBy { it.start }
                val longer = within.flatten().map { text.substring(it.start, it.end) }.distinct().take(2)
                inside += SearchHit(verse, text, marked.map { it.start until it.end }, reasons = longer.map { "Inside “$it”" })
            }
        }
        val exact = inRow + atStart
        val sources = if (inside.isEmpty()) emptyList() else {
            val byBook = inside.groupingBy { it.verse.book }.eachCount()
            listOf(SearchSource(listOf(INSIDE), "Inside longer words", inside.size, INSIDE !in disabled, byBook))
        }
        val hits = if (INSIDE in disabled) exact else exact + inside
        return SearchOutcome(hits = hits, exactCount = exact.size, sources = sources, relatedKind = RelatedKind.INSIDE_LONGER_WORDS)
    }

    // A word of the text: where it is, and its letters without the invisible joiners and soft
    // hyphens the text has here and there (ఆమేన్‌), which a query won't.
    private class Token(val start: Int, val end: Int, val key: String)

    private fun tokensOf(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            if (!isTelugu(text[i])) {
                i++
                continue
            }
            val start = i
            while (i < text.length && (isTelugu(text[i]) || text[i] in INVISIBLE)) i++
            out += Token(start, i, matchKey(text.substring(start, i)))
        }
        return out
    }

    /** [word] as matched: without zero-width joiners and soft hyphens. */
    fun matchKey(word: String): String = word.filterNot { it in INVISIBLE }

    private fun isTelugu(c: Char) = c in 'ఀ'..'౿'

    // The query's words in a row, each where a word starts: దేవుడు లోకమును, "God ... the world".
    private fun hasRun(tokens: List<Token>, words: List<String>): Boolean {
        for (i in 0..tokens.size - words.size) {
            if (words.indices.all { k -> tokens[i + k].key.startsWith(words[k]) }) return true
        }
        return false
    }

    private const val INVISIBLE = "‌‍­"
}

/** True when [text] has a Telugu letter: a search in the Telugu text. */
internal fun hasTeluguLetter(text: String) = text.any { it in 'ఀ'..'౿' }
