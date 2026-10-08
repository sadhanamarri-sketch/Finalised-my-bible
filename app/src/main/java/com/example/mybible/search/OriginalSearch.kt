package com.example.mybible.search

import com.example.mybible.model.OriginalWordCard
import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.SearchSource
import com.example.mybible.model.Verse
import java.text.Normalizer
import java.util.BitSet

/**
 * A search for a word of the Bible's own languages, named by:
 *
 * - its letters: ἀγάπη, or any form the text has (ἠγάπησεν); Hebrew with or without its vowel
 *   points and with its prefixes (וַיֹּאמֶר is "and" + amar);
 * - its Strong's number: G26, H2617, or one sense of it, G863H;
 * - STEPBible's transliteration of it (agape, chesed), when the King James text doesn't have
 *   the word: "amen" and "abba" are English searches.
 *
 * Finds every verse the word is in, whatever the King James calls it there, marking the English
 * words that usually render it. A word's meanings are chips: aphiēmi's "forgive" can stay on and
 * "permit" go off.
 */
class OriginalSearch(private val index: BibleIndex, private val lexicon: SearchLexicon) {

    /** The verses of the word(s) [query] names by its letters or number; null when it does neither. */
    fun search(query: String, disabled: Set<String>): SearchOutcome? {
        strongsWords(query)?.let { return outcome(listOf(it), disabled) }
        if (!hasOriginalLetter(query)) return null
        // A name of two words is one: Ἄρειος Πάγος.
        spelled(query).takeIf { it.isNotEmpty() }?.let { return outcome(listOf(it), disabled) }
        val typed = query.split(separators).filter(::hasOriginalLetter)
        val found = typed.map(::spelled)
        if (found.any { it.isEmpty() }) {
            return SearchOutcome(suggestion = if (typed.size == 1) suggestionFor(originalKey(typed[0])) else null)
        }
        return outcome(found, disabled)
    }

    /** The verses of the word STEPBible transliterates as [query] (agape, chesed, kyrios), or null. */
    fun searchTransliteration(query: String, disabled: Set<String>): SearchOutcome? {
        val words = wordsOf(query)
        val key = latinKey(query)
        if (words.isEmpty() || key.length < 3) return null
        // English: the King James has the words, or today's English for them (ego: pride).
        if (key !in WORDS_NOT_NAMES && words.all(index::contains)) return null
        if (lexicon.kingJamesWording(words, index::contains) != null) return null
        val found = transliterated(key)
        return if (found.isEmpty()) null else outcome(listOf(found), disabled)
    }

    // ---- what the query names ----

    private fun strongsWords(query: String): List<OriginalWord>? {
        val match = strongsNumber.matchEntire(query.trim()) ?: return null
        val number = match.groupValues[1].uppercase() + match.groupValues[2].padStart(4, '0')
        val sense = match.groupValues[3].uppercase()
        if (sense.isNotEmpty()) lexicon.original(number + sense)?.let { return listOf(it) }
        return lexicon.originalsNumbered(number)
    }

    /**
     * The words [typed] spells: the ones it's the spelling of first, then the ones it's a form of,
     * each by how many verses it's in. Pointed Hebrew keeps to the word pointed so: חֶסֶד is
     * chesed, kindness, where חסד is also chasad, to be kind.
     */
    private fun spelled(typed: String): List<OriginalWord> {
        val key = originalKey(typed)
        var lemmas = lexicon.numbersWithLemma(key)
        var forms = lexicon.numbersWithForm(key)
        // A Hebrew word can carry "and", "the", "in", "as", "to", "from" or "that": וּבַשָּׁמַיִם.
        var rest = key
        while (lemmas.isEmpty() && forms.isEmpty() && rest.length >= 3 && rest[0] in HEBREW_PREFIXES) {
            rest = rest.substring(1)
            lemmas = lexicon.numbersWithLemma(rest)
            forms = lexicon.numbersWithForm(rest)
        }
        if (typed.any { it in HEBREW_POINTS }) {
            val pointed = pointedKey(typed)
            val exact = lemmas.filter { n -> lexicon.originalsNumbered(n).any { pointed.endsWith(pointedKey(it.lemma)) } }
            if (exact.isNotEmpty()) return exact.flatMap(lexicon::originalsNumbered)
        }
        val byVerses = compareByDescending<String> { n -> lexicon.originalsNumbered(n).sumOf { it.verseCount } }
        val numbers = lemmas.sortedWith(byVerses) + (forms - lemmas.toSet()).sortedWith(byVerses)
        return numbers.flatMap(lexicon::originalsNumbered)
    }

    // STEPBible's spelling, then the ones people often use instead: kyrios for kurios (y for u),
    // ecclesia for ekklesia, tzedakah for tsedaqah, hesed for chesed.
    private fun transliterated(key: String): List<OriginalWord> {
        val tries = linkedSetOf(key, key.replace('y', 'u'), key.replace('c', 'k'), key.replace("tz", "ts"))
        TRANSLITERATIONS[key]?.let { tries += it }
        for (spelling in tries) {
            val words = lexicon.originalsTransliterated(spelling)
            if (words.isEmpty()) continue
            return words.map { it.number }.distinct()
                .sortedByDescending { n -> lexicon.originalsNumbered(n).sumOf { it.verseCount } }
                .flatMap(lexicon::originalsNumbered)
        }
        return emptyList()
    }

