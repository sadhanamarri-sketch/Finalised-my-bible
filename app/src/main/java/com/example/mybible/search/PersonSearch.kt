package com.example.mybible.search

import com.example.mybible.model.MatchedWord
import com.example.mybible.model.RelatedKind
import com.example.mybible.model.SearchHit
import com.example.mybible.model.SearchOutcome
import com.example.mybible.model.Verse
import java.util.BitSet

/**
 * A search that names a person and a subject ("Paul intercession", "David trust", "పౌలు ప్రార్థన")
 * also finds the subject in the person's own words, where no verse needs to name him: Paul is his
 * 13 letters, John his Gospel, letters and Revelation, Peter, James and Jude their letters, Solomon
 * Proverbs, Ecclesiastes and the Song, David the psalms titled his, Jesus his own words (the red
 * letters), and each prophet, Ezra, Nehemiah, Matthew and Luke their books. They come after the
 * verses with every word, under their own heading.
 *
 * A few subjects are read more widely than their word, as the person would put it: intercession is
 * praying for others (pray, prayer, supplication, "making mention"), the verses praying
 * for someone first, so "Paul intercession" finds his prayers for the churches (Romans 1:9,
 * Ephesians 1:16, Philippians 1:4, Colossians 1:9), none of which names him or intercession.
 */
class PersonSearch(private val index: BibleIndex, private val lexicon: SearchLexicon, private val verses: SmartSearch) {

    private val scopes = HashMap<Person, BitSet>()

    // The verses that are [person]'s own words.
    private fun scope(person: Person): BitSet = synchronized(scopes) {
        scopes.getOrPut(person) {
            BitSet(index.size).also { out ->
                for (id in 0 until index.size) {
                    val v = index[id]
                    if (person.has(v.book, v.chapter, v.isRedLetter)) out.set(id)
                }
            }
        }
    }

    /**
     * [outcome], the search for [query], with the subject in the person's own words after its
     * verses, when [query] names a person and something more. Otherwise [outcome] as it is.
     */
    fun search(query: String, outcome: SearchOutcome, disabled: Set<String> = emptySet()): SearchOutcome {
        val words = wordsOf(query).filter { it.length > 1 }
        val person = words.firstNotNullOfOrNull { PEOPLE[it] } ?: return outcome
        val subject = words.filter { PEOPLE[it] !== person && it !in STOPWORDS }
        if (subject.isEmpty()) return outcome
        val inScope = scope(person)

        // The subject's words read widely, each any of its words; the rest searched as usual, together.
        val wide = subject.mapNotNull { SUBJECTS[it] }.distinct()
            .let { all -> all.filter { s -> all.none { it !== s && it.words.containsAll(s.words) } } }
        val plain = subject.filter { it !in SUBJECTS }
        val plainHits = if (plain.isEmpty()) null else verses.search(plain.joinToString(" "), false, disabled)
            .let { if (it.closeMatches) emptyList() else it.hits }
            .mapNotNull { hit -> index.idOf(hit.verse.book, hit.verse.chapter, hit.verse.number).takeIf { it >= 0 && inScope.get(it) }?.let { it to hit } }
            .toMap()
        val wideForms = wide.map { s -> s.words.flatMapTo(HashSet()) { lexicon.formsOf(it) + it } }
        val found = (inScope.clone() as BitSet)
        plainHits?.let { hits -> found.and(BitSet(index.size).apply { hits.keys.forEach(::set) }) }
        wideForms.forEach { forms -> found.and(index.versesWithAny(forms)) }

        val forOthers = wide.any { it.forOthersFirst }
        val hits = ArrayList<Pair<Int, SearchHit>>()
        var id = found.nextSetBit(0)
        while (id >= 0) {
            val v = index[id]
            val plainHit = plainHits?.get(id)
            val marks = ArrayList<IntRange>(plainHit?.highlights.orEmpty())
            val matched = LinkedHashSet<MatchedWord>(plainHit?.matched.orEmpty())
            forEachWord(v.text) { word, start, end ->
                if (wideForms.any { word in it }) {
                    marks += start until end
                    matched += MatchedWord(word, MatchedWord.Kind.WORD)
                }
            }
            val rank = if (forOthers && FOR_OTHERS.containsMatchIn(v.text)) 0 else 1
            hits += rank to SearchHit(
                verse = Verse(v.book, v.chapter, v.number, v.text, v.isRedLetter),
                text = v.text,
                highlights = marks.sortedBy { it.first }.distinct(),
                related = true,
                reasons = plainHit?.reasons.orEmpty(),
                matched = matched.toList()
            )
            id = found.nextSetBit(id + 1)
        }
        val ordered = hits.sortedBy { it.first }.map { it.second }
        val note = (listOf(person.note) + wide.map { it.note }).joinToString(". ") + "."
        return merge(outcome, person, ordered, note)
    }

