package com.example.mybible.search

import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * A Greek or Hebrew word in one of its senses (STEPBible splits aphiēmi into "leave", "forgive"
 * and "permit"), from assets/search/original_words.tsv.
 */
class OriginalWord(
    /** STEPBible's sense-level Strong's number: G0863H. */
    val key: String,
    /** Greek, Hebrew or Aramaic. */
    val language: String,
    /** The word itself: ἀφίημι. Now and then two spellings: "ἄρρην, ἄρσην". */
    val lemma: String,
    val transliteration: String,
    /** What it means, in today's English: "forgive", "to worry". */
    val gloss: String,
    /** How many verses it's in. */
    val verseCount: Int,
    /** The King James words Strong's lists for it, with the % of its verses using each. */
    val renderings: Map<String, Int>,
    /**
     * The renderings worth marking where it is: used in 5% of its verses or more, and at least 4
     * times likelier there than anywhere (logos: word, not say). See make_search_data.py.
     */
    val marked: Set<String>,
    // Its verses as original_words.tsv has them, read the first time they're needed: most
    // searches need only a few words' verses, and all of them take a while to read.
    private val encodedVerses: String
) {
    /** Its verses as book index * 65536 + chapter * 256 + verse. */
    val verses: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) { SearchLexicon.decodeRefs(encodedVerses) }

    /** Its Strong's number without the sense: G0863. */
    val number: String get() = key.substring(0, 5)

    /** The single words its gloss means: "to worry" → worry; "to hope/expect" → hope, expect. */
    val heads: Set<String> = glossHeads(gloss)
}

/** A Greek or Hebrew word a search word matched, and whether through its meaning or its renderings. */
class OriginalMatch(val word: OriginalWord, val byMeaning: Boolean)

/**
 * Search's bundled word data in app/src/main/assets/search, written by
 * tools/search/make_search_data.py: the King James word forms, today's words with their King
 * James wording, and the Greek and Hebrew words with their meanings, renderings, verses and the
 * forms they take in the text.
 */
