package com.example.mybible.search

import java.io.InputStream

/**
 * A Greek or Hebrew word in one of its senses (STEPBible splits aphiēmi into "leave", "forgive"
 * and "permit"), from assets/search/original_words.tsv.
 */
class OriginalWord(
    /** STEPBible's sense-level Strong's number: G0863H. */
    val key: String,
    val transliteration: String,
    /** What it means, in today's English: "forgive", "to worry". */
    val gloss: String,
    /** How many verses it's in. */
    val verseCount: Int,
    /** The King James words Strong's lists for it, with the % of its verses using each. */
    val renderings: Map<String, Int>,
    /** Its verses as book index * 65536 + chapter * 256 + verse; empty past MAX_VERSES. */
    val verses: IntArray
) {
    val language: String get() = if (key.startsWith("G")) "Greek" else "Hebrew"

    /** The single words its gloss means: "to worry" → worry; "to hope/expect" → hope, expect. */
    val heads: Set<String> = glossHeads(gloss)
}

/** A Greek or Hebrew word a search word matched, and whether through its meaning or its renderings. */
class OriginalMatch(val word: OriginalWord, val byMeaning: Boolean)

/**
 * Search's bundled word data in app/src/main/assets/search, written by
 * tools/search/make_search_data.py: the King James word forms, today's words with their King
 * James wording, and the Greek and Hebrew words with their meanings, renderings and verses.
 */
