package com.example.mybible.search

import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.SearchSource
import com.example.mybible.model.Verse
import java.util.BitSet

/**
 * Search over the King James text that also finds what a query means, not only its letters:
 *
 * - every form of each word (love: loved, loveth, loving), from the bundled word families;
 * - the King James wording for today's words (worry: careful, take no thought; you: thee, thou);
 * - verses with the same Greek or Hebrew word (worry: merimnaō, "to worry", wherever the King
 *   James renders it), from STEPBible's meanings;
 * - function words optional, so "come to me" finds "Come unto me".
 *
 * A verse needs every other word of the query, through its forms or one of those. Verses with
 * the words themselves come first, then those that say it in other words, each with why it
 * matched. Every addition is a [SearchSource] the Search page can switch off ([disabled] ids).
 *
 * A search typed in Greek or Hebrew, by Strong's number, or for a transliteration the King
 * James text doesn't have (agape) goes to [OriginalSearch] instead.
 */
class SmartSearch(private val index: BibleIndex, private val lexicon: SearchLexicon) {

    private val inBible: (String) -> Boolean = index::contains
    private val originals = OriginalSearch(index, lexicon)

    fun search(query: String, caseSensitive: Boolean = false, disabled: Set<String> = emptySet()): SearchOutcome {
        originals.search(query, disabled)?.let { return it }
        originals.searchTransliteration(query, disabled)?.let { return it }
        val typed = typedWordsOf(query)
        val terms = termsOf(typed, caseSensitive)
        if (terms.isEmpty()) return SearchOutcome()
        val formsOn = !caseSensitive && FORMS !in disabled
        val required = terms.filter { it.required }

        for (t in required) {
            t.literal = versesWith(t.words.map { setOf(it) }, if (caseSensitive) t.typed else null)
            t.exact = if (caseSensitive) t.literal else versesWith(t.words.map(lexicon::formsOf))
        }
        if (!caseSensitive) addSources(required)

        fun exactOf(t: Term) = if (formsOn) t.exact else t.literal
        val matching = required.map { t ->
            (exactOf(t).clone() as BitSet).also { m -> t.sources.forEach { if (it.id !in disabled) m.or(it.verses) } }
        }
        val candidates = matching.reduce { a, b -> (a.clone() as BitSet).apply { and(b) } }

        val ranking = Ranking(terms, formsOn, disabled)
        val exactHits = ArrayList<Ranked>()
        val relatedHits = ArrayList<Ranked>()
        candidates.forEachSetBit { id ->
            val missing = required.filter { !exactOf(it).get(id) }
            val reasons = missing.map { t -> t.sources.filter { it.id !in disabled && it.verses.get(id) }.minBy { it.rank } }
            val ranked = ranking.rank(id, reasons)
            if (missing.isEmpty()) exactHits += ranked else relatedHits += ranked
        }
        exactHits.sortWith(byPlace)
        relatedHits.sortWith(byPlace)

        val highlighter = Highlighter(terms, ::exactOf, caseSensitive, formsOn, ranking.slots)
        val hits = exactHits.map { highlighter.hit(it, related = false) } + relatedHits.map { highlighter.hit(it, related = true) }
        val sources = if (caseSensitive) emptyList() else chips(required, matching, ::exactOf, disabled)

        if (hits.isNotEmpty()) return SearchOutcome(hits, exactHits.size, sources)
        if (caseSensitive) return SearchOutcome()
        suggestionFor(query)?.let { return SearchOutcome(sources = sources, suggestion = it) }
        return closeMatches(required, matching, ::exactOf, disabled, ranking, highlighter, sources)
    }

    // ---- query words and terms ----

    private class Term(val words: List<String>, val typed: List<String>, var required: Boolean) {
        var wording: List<List<String>> = emptyList()
        var literal = BitSet()
        var exact = BitSet()
        val sources = ArrayList<Source>()
    }