class SearchLexicon private constructor(
    families: List<List<String>>,
    private val modern: List<Pair<List<String>, List<List<String>>>>,
    private val originals: List<OriginalWord>,
    // original_forms.tsv's text, read the first time a search is typed in Greek or Hebrew.
    private val originalForms: () -> String
) {
    private val familiesOf = HashMap<String, MutableList<List<String>>>()
    private val byHead = HashMap<String, MutableList<OriginalWord>>()
    private val byRendering = HashMap<String, MutableList<OriginalWord>>()
    private val markedForms = ConcurrentHashMap<String, Set<String>>()

    init {
        for (family in families) for (word in family) familiesOf.getOrPut(word) { ArrayList(1) } += family
        for (word in originals) {
            // God, Lord, "the": their verses mostly say it anyway.
            if (word.verseCount > MAX_VERSES) continue
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

    /** The words to mark in a verse where [word] is: every form of its [OriginalWord.marked] renderings. */
    fun markedFormsOf(word: OriginalWord): Set<String> =
        markedForms.getOrPut(word.key) { word.marked.flatMapTo(HashSet(), ::formsOf) }

    // ---- Greek and Hebrew words by how a search names them (see OriginalSearch) ----

    private val byNumber: Map<String, List<OriginalWord>> by lazy { originals.groupBy { it.number } }

    // By originalKey of the word (each spelling of it) and latinKey of its transliteration.
    private val byLemma: Map<String, List<OriginalWord>> by lazy { indexBy { word -> word.lemma.split(',').map(::originalKey) } }
    private val byTransliteration: Map<String, List<OriginalWord>> by lazy {
        indexBy { word -> word.transliteration.split(',').map(::latinKey) }
    }

    // Every other spelling the text has (ηγαπησεν), as originalKey, with the numbers of the words spelled so.
    private val numbersBySpelling: Map<String, List<String>> by lazy {
        val out = HashMap<String, MutableList<String>>(65_536)
        for (line in dataLines(originalForms())) {
            val tab = line.indexOf('\t')
            if (tab < 0) continue
            val number = line.substring(0, tab)
            for (spelling in line.substring(tab + 1).split(' ')) out.getOrPut(spelling) { ArrayList(1) } += number
        }
        out
    }

    private fun indexBy(keys: (OriginalWord) -> List<String>): Map<String, List<OriginalWord>> {
        val out = HashMap<String, MutableList<OriginalWord>>(originals.size * 2)
        for (word in originals) for (key in keys(word)) if (key.isNotEmpty()) out.getOrPut(key) { ArrayList(2) } += word
        return out
    }

    /** Every sense of the word numbered [number] (G0863): aphiēmi's "leave", "forgive" and "permit". */
    fun originalsNumbered(number: String): List<OriginalWord> = byNumber[number].orEmpty()

    /** The sense numbered [key] (G0863H), or null. */
    fun original(key: String): OriginalWord? = byNumber[key.take(5)]?.firstOrNull { it.key == key }

    /** The numbers of the words spelled [key] (an [originalKey]) themselves: αγαπη, agapē's. */
    fun numbersWithLemma(key: String): List<String> = byLemma[key]?.map { it.number }?.distinct().orEmpty()

    /** The numbers of the words the text spells [key] somewhere: ηγαπησεν, agapaō's. */
    fun numbersWithForm(key: String): List<String> = numbersBySpelling[key].orEmpty()

    /** The words STEPBible transliterates as [key] (a [latinKey]: agape, chesed). */
    fun originalsTransliterated(key: String): List<OriginalWord> = byTransliteration[key].orEmpty()

    /** Every spelling those two know, for suggesting one: the words' own, then their forms. */
    fun originalSpellings(): Sequence<String> = byLemma.keys.asSequence() + numbersBySpelling.keys.asSequence()

    // A gloss in today's spelling or words, with the King James one: favor → favour.
    private fun kingJamesWordsOf(heads: Set<String>, inBible: (String) -> Boolean): Set<String> {
        val out = HashSet(heads)
        for (head in heads) {
            kingJamesWording(listOf(head), inBible)?.forEach { if (it.size == 1) out += it[0] }
        }
        return out
    }

    companion object {
        /** Words in more verses than this mean nothing more for a search in English: God, Lord, "the". */
        const val MAX_VERSES = 500
        const val MIN_RENDERING_SHARE = 20
        const val MIN_COMBINED_SHARE = 80
        // Below this a single verse is a big share: H7293 rahav is "strength" in one verse of two.
        const val MIN_VERSES_FOR_RENDERINGS = 4

        const val WORD_FORMS = "word_forms.tsv"
        const val MODERN_KJV = "modern_kjv.tsv"
        const val ORIGINAL_WORDS = "original_words.tsv"
        const val ORIGINAL_FORMS = "original_forms.tsv"

        fun load(open: (String) -> InputStream): SearchLexicon {
            fun read(name: String) = open(name).bufferedReader().use { it.readText() }
            return parse(read(WORD_FORMS), read(MODERN_KJV), read(ORIGINAL_WORDS), originalForms = { read(ORIGINAL_FORMS) })
        }

        fun parse(wordForms: String, modernKjv: String, originalWords: String, originalForms: () -> String = { "" }): SearchLexicon {
            val families = dataLines(wordForms).map { it.split(' ') }.filter { it.size > 1 }
            val modern = dataLines(modernKjv).mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val kjv = line.substring(tab + 1).split('|').map { it.split(' ') }
                line.substring(0, tab).split('|').map { it.split(' ') to kjv }
            }.flatten()
            val originals = dataLines(originalWords).mapNotNull(::parseOriginal)
            return SearchLexicon(families, modern, originals, originalForms)
        }

        private fun dataLines(text: String) = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()

        // Sense, language, word, transliteration, meaning, verse count, renderings, verses.
        private fun parseOriginal(line: String): OriginalWord? {
            val cols = line.split('\t')
            if (cols.size < 8 || cols[0].length < 5) return null
            // "love:75*": love in 75% of its verses, worth marking there.
            val renderings = HashMap<String, Int>()
            val marked = HashSet<String>()
            for (part in cols[6].split(',')) {
                val colon = part.indexOf(':')
                if (colon <= 0) continue
                val rendering = part.substring(0, colon)
                renderings[rendering] = part.substring(colon + 1).removeSuffix("*").toIntOrNull() ?: continue
                if (part.endsWith('*')) marked += rendering
            }
            return OriginalWord(
                key = cols[0],
                language = when (cols[1]) {
                    "G" -> "Greek"
                    "A" -> "Aramaic"
                    else -> "Hebrew"
                },
                lemma = cols[2],
                transliteration = cols[3],
                gloss = cols[4],
                verseCount = cols[5].toIntOrNull() ?: return null,
                renderings = renderings,
                marked = marked,
                encodedVerses = cols[7]
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