class SearchLexicon private constructor(
    families: List<List<String>>,
    private val modern: List<Pair<List<String>, List<List<String>>>>,
    originals: List<OriginalWord>
) {
    private val familiesOf = HashMap<String, MutableList<List<String>>>()
    private val byHead = HashMap<String, MutableList<OriginalWord>>()
    private val byRendering = HashMap<String, MutableList<OriginalWord>>()

    init {
        for (family in families) for (word in family) familiesOf.getOrPut(word) { ArrayList(1) } += family
        for (word in originals) {
            if (word.verses.isEmpty()) continue
            for (head in word.heads) byHead.getOrPut(head) { ArrayList(2) } += word
            if (word.verseCount < MIN_VERSES_FOR_RENDERINGS) continue
            for ((rendering, share) in word.renderings) {
                if (share >= MIN_RENDERING_SHARE) byRendering.getOrPut(rendering) { ArrayList(2) } += word
            }
        }
    }

    /**
     * The forms a search for [word] also finds: its own family and every family it's a form in
     * (healing → healings, and heal's: heal, healed, healeth). Never a function word it didn't ask
     * for, and never the forms of be, have or do (being doesn't find is or was).
     */
    fun formsOf(word: String): Set<String> {
        if (word in STOPWORDS) return setOf(word)
        val out = linkedSetOf(word)
        familiesOf[word]?.forEach { family ->
            if (family[0] !in STOPWORDS) family.filterTo(out) { it !in STOPWORDS }
        }
        return out
    }

    /**
     * The words [word] can stand for when matching meanings and today's wording: its forms when
     * the King James text has it, else its plain forms (worried → worry, demons → demon).
     */
    fun kin(word: String, inBible: (String) -> Boolean): Set<String> =
        if (inBible(word)) formsOf(word) else plainForms(word)

    /**
     * The King James wording for today's [words] (holy spirit → holy ghost; worry → careful,
     * take no thought, ...), each a word or a phrase; null when the list has nothing for them.
     */
    fun kingJamesWording(words: List<String>, inBible: (String) -> Boolean): List<List<String>>? {
        val kins = words.map { kin(it, inBible) }
        return modern.firstOrNull { (key, _) ->
            key.size == words.size && key.indices.all { key[it] in kins[it] }
        }?.second
    }

    /** True when the Greek and Hebrew meanings know [word], so it isn't a typo to correct. */
    fun hasMeaning(word: String, inBible: (String) -> Boolean): Boolean =
        kin(word, inBible).any { it in byHead }

    /**
     * The Greek and Hebrew words that mean [word]: those whose gloss is it (merimnaō "to worry"),
     * and those the King James renders with it at least 20% of the time when the rest is mostly
     * its gloss word, so the two are interchangeable there (agapē: charity 23%, love 75%).
     * The second rule keeps out words with a sense unrelated to [word]: charizomai is "forgive"
     * 42% of the time but "give" or "deliver" most of the rest.
     */
    fun meaningsOf(word: String, inBible: (String) -> Boolean): List<OriginalMatch> {
        if (word in STOPWORDS) return emptyList()
        val found = LinkedHashMap<String, OriginalMatch>()
        for (k in kin(word, inBible)) {
            byHead[k]?.forEach { found.getOrPut(it.key) { OriginalMatch(it, byMeaning = true) } }
        }
        if (inBible(word)) {
            val forms = formsOf(word)
            for (form in forms) {
                for (original in byRendering[form].orEmpty()) {
                    if (original.key in found) continue
                    val share = forms.maxOf { original.renderings[it] ?: 0 }
                    val glossWords = kingJamesWordsOf(original.heads, inBible)
                    val glossShare = original.renderings.entries
                        .filter { (rendering, _) -> formsOf(rendering).any { it in glossWords } }
                        .maxOfOrNull { it.value } ?: 0
                    if (share >= MIN_RENDERING_SHARE && share + glossShare >= MIN_COMBINED_SHARE) {
                        found[original.key] = OriginalMatch(original, byMeaning = false)
                    }
                }
            }
        }
        return found.values.toList()
    }

    // A gloss in today's spelling or words, with the King James one: favor → favour.
    private fun kingJamesWordsOf(heads: Set<String>, inBible: (String) -> Boolean): Set<String> {
        val out = HashSet(heads)
        for (head in heads) {
            kingJamesWording(listOf(head), inBible)?.forEach { if (it.size == 1) out += it[0] }
        }
        return out
    }

    companion object {
        /** Words in more verses than this have no verse list: God, Lord, "the" (make_search_data.py). */
        const val MAX_VERSES = 500
        const val MIN_RENDERING_SHARE = 20
        const val MIN_COMBINED_SHARE = 80
        // Below this a single verse is a big share: H7293 rahav is "strength" in one verse of two.
        const val MIN_VERSES_FOR_RENDERINGS = 4

        const val WORD_FORMS = "word_forms.tsv"
        const val MODERN_KJV = "modern_kjv.tsv"
        const val ORIGINAL_WORDS = "original_words.tsv"

        fun load(open: (String) -> InputStream): SearchLexicon = parse(
            wordForms = open(WORD_FORMS).bufferedReader().use { it.readText() },
            modernKjv = open(MODERN_KJV).bufferedReader().use { it.readText() },
            originalWords = open(ORIGINAL_WORDS).bufferedReader().use { it.readText() }
        )

        fun parse(wordForms: String, modernKjv: String, originalWords: String): SearchLexicon {
            val families = dataLines(wordForms).map { it.split(' ') }.filter { it.size > 1 }
            val modern = dataLines(modernKjv).mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val kjv = line.substring(tab + 1).split('|').map { it.split(' ') }
                line.substring(0, tab).split('|').map { it.split(' ') to kjv }
            }.flatten()
            val originals = dataLines(originalWords).mapNotNull(::parseOriginal)
            return SearchLexicon(families, modern, originals)
        }

        private fun dataLines(text: String) = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()

        private fun parseOriginal(line: String): OriginalWord? {
            val cols = line.split('\t')
            if (cols.size < 6) return null
            val renderings = HashMap<String, Int>()
            for (part in cols[4].split(',')) {
                val colon = part.indexOf(':')
                if (colon > 0) renderings[part.substring(0, colon)] = part.substring(colon + 1).toIntOrNull() ?: continue
            }
            return OriginalWord(
                key = cols[0],
                transliteration = cols[1],
                gloss = cols[2],
                verseCount = cols[3].toIntOrNull() ?: return null,
                renderings = renderings,
                verses = decodeRefs(cols[5])
            )
        }

        /** make_search_data.py's encode_refs, undone: base-36 differences from the verse before. */
        internal fun decodeRefs(encoded: String): IntArray {
            if (encoded.isEmpty()) return IntArray(0)
            val parts = encoded.split(',')
            val out = IntArray(parts.size)
            var last = 0
            for ((i, part) in parts.withIndex()) {
                last += part.toInt(36)
                out[i] = last
            }
            return out
        }
    }
}

// Particles that don't change what a gloss is about: "to look up" means look.
private val GLOSS_PARTICLES = setOf(
    "up", "down", "out", "off", "away", "again", "through", "back", "over", "forth", "about",
    "around", "along", "aside", "together"
)
private val parenthesized = Regex("\\([^)]*\\)")
private val glossAlternatives = Regex("[/,;]")

/**
 * The single words a gloss means: "to worry" → worry; "to hope/expect" → hope, expect.
 * "brotherly love" and "money-loving" give nothing: they name something narrower than love.
 */
internal fun glossHeads(gloss: String): Set<String> {
    val out = HashSet<String>(2)
    for (alternative in parenthesized.replace(gloss.lowercase(), " ").split(glossAlternatives)) {
        val content = wordsOf(alternative).filter { it !in STOPWORDS && it !in GLOSS_PARTICLES }
        if (content.size == 1) out += content[0]
    }
    return out
}