    private fun typedWordsOf(query: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < query.length) {
            if (query[i] in 'a'..'z' || query[i] in 'A'..'Z') {
                val start = i
                while (i < query.length && (query[i] in 'a'..'z' || query[i] in 'A'..'Z')) i++
                if (i - start > 1) out += query.substring(start, i)
            } else {
                i++
            }
        }
        return out
    }

    /**
     * The query's words as terms: today's phrases the King James words differently become one
     * ("holy spirit"), function words become optional, and a word whose forms another word of
     * the query already has ("love one another as I have loved you") only helps the ranking.
     */
    private fun termsOf(typed: List<String>, caseSensitive: Boolean): List<Term> {
        val lower = typed.map { it.lowercase() }
        val terms = ArrayList<Term>()
        var i = 0
        while (i < lower.size) {
            var phrase: Term? = null
            if (!caseSensitive) {
                for (n in minOf(4, lower.size - i) downTo 2) {
                    val words = lower.subList(i, i + n).toList()
                    val wording = lexicon.kingJamesWording(words, inBible) ?: continue
                    phrase = Term(words, typed.subList(i, i + n).toList(), required = true).also { it.wording = wording }
                    break
                }
            }
            if (phrase != null) {
                terms += phrase
                i += phrase.words.size
                continue
            }
            val word = lower[i]
            terms += Term(listOf(word), listOf(typed[i]), required = word !in OPTIONAL_WORDS).also {
                if (!caseSensitive) it.wording = lexicon.kingJamesWording(listOf(word), inBible).orEmpty()
            }
            i++
        }
        if (terms.none { it.required }) terms.forEach { it.required = true }
        if (!caseSensitive) {
            val seen = HashSet<Set<String>>()
            for (t in terms) {
                if (t.required && t.words.size == 1 && !seen.add(lexicon.formsOf(t.words[0]))) t.required = false
            }
        }
        return terms
    }

    // ---- what else each word matches ----

    private enum class Kind { WORDING, ORIGINAL }

    private class Source(
        val id: String,
        val kind: Kind,
        val chip: String,
        val reason: String,
        val verses: BitSet,
        /** The words to mark in a verse it matched, each a run of word slots. */
        val marks: List<List<Set<String>>>,
        /** For King James wording, its words. */
        val words: List<String> = emptyList()
    ) {
        var rank = 0
    }

    private fun addSources(required: List<Term>) {
        var rank = 0
        for (t in required) {
            val seen = HashSet<List<Set<String>>>()
            for (alternative in t.wording) {
                val slots = alternative.map(lexicon::formsOf)
                if (!seen.add(slots)) continue // neighbour, neighbours: the same verses
                val text = alternative.joinToString(" ")
                t.sources += Source(
                    id = "kjv:$text",
                    kind = Kind.WORDING,
                    chip = "KJV: $text",
                    reason = "KJV wording: “$text”",
                    verses = versesWith(slots),
                    // thee and thou are everywhere; the query's own run marks them where it matters.
                    marks = if (alternative.size == 1 && alternative[0] in STOPWORDS) emptyList() else listOf(slots),
                    words = alternative
                ).also { it.rank = rank++ }
            }
        }
        val originals = ArrayList<Triple<Term, Source, Pair<Boolean, Int>>>()
        for (t in required) {
            val word = t.words.singleOrNull() ?: continue
            if (word in STOPWORDS || t.exact.cardinality() > COMMON_WORD_VERSES) continue
            for (match in lexicon.meaningsOf(word, inBible)) {
                val original = match.word
                val verses = BitSet(index.size)
                for (ref in original.verses) {
                    val id = index.idOf(ref)
                    if (id >= 0) verses.set(id)
                }
                val added = (verses.clone() as BitSet).apply { andNot(t.exact) }.cardinality()
                if (added == 0) continue
                val marked = lexicon.markedFormsOf(original)
                val name = "${original.language} ${original.transliteration}"
                val source = Source(
                    id = OriginalSearch.SOURCE_PREFIX + original.key,
                    kind = Kind.ORIGINAL,
                    chip = name,
                    reason = "$name, meaning “${original.gloss}”",
                    verses = verses,
                    marks = if (marked.isEmpty()) emptyList() else listOf(listOf(marked))
                )
                originals += Triple(t, source, match.byMeaning to added)
            }
        }
        // Meaning matches first, then the ones adding the most verses.
        originals.sortWith(compareBy({ !it.third.first }, { -it.third.second }))
        for ((t, source, _) in originals) {
            source.rank = rank++
            t.sources += source
        }
    }

    // ---- ranking ----

    private class Ranked(val id: Int, val tier: Int, val pairs: Int, val missing: Int, val reasons: List<Source>, val words: List<Token>) {
        val sourceRank = reasons.minOfOrNull { it.rank } ?: -1
    }

    private val byPlace = compareBy<Ranked>({ it.tier }, { -it.pairs }, { it.missing }, { it.sourceRank }, { it.id })

    /**
     * Puts the query's words in its order first: tier 0 the words as typed, together; tier 1 in
     * any form or King James wording; then by how many neighbouring pairs of them a verse has,
     * how few optional words it lacks, and Bible order.
     */
    private inner class Ranking(terms: List<Term>, formsOn: Boolean, disabled: Set<String>) {
        val slots = ArrayList<Set<String>>()
        private val typedRun = ArrayList<Set<String>>()
        private val optional = ArrayList<Set<String>>()

        init {
            for (t in terms) {
                if (t.words.size > 1) {
                    for (word in t.words) {
                        slots += if (formsOn) lexicon.formsOf(word) else setOf(word)
                        typedRun += setOf(word)
                    }
                    continue
                }
                val word = t.words[0]
                val slot = HashSet(if (formsOn) lexicon.formsOf(word) else setOf(word))
                if (t.required) {
                    for (s in t.sources) {
                        if (s.kind == Kind.WORDING && s.id !in disabled && s.words.size == 1) slot += lexicon.formsOf(s.words[0])
                    }
                } else {
                    for (alternative in t.wording) if (alternative.size == 1) slot += alternative[0]
                    optional += slot
                }
                slots += slot
                typedRun += setOf(word)
            }
        }

        fun rank(id: Int, reasons: List<Source>): Ranked {
            val words = tokensOf(index[id].text)
            var tier = 2
            var pairs = 0
            if (slots.size > 1) {
                tier = when {
                    hasRun(words, typedRun) -> 0
                    hasRun(words, slots) -> 1
                    else -> 2
                }
                if (slots.size > 2) {
                    for (k in 0 until slots.size - 1) if (hasRun(words, slots.subList(k, k + 2))) pairs++
                }
            }
            val missing = if (optional.isEmpty()) 0 else {
                val present = words.mapTo(HashSet()) { it.word }
                optional.count { slot -> slot.none { it in present } }
            }
            return Ranked(id, tier, pairs, missing, reasons, words)
        }
    }

    // ---- highlights ----

    private inner class Highlighter(
        private val terms: List<Term>,
        private val exactOf: (Term) -> BitSet,
        private val caseSensitive: Boolean,
        private val formsOn: Boolean,
        private val querySlots: List<Set<String>>
    ) {
        private val allFunctionWords = terms.all { t -> t.words.all { it in STOPWORDS } }

        fun hit(ranked: Ranked, related: Boolean, reasons: List<String>? = null): SearchHit {
            val verse = index[ranked.id]
            val text = verse.text
            val words = ranked.words
            val marks = ArrayList<IntRange>()
            for (t in terms) {
                if (!t.required) continue
                if (exactOf(t).get(ranked.id)) {
                    if (t.words.size == 1 && t.words[0] in STOPWORDS && !allFunctionWords) continue
                    val slots = t.words.map { if (formsOn) lexicon.formsOf(it) else setOf(it) }
                    marks += runs(words, text, slots, if (caseSensitive) t.typed else null)
                }
            }
            for (source in ranked.reasons) for (run in source.marks) marks += runs(words, text, run)
            if (ranked.tier <= 1) marks += runs(words, text, querySlots)
            return SearchHit(
                verse = Verse(verse.book, verse.chapter, verse.number, text),
                text = text,
                highlights = merged(marks),
                related = related,
                reasons = reasons ?: ranked.reasons.map { it.reason }.distinct()
            )
        }
    }

    // ---- the chips ----

    private fun chips(
        required: List<Term>,
        matching: List<BitSet>,
        exactOf: (Term) -> BitSet,
        disabled: Set<String>
    ): List<SearchSource> {
        // What each source adds: verses it matches that have the term's word in no form, but every
        // other word of the query. Counted whether or not it's switched on, so a chip that's off
        // still says what it would bring back.
        val added = LinkedHashMap<String, BitSet>()
        val byId = LinkedHashMap<String, Source>()
        for ((i, t) in required.withIndex()) {
            var others: BitSet? = null
            for ((j, m) in matching.withIndex()) {
                if (j == i) continue
                others = if (others == null) m.clone() as BitSet else others.apply { and(m) }
            }
            for (s in t.sources) {
                val verses = (s.verses.clone() as BitSet).apply { andNot(exactOf(t)) }
                others?.let { verses.and(it) }
                added.getOrPut(s.id) { BitSet() }.or(verses)
                byId.putIfAbsent(s.id, s)
            }
        }
        val out = ArrayList<SearchSource>()
        val allExact = required.map { it.exact }.reduce { a, b -> (a.clone() as BitSet).apply { and(b) } }
        val allLiteral = required.map { it.literal }.reduce { a, b -> (a.clone() as BitSet).apply { and(b) } }
        val formsAdd = (allExact.clone() as BitSet).apply { andNot(allLiteral) }.cardinality()
        if (formsAdd > 0) out += SearchSource(listOf(FORMS), "Word forms", formsAdd, FORMS !in disabled)

        val ordered = byId.values.sortedBy { it.rank }.filter { added.getValue(it.id).cardinality() > 0 }
        for (s in ordered.filter { it.kind == Kind.WORDING }) {
            out += SearchSource(listOf(s.id), s.chip, added.getValue(s.id).cardinality(), s.id !in disabled)
        }
        // Senses that read the same (chayil "strength" in Hebrew and in Aramaic) share a chip; two
        // with the same name but different meanings get the meaning added to tell them apart.
        val originals = ordered.filter { it.kind == Kind.ORIGINAL }.groupBy { it.reason }.values.toList()
        val shown = originals.take(MAX_ORIGINAL_CHIPS)
        val sameName = shown.groupingBy { it[0].chip }.eachCount()
        for (group in shown) {
            val first = group[0]
            val label = if (sameName.getValue(first.chip) > 1) "${first.chip} · ${first.reason.substringAfter("meaning ")}" else first.chip
            out += originalsChip(group, label, added, disabled)
        }
        val rest = originals.drop(MAX_ORIGINAL_CHIPS).flatten()
        if (rest.isNotEmpty()) out += originalsChip(rest, "More Greek & Hebrew", added, disabled)
        return out
    }

    private fun originalsChip(sources: List<Source>, label: String, added: Map<String, BitSet>, disabled: Set<String>): SearchSource {
        val verses = BitSet().apply { sources.forEach { or(added.getValue(it.id)) } }
        return SearchSource(sources.map { it.id }, label, verses.cardinality(), sources.all { it.id !in disabled })
    }

    // ---- nothing found ----

    /** The query with each word the Bible doesn't have put right ("fiath" → "faith"), or null. */
    private fun suggestionFor(query: String): String? {
        val words = wordsOf(query)
        var changed = false
        val fixed = words.map { word ->
            val known = word.length < 3 || index.contains(word) ||
                lexicon.kingJamesWording(listOf(word), inBible) != null ||
                plainForms(word).any(index::contains) || lexicon.hasMeaning(word, inBible)
            if (known) word else closestWord(word)?.also { changed = true } ?: word
        }
        return if (changed) fixed.joinToString(" ") else null
    }

    private fun closestWord(word: String): String? {
        val limit = if (word.length <= 4) 1 else 2
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var bestFunction = true
        var bestCount = 0
        for ((candidate, count) in index.vocabulary) {
            val distance = editDistance(word, candidate, limit)
            if (distance > limit) continue
            val function = candidate in STOPWORDS
            val better = distance < bestDistance ||
                (distance == bestDistance && (bestFunction && !function || bestFunction == function && count > bestCount))
            if (better) {
                best = candidate
                bestDistance = distance
                bestFunction = function
                bestCount = count
            }
        }
        return best
    }

    /** No verse has every word: the verses with all but one, for a query of three or more. */
    private fun closeMatches(
        required: List<Term>,
        matching: List<BitSet>,
        exactOf: (Term) -> BitSet,
        disabled: Set<String>,
        ranking: Ranking,
        highlighter: Highlighter,
        sources: List<SearchSource>
    ): SearchOutcome {
        if (required.size < 3) return SearchOutcome(sources = sources)
        val seen = BitSet(index.size)
        val hits = ArrayList<Pair<Ranked, String>>()
        for (skip in required.indices) {
            val others = matching.filterIndexed { i, _ -> i != skip }.reduce { a, b -> (a.clone() as BitSet).apply { and(b) } }
            others.forEachSetBit { id ->
                if (seen.get(id)) return@forEachSetBit
                seen.set(id)
                val reasons = required.filter { it !== required[skip] && !exactOf(it).get(id) }
                    .map { t -> t.sources.filter { it.id !in disabled && it.verses.get(id) }.minBy { it.rank } }
                hits += ranking.rank(id, reasons) to "Without “${required[skip].typed.joinToString(" ")}”"
            }
        }
        if (hits.isEmpty()) return SearchOutcome(sources = sources)
        hits.sortWith(compareBy(byPlace) { it.first })
        return SearchOutcome(
            hits = hits.map { (ranked, without) ->
                highlighter.hit(ranked, related = false, reasons = ranked.reasons.map { it.reason }.distinct() + without)
            },
            sources = sources,
            closeMatches = true
        )
    }

    // ---- verses with a run of words ----

    private class Token(val word: String, val start: Int, val end: Int)

    private fun tokensOf(text: String): List<Token> {
        val out = ArrayList<Token>()
        forEachWord(text) { word, start, end -> out += Token(word, start, end) }
        return out
    }

    /** Verses where a word of each slot comes in a row ([exactCase]: spelled as typed, capitals and all). */
    private fun versesWith(slots: List<Set<String>>, exactCase: List<String>? = null): BitSet {
        var found: BitSet? = null
        for (slot in slots) {
            val verses = index.versesWithAny(slot)
            found = found?.apply { and(verses) } ?: verses
        }
        val candidates = found ?: BitSet()
        if (slots.size == 1 && exactCase == null) return candidates
        val out = BitSet(index.size)
        candidates.forEachSetBit { id ->
            val text = index[id].text
            if (runs(tokensOf(text), text, slots, exactCase, firstOnly = true).isNotEmpty()) out.set(id)
        }
        return out
    }

    private fun hasRun(words: List<Token>, slots: List<Set<String>>): Boolean {
        if (slots.isEmpty() || words.size < slots.size) return false
        for (j in 0..words.size - slots.size) {
            if (slots.indices.all { k -> words[j + k].word in slots[k] }) return true
        }
        return false
    }

    private fun runs(
        words: List<Token>,
        text: String,
        slots: List<Set<String>>,
        exactCase: List<String>? = null,
        firstOnly: Boolean = false
    ): List<IntRange> {
        if (slots.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var j = 0
        while (j + slots.size <= words.size) {
            val matches = slots.indices.all { k ->
                val w = words[j + k]
                w.word in slots[k] && (exactCase == null || text.regionMatches(w.start, exactCase[k], 0, exactCase[k].length) && w.end - w.start == exactCase[k].length)
            }
            if (matches) {
                out += words[j].start until words[j + slots.size - 1].end
                if (firstOnly) return out
                j += slots.size
            } else {
                j++
            }
        }
        return out
    }

    private fun merged(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size < 2) return ranges
        val sorted = ranges.sortedBy { it.first }
        val out = ArrayList<IntRange>()
        var current = sorted[0]
        for (r in sorted.drop(1)) {
            current = if (r.first <= current.last + 1) current.first..maxOf(current.last, r.last) else {
                out += current
                r
            }
        }
        out += current
        return out
    }

    companion object {
        /** Id of the "Word forms" chip. */
        const val FORMS = "forms"
        /** A word already in this many verses gets no Greek and Hebrew: come, say, lord. */
        const val COMMON_WORD_VERSES = 1000
        /** Past this many, Greek and Hebrew words share one "More Greek & Hebrew" chip. */
        const val MAX_ORIGINAL_CHIPS = 5
    }
}

private inline fun BitSet.forEachSetBit(action: (Int) -> Unit) {
    var i = nextSetBit(0)
    while (i >= 0) {
        action(i)
        i = nextSetBit(i + 1)
    }
}