    /** The word itself for the spelling nearest [key], when it's off by a letter or two. */
    private fun suggestionFor(key: String): String? {
        if (key.length < 4) return null
        val limit = if (key.length >= 7) 2 else 1
        var best: String? = null
        var bestDistance = limit + 1
        for (spelling in lexicon.originalSpellings()) {
            val distance = editDistance(key, spelling, limit)
            if (distance < bestDistance) {
                best = spelling
                bestDistance = distance
            }
        }
        val numbers = best?.let { lexicon.numbersWithLemma(it) + lexicon.numbersWithForm(it) }.orEmpty()
        return numbers.flatMap(lexicon::originalsNumbered).maxByOrNull { it.verseCount }?.lemma?.substringBefore(',')
    }

    // ---- the verses ----

    // A chip: one meaning of one word, all the senses that have it (Joseph the patriarch and
    // Joseph of Nazareth are both "Joseph"; shalom's "peace" and the altar Jehovah-shalom's "Peace").
    private class Meaning(val senses: List<OriginalWord>, val verses: BitSet) {
        val word = senses[0]
        val ids = senses.map { SOURCE_PREFIX + it.key }
        var label = word.gloss
    }

    /** [groups]: for each word of the query, the senses it can be. A verse needs one of each. */
    private fun outcome(groups: List<List<OriginalWord>>, disabled: Set<String>): SearchOutcome {
        if (groups.isEmpty() || groups.any { it.isEmpty() }) return SearchOutcome()
        val meanings = groups.map { group ->
            group.groupBy { it.number to it.gloss.lowercase() }.values
                .map { senses -> Meaning(senses, BitSet(index.size).apply { senses.forEach { or(versesOf(it)) } }) }
                .filter { it.verses.cardinality() > 0 }
        }
        val all = meanings.flatten()
        fun on(m: Meaning) = m.ids.none { it in disabled }
        val matching = meanings.map { group -> BitSet(index.size).apply { group.filter(::on).forEach { or(it.verses) } } }
        val found = matching.reduceOrNull { a, b -> (a.clone() as BitSet).apply { and(b) } } ?: BitSet()

        // A word's meanings are chips when it has more than one, the most used first and past
        // a few, the rest on one chip (davar has seventeen). Each says what it brings with the
        // query's other words there, whether or not it's on: a chip that's off still says what
        // it would bring back.
        val chosen = meanings.filter { it.size > 1 }.flatten()
        labelChips(chosen)
        val sources = meanings.withIndex().filter { it.value.size > 1 }.flatMap { (i, group) ->
            val others = matching.filterIndexed { j, _ -> j != i }.reduceOrNull { a, b -> (a.clone() as BitSet).apply { and(b) } }
            fun chip(chipped: List<Meaning>, label: String): SearchSource {
                val verses = BitSet(index.size).apply { chipped.forEach { or(it.verses) } }
                others?.let { verses.and(it) }
                return SearchSource(chipped.flatMap { it.ids }, label, verses.cardinality(), chipped.all(::on))
            }
            val sorted = group.sortedByDescending { it.verses.cardinality() }
            if (sorted.size <= MAX_MEANING_CHIPS + 1) sorted.map { chip(listOf(it), it.label) } else {
                sorted.take(MAX_MEANING_CHIPS).map { chip(listOf(it), it.label) } + chip(sorted.drop(MAX_MEANING_CHIPS), "Other meanings")
            }
        }

        val spellings = HashMap<String, HashMap<String, Int>>()
        val hits = ArrayList<SearchHit>(found.cardinality())
        var id = found.nextSetBit(0)
        while (id >= 0) {
            val here = all.filter { on(it) && it.verses.get(id) }
            val reasons = chosen.filter { it in here }
                .map { "${it.word.transliteration.substringBefore(',')}, meaning “${it.word.gloss}”" }.distinct()
            hits += hit(id, here.flatMapTo(HashSet()) { m -> m.senses.flatMap(lexicon::markedFormsOf) }, reasons, spellings)
            id = found.nextSetBit(id + 1)
        }
        val cards = groups.flatten().groupBy { it.number }.values.take(MAX_WORD_CARDS).map { card(it, spellings) }
        return SearchOutcome(hits = hits, exactCount = hits.size, sources = sources, originalWords = cards)
    }

    private fun versesOf(word: OriginalWord): BitSet {
        val out = BitSet(index.size)
        for (ref in word.verses) {
            val id = index.idOf(ref)
            if (id >= 0) out.set(id)
        }
        return out
    }