    /** Someone whose own words a search can look in. */
    class Person(
        /** What their words are: "Paul's letters". */
        val label: String,
        /** Which those are: "Romans to Philemon". */
        val note: String,
        val books: Set<String> = emptySet(),
        val psalms: Set<Int> = emptySet(),
        val redLetters: Boolean = false
    ) {
        fun has(book: String, chapter: Int, isRedLetter: Boolean): Boolean =
            book in books || (book == "Psalms" && chapter in psalms) || (redLetters && isRedLetter)
    }

    // A subject read more widely than its word: any of [words] (in any form) says it.
    private class Subject(val words: List<String>, val note: String, val forOthersFirst: Boolean = false)

    companion object {
        private val PAULS_LETTERS = setOf(
            "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians",
            "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon"
        )

        // The psalms whose titles call them David's.
        private val DAVIDS_PSALMS: Set<Int> = ((3..9) + (11..32) + (34..41) + (51..65) + (68..70) + 86 + 101 + 103 +
            (108..110) + 122 + 124 + 131 + 133 + (138..145)).toSet()

        val PAUL = Person("Paul's letters", "Romans to Philemon", PAULS_LETTERS)
        val JOHN = Person("John's writings", "His Gospel, his three letters and Revelation", setOf("John", "1 John", "2 John", "3 John", "Revelation"))
        val PETER = Person("Peter's letters", "1 and 2 Peter", setOf("1 Peter", "2 Peter"))
        val JAMES = Person("James's letter", "The letter of James", setOf("James"))
        val JUDE = Person("Jude's letter", "The letter of Jude", setOf("Jude"))
        val SOLOMON = Person("Solomon's books", "Proverbs, Ecclesiastes and the Song of Solomon", setOf("Proverbs", "Ecclesiastes", "Song of Solomon"))
        val DAVID = Person("David's psalms", "The 73 psalms whose titles call them his", psalms = DAVIDS_PSALMS)
        val JESUS = Person("Jesus' own words", "His words, as the red letters mark them", redLetters = true)

        // Each of these, their book (Jeremiah's Lamentations too, Luke's Acts).
        private fun book(name: String, vararg more: String) =
            Person(listOf(name, *more).joinToString(" and "), "The book of " + listOf(name, *more).joinToString(" and "), setOf(name, *more))

        private val ISAIAH = book("Isaiah")
        private val JEREMIAH = book("Jeremiah", "Lamentations")
        private val EZEKIEL = book("Ezekiel")
        private val DANIEL = book("Daniel")
        private val HOSEA = book("Hosea")
        private val JOEL = book("Joel")
        private val AMOS = book("Amos")
        private val OBADIAH = book("Obadiah")
        private val JONAH = book("Jonah")
        private val MICAH = book("Micah")
        private val NAHUM = book("Nahum")
        private val HABAKKUK = book("Habakkuk")
        private val ZEPHANIAH = book("Zephaniah")
        private val HAGGAI = book("Haggai")
        private val ZECHARIAH = book("Zechariah")
        private val MALACHI = book("Malachi")
        private val EZRA = book("Ezra")
        private val NEHEMIAH = book("Nehemiah")
        private val MATTHEW = book("Matthew")
        private val MARK = book("Mark")
        private val LUKE = book("Luke", "Acts")

        // By name. Not "mark", a word as often as a name (the mark of the beast).
        private val PEOPLE: Map<String, Person> = mapOf(
            "paul" to PAUL, "john" to JOHN, "peter" to PETER, "james" to JAMES, "jude" to JUDE,
            "solomon" to SOLOMON, "david" to DAVID, "jesus" to JESUS, "christ" to JESUS,
            "isaiah" to ISAIAH, "jeremiah" to JEREMIAH, "ezekiel" to EZEKIEL, "daniel" to DANIEL, "hosea" to HOSEA,
            "joel" to JOEL, "amos" to AMOS, "obadiah" to OBADIAH, "jonah" to JONAH, "micah" to MICAH, "nahum" to NAHUM,
            "habakkuk" to HABAKKUK, "zephaniah" to ZEPHANIAH, "haggai" to HAGGAI, "zechariah" to ZECHARIAH,
            "malachi" to MALACHI, "ezra" to EZRA, "nehemiah" to NEHEMIAH, "matthew" to MATTHEW, "luke" to LUKE
        )

        // By their Telugu names. Not యాకోబు (James, and Jacob too) nor యూదా (Jude, and Judah).
        private val TELUGU_PEOPLE: Map<String, Person> = mapOf(
            "పౌలు" to PAUL, "యోహాను" to JOHN, "పేతురు" to PETER, "సొలొమోను" to SOLOMON, "దావీదు" to DAVID,
            "యేసు" to JESUS, "క్రీస్తు" to JESUS, "యెషయా" to ISAIAH, "యిర్మీయా" to JEREMIAH, "యెహెజ్కేలు" to EZEKIEL,
            "దానియేలు" to DANIEL, "హోషేయ" to HOSEA, "యోవేలు" to JOEL, "ఆమోసు" to AMOS, "ఓబద్యా" to OBADIAH,
            "యోనా" to JONAH, "మీకా" to MICAH, "నహూము" to NAHUM, "హబక్కూకు" to HABAKKUK, "జెఫన్యా" to ZEPHANIAH,
            "హగ్గయి" to HAGGAI, "జెకర్యా" to ZECHARIAH, "మలాకీ" to MALACHI, "ఎజ్రా" to EZRA, "నెహెమ్యా" to NEHEMIAH,
            "మత్తయి" to MATTHEW, "మార్కు" to MARK, "లూకా" to LUKE
        )

        // Endings a Telugu name takes (పౌలును, దావీదునకు, యేసుతో); none other, so యోనా (Jonah) isn't యోనాతాను (Jonathan).
        private val TELUGU_ENDINGS = listOf("", "ను", "నకు", "కు", "తో", "యొక్క", "యు", "ని", "లో", "చేత", "గారి", "ద్వారా", "నుగూర్చి")

        // Not "intreat": in Paul's letters it's never prayer (we intreat, intreat him as a father).
        private val INTERCESSION = Subject(
            listOf("intercession", "pray", "prayer", "supplication", "mention"),
            "Intercession read as praying for others (pray, prayer, supplication, “making mention”), those praying for someone first",
            forOthersFirst = true
        )
        private val PRAYER = Subject(listOf("pray", "prayer", "supplication"), "Prayer read widely: pray, prayer, supplication")
        private val THANKS = Subject(listOf("thanks", "thank", "thankful", "thanksgiving"), "Thanksgiving read widely: thanks, thankful, thanksgiving")
        private val SUFFERING = Subject(
            listOf("suffer", "suffering", "affliction", "tribulation", "persecution", "distress"),
            "Suffering read widely: suffer, affliction, tribulation, persecution, distress"
        )
        private val ENCOURAGEMENT = Subject(
            listOf("comfort", "exhort", "exhortation", "consolation", "encourage"),
            "Encouragement read widely: comfort, exhort, consolation"
        )
        private val JOY = Subject(listOf("joy", "rejoice", "glad", "gladness"), "Joy read widely: joy, rejoice, glad")

        // The words of a search that stand for each, in the forms one might type.
        private val SUBJECTS: Map<String, Subject> = buildMap {
            fun put(subject: Subject, vararg words: String) = words.forEach { put(it, subject) }
            put(INTERCESSION, "intercession", "intercessions", "intercessor", "intercessory", "intercede", "intercedes", "interceded", "interceding")
            put(PRAYER, "pray", "prays", "prayed", "praying", "prayer", "prayers", "supplication", "supplications")
            put(THANKS, "thanks", "thank", "thankful", "thankfulness", "thanksgiving", "thanksgivings", "gratitude", "grateful")
            put(SUFFERING, "suffer", "suffering", "sufferings", "suffered")
            put(ENCOURAGEMENT, "encourage", "encouragement", "encouraged", "encouraging")
            put(JOY, "joy", "joyful", "rejoice", "rejoicing")
        }

        // A verse praying for someone: "for you", "of thee" (making mention of thee), "for Israel".
        private val FOR_OTHERS = Regex(
            "\\bfor (you|thee|them|us|me|him|all|israel|one another|the saints)\\b|\\bof (you|thee)\\b",
            RegexOption.IGNORE_CASE
        )

        // Telugu subjects read widely: prayer is ప్రార్థన, ప్రార్థించు, విజ్ఞాపన, విన్నపము (where a word starts).
        private val TELUGU_PRAYER = listOf("ప్రార్థ", "విజ్ఞాపన", "విన్నప")

        /**
         * The person a Telugu search's [words] name (as TeluguSearch.wordsOf gives them), with
         * the rest, its subject; null when they name none, or nothing more.
         */
        fun teluguPerson(words: List<String>): Pair<Person, List<String>>? {
            val person = words.firstNotNullOfOrNull(::teluguPersonOf) ?: return null
            val subject = words.filter { teluguPersonOf(it) !== person }
            return if (subject.isEmpty()) null else person to subject
        }

        private fun teluguPersonOf(word: String): Person? {
            for ((name, person) in TELUGU_PEOPLE) {
                if (!word.startsWith(name)) continue
                val ending = word.substring(name.length)
                // యేసుక్రీస్తు: both of his names at once.
                if (ending in TELUGU_ENDINGS || TELUGU_PEOPLE[ending] === person) return person
            }
            return null
        }

        /** What each word of a Telugu subject is found by: its own start, or a wider reading's. */
        fun teluguReading(word: String): List<String> =
            if (TELUGU_PRAYER.any { word.startsWith(it) || it.startsWith(word) && word.length >= 4 }) TELUGU_PRAYER else listOf(word)

        /**
         * [candidates] that are [person]'s own words and have each of [readings] (as from
         * [teluguReading]) where a Telugu word starts, as hits on their Telugu text.
         */
        fun teluguHits(person: Person, readings: List<List<String>>, candidates: List<Verse>): List<SearchHit> =
            candidates.mapNotNull { verse ->
                val text = verse.teluguText ?: return@mapNotNull null
                if (!person.has(verse.book, verse.chapter, verse.isRedLetter)) return@mapNotNull null
                val found = readings.map { TeluguSearch.wordStarts(text, it) }
                if (found.any { it.isEmpty() }) return@mapNotNull null
                val marks = found.flatten().distinct().sortedBy { it.first }
                SearchHit(verse, text, marks, related = true, matched = marks.map { MatchedWord(text.substring(it.first, it.last + 1), MatchedWord.Kind.WORD) }.distinct())
            }

        /** The note under a Telugu search's person heading. */
        fun teluguNote(person: Person, readings: List<List<String>>): String =
            person.note + if (readings.any { it === TELUGU_PRAYER }) ". Prayer read widely: ప్రార్థన, విజ్ఞాపన, విన్నపము." else "."

        /**
         * [outcome] with [personHits] after its verses, under [person]'s heading: those it doesn't
         * have already. The verses with all but one word give way to them.
         */
        fun merge(outcome: SearchOutcome, person: Person, personHits: List<SearchHit>, note: String): SearchOutcome {
            val kept = if (outcome.closeMatches) emptyList() else outcome.hits
            val have = kept.mapTo(HashSet()) { Triple(it.verse.book, it.verse.chapter, it.verse.number) }
            val more = personHits.filter { Triple(it.verse.book, it.verse.chapter, it.verse.number) !in have }
            if (more.isEmpty()) return outcome
            return outcome.copy(
                hits = kept + more,
                exactCount = kept.size,
                closeMatches = false,
                suggestion = null,
                eachWord = emptyList(),
                relatedKind = RelatedKind.PERSON,
                relatedHeading = "In ${person.label}",
                relatedNote = note
            )
        }
    }
}