    // Each chip's meaning, with the word it's of when they're several words, and then the
    // language or the number when two still read the same (amar "to say" in Hebrew and Aramaic).
    private fun labelChips(meanings: List<Meaning>) {
        val severalWords = meanings.map { it.word.transliteration }.distinct().size > 1
        for (m in meanings) {
            m.label = if (severalWords) "${m.word.transliteration.substringBefore(',')} · ${m.word.gloss}" else m.word.gloss
        }
        for (same in meanings.groupBy { it.label }.values) {
            if (same.size < 2) continue
            val byLanguage = same.map { it.word.language }.distinct().size == same.size
            for (m in same) m.label += if (byLanguage) " (${m.word.language})" else " (${displayNumber(m.word.number)})"
        }
    }

    private fun hit(id: Int, marked: Set<String>, reasons: List<String>, spellings: HashMap<String, HashMap<String, Int>>): SearchHit {
        val verse = index[id]
        val text = verse.text
        val highlights = ArrayList<IntRange>()
        forEachWord(text) { word, start, end ->
            if (word in marked) {
                highlights += start until end
                spellings.getOrPut(word) { HashMap() }.merge(text.substring(start, end), 1, Int::plus)
            }
        }
        return SearchHit(Verse(verse.book, verse.chapter, verse.number, text), text, highlights, reasons = reasons)
    }

    private fun card(senses: List<OriginalWord>, spellings: Map<String, Map<String, Int>>): OriginalWordCard {
        val word = senses[0]
        val verses = senses.associateWith { versesOf(it).cardinality() }
        val total = BitSet().apply { senses.forEach { or(versesOf(it)) } }.cardinality()
        // Its meanings by how often each is meant: elohim's "God", not its place names.
        val meanings = senses.groupBy { it.gloss.lowercase() }.values
            .map { group -> group[0].gloss to group.sumOf { verses.getValue(it) } }
            .sortedByDescending { it.second }
            .filterIndexed { i, (_, n) -> i == 0 || n * 20 >= total }
            .take(3).map { it.first }
        // Its distinctive renderings over all its senses, each sense counting for its share of
        // the verses, spelled as the text has them most (LORD, God).
        val weighted = HashMap<String, Double>()
        for (sense in senses) {
            for ((rendering, share) in sense.renderings) {
                if (rendering in sense.marked) weighted.merge(rendering, share.toDouble() * verses.getValue(sense), Double::plus)
            }
        }
        val kingJames = weighted.entries.filter { it.value >= MIN_CARD_SHARE * total }
            .sortedByDescending { it.value }.take(4)
            .map { (rendering, _) -> spellings[rendering]?.maxByOrNull { it.value }?.key ?: rendering }
        return OriginalWordCard(
            language = word.language,
            word = word.lemma,
            transliteration = word.transliteration,
            number = displayNumber(word.number),
            meanings = meanings,
            kingJames = kingJames,
            verseCount = total
        )
    }

    companion object {
        /** Prefix of a Greek or Hebrew sense's chip id: "orig:G0863H", as in SmartSearch. */
        const val SOURCE_PREFIX = "orig:"
        /** At most this many words get a card above the verses. */
        const val MAX_WORD_CARDS = 3
        /** A card names the King James words used in this % of the word's verses or more. */
        const val MIN_CARD_SHARE = 5
        /** A word's meanings past this many share an "Other meanings" chip. */
        const val MAX_MEANING_CHIPS = 5

        private val strongsNumber = Regex("([GgHh])0*(\\d{1,5})([A-Za-z]?)")
        private val separators = Regex("[\\s\\u05BE,.;:·]+")
        // ו and, ה the, ב in, כ as, ל to, מ from, ש that.
        private const val HEBREW_PREFIXES = "והבכלמש"
        // Vowel points, dagesh, and the dots of shin and sin; not the accents of the chant.
        private val HEBREW_POINTS = ('ְ'..'ּ').toSet() + setOf('ׁ', 'ׂ', 'ׇ')

        // Spellings people use for words STEPBible transliterates otherwise.
        private val TRANSLITERATIONS = mapOf(
            "hesed" to "chesed", "huios" to "uhios", "ruah" to "ruach", "shaddai" to "shadday",
            "kadosh" to "qadosh", "kabod" to "kavod", "nabi" to "navi", "berith" to "berit", "brit" to "berit",
            "tzedakah" to "tsedaqah", "tsedakah" to "tsedaqah", "gehenna" to "geenna", "paraclete" to "parakletos",
            "melech" to "melekh", "chai" to "chay", "yahweh" to "yehovah", "jahweh" to "yehovah", "yhwh" to "yehovah"
        )

        // Hebrew words people search for that the King James has only as a name: Chesed, Hesed,
        // Jehovah-shalom.
        private val WORDS_NOT_NAMES = setOf("chesed", "hesed", "shalom")

        /** G0026 → G26. */
        fun displayNumber(number: String) = number[0] + number.substring(1).trimStart('0')

        // A Hebrew word with its vowel points, without the accents of the chant: חֶ֫סֶד → חֶסֶד.
        private fun pointedKey(text: String): String {
            val out = StringBuilder(text.length)
            for (c in Normalizer.normalize(text, Normalizer.Form.NFD)) {
                if (isHebrewLetter(c)) out.append(originalKey(c.toString())) else if (c in HEBREW_POINTS) out.append(c)
            }
            return out.toString()
        }
    }
}
