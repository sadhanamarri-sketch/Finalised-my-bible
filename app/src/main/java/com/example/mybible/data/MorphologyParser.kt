package com.example.mybible.data

/**
 * One term of a word's grammar ("Aorist", "Genitive"), with what it means in plain English, and
 * for a Hebrew verb stem a word or two for the margin of a definition ("Piel": "intensive").
 */
data class GrammarTerm(val label: String, val explanation: String? = null, val hint: String? = null)

/**
 * The grammar of a Greek word from its TAGNT code, in plain English: "V-AAI-3S" is Verb · Aorist
 * · Active · Indicative · 3rd person singular. TAGNT uses Robinson's codes: N noun, V verb, A
 * adjective, T article, and the pronouns P personal, R relative, D demonstrative, I
 * interrogative, X indefinite, F reflexive, S possessive, C reciprocal, K and Q correlative; then
 * spelled-out ADV, CONJ, COND, PREP, PRT, INJ. A word written as two (κἀκεῖνος, "and that one")
 * has both codes: "CONJ + G1565=D-NSM".
 */
object MorphologyParser {

    fun describe(raw: String): String = terms(raw).joinToString(" · ") { it.label }

    /** [raw]: the code ("V-AAI-3S"), maybe after its Strong's number ("G0025=V-AAI-3S"). */
    fun terms(raw: String): List<GrammarTerm> {
        // "CONJ + G1565=D-NSM": each word's grammar in turn.
        val parts = raw.split(" + ").map { it.substringAfter('=').trim().uppercase() }.filter { it.isNotEmpty() }
        val out = ArrayList<GrammarTerm>()
        for ((i, part) in parts.withIndex()) {
            if (i > 0) out += JOINED
            out += termsOf(part)
        }
        return out
    }

    private fun termsOf(code: String): List<GrammarTerm> {
        val segments = code.split('-')
        val head = segments[0]
        val rest = segments.drop(1)
        val out = ArrayList<GrammarTerm>()
        when (head) {
            "V" -> verb(rest, out)
            "N" -> nominal(NOUN, rest, out)
            "A" -> if (rest.firstOrNull() == "NUI") out += NUMBER else nominal(ADJECTIVE, rest, out)
            "T" -> nominal(ARTICLE, rest, out)
            "P" -> pronoun(PERSONAL, rest, out)
            "R", "D", "I", "X", "K", "C", "Q" -> nominal(PRONOUNS.getValue(head), rest, out)
            "F" -> pronoun(REFLEXIVE, rest, out)
            "S" -> possessive(rest, out)
            // Ἑβραϊστί, "in Hebrew"; Ἰουδαϊκῶς, "like a Jew".
            "ADV" -> if (rest == listOf("T")) out += PEOPLES_WAY else word(head, rest, out)
            else -> word(head, rest, out)
        }
        return out
    }

    // ADV, CONJ, PREP…, and what's added: PRT-N (not), ADV-I (where?), INJ-HEB (hosanna).
    private fun word(head: String, rest: List<String>, out: MutableList<GrammarTerm>) {
        out += WORDS[head] ?: GrammarTerm(head)
        rest.forEach { suffix -> SUFFIXES[suffix]?.let(out::add) }
    }

    // V-AAI-3S, V-2AAI-3S (second aorist), V-PAP-NSM (participle), V-AAN (infinitive).
    private fun verb(rest: List<String>, out: MutableList<GrammarTerm>) {
        out += VERB
        val tvm = rest.firstOrNull() ?: return
        val second = tvm.startsWith("2")
        val letters = tvm.removePrefix("2")
        if (letters.length < 3) return
        TENSES[letters[0]]?.let { out += if (second) it.copy(label = "${it.label} (second form)", explanation = "${it.explanation} $SECOND_FORM") else it }
        VOICES[letters[1]]?.let(out::add)
        MOODS[letters[2]]?.let(out::add)
        val inflection = rest.getOrNull(1) ?: return
        // V-AAM-2S-ARAM: ἐφφαθά, Aramaic.
        if (inflection in SUFFIXES) {
            rest.drop(1).forEach { suffix -> SUFFIXES[suffix]?.let(out::add) }
            return
        }
        if (letters[2] == 'P') caseNumberGender(inflection, out) else PERSONS[inflection.take(2)]?.let(out::add)
        rest.drop(2).forEach { suffix -> SUFFIXES[suffix]?.let(out::add) }
    }

    // N-NSF, N-NSM-P (a person's name), A-ASN-C (comparative), T-NSM, R-GSF.
    private fun nominal(what: GrammarTerm, rest: List<String>, out: MutableList<GrammarTerm>) {
        out += what
        rest.firstOrNull()?.let { caseNumberGender(it, out) }
        rest.drop(1).forEach { suffix -> SUFFIXES[suffix]?.let(out::add) }
    }

    // P-1GS (I, genitive singular: "my"), P-NSM ("he": αὐτός), F-3GSM (himself).
    private fun pronoun(what: GrammarTerm, rest: List<String>, out: MutableList<GrammarTerm>) {
        out += what
        val inflection = rest.firstOrNull() ?: return
        val person = inflection.firstOrNull()?.takeIf { it.isDigit() }
        if (person == null) {
            caseNumberGender(inflection, out)
            return
        }
        // Person, then case, number and maybe gender: "1GS", "3GSM".
        PERSONS["$person${inflection.getOrNull(2) ?: ' '}"]?.let(out::add)
        inflection.getOrNull(1)?.let { CASES[it] }?.let(out::add)
        inflection.getOrNull(3)?.let { GENDERS[it] }?.let(out::add)
    }

    // S-1SNSM: "my" (one owner, 1st person), then the case, number and gender it agrees in.
    private fun possessive(rest: List<String>, out: MutableList<GrammarTerm>) {
        out += POSSESSIVE
        val inflection = rest.firstOrNull() ?: return
        if (inflection.length >= 2) OWNERS[inflection.substring(0, 2)]?.let(out::add)
        if (inflection.length > 2) caseNumberGender(inflection.substring(2), out)
    }

    private fun caseNumberGender(code: String, out: MutableList<GrammarTerm>) {
        code.getOrNull(0)?.let { CASES[it] }?.let(out::add)
        code.getOrNull(1)?.let { NUMBERS[it] }?.let(out::add)
        code.getOrNull(2)?.let { GENDERS[it] }?.let(out::add)
    }

    private val VERB = GrammarTerm("Verb", "A word for an action or a state: love, be, go.")
    private val NOUN = GrammarTerm("Noun", "A word for a person, place or thing.")
    private val ADJECTIVE = GrammarTerm("Adjective", "Describes a noun: good, holy, great. Used alone, it can stand for one: “the holy (ones)”.")
    private val ARTICLE = GrammarTerm("Article", "“the”. Greek has no word for “a”.")
    private val NUMBER = GrammarTerm("Number", "A number word that doesn’t change its form: two, seven, twelve.")
    private val PERSONAL = GrammarTerm("Personal pronoun", "I, you, he, she, it, we, they.")
    private val REFLEXIVE = GrammarTerm("Reflexive pronoun", "myself, yourself, himself, themselves.")
    private val POSSESSIVE = GrammarTerm("Possessive pronoun", "my, your, our: whose something is.")
    private val PEOPLES_WAY = GrammarTerm("Adverb", "Made from a people’s name: “in Hebrew”, “in Greek”, “like a Jew”.")
    private val JOINED = GrammarTerm("+", "Two words written as one, like κἀγώ for καὶ ἐγώ, “and I”.")
    private const val SECOND_FORM = "The “second” form means the same; it’s just formed another way."

    private val PRONOUNS = mapOf(
        "R" to GrammarTerm("Relative pronoun", "who, which, that: it starts a clause about something just named."),
        "D" to GrammarTerm("Demonstrative pronoun", "this, that, these, those."),
        "I" to GrammarTerm("Interrogative pronoun", "who? what? which?"),
        "X" to GrammarTerm("Indefinite pronoun", "someone, something, anyone, a certain."),
        "K" to GrammarTerm("Correlative pronoun", "as much as, such as, as great as."),
        "C" to GrammarTerm("Reciprocal pronoun", "one another."),
        "Q" to GrammarTerm("Correlative pronoun", "how much? how great? of what kind?")
    )

    private val WORDS = mapOf(
        "ADV" to GrammarTerm("Adverb", "Says how, when or where: now, there, quickly."),
        "CONJ" to GrammarTerm("Conjunction", "Joins words or clauses: and, but, for, or."),
        "COND" to GrammarTerm("Conditional", "“if”: what follows depends on it."),
        "PREP" to GrammarTerm("Preposition", "in, into, from, to, with, by, through…"),
        "PRT" to GrammarTerm("Particle", "A small word that adds emphasis, a “not”, or makes a question."),
        "INJ" to GrammarTerm("Interjection", "An exclamation: O!, woe!, behold!")
    )

    private val SUFFIXES = mapOf(
        "N" to GrammarTerm("Negative", "Says no: not, no one, neither, never."),
        "I" to GrammarTerm("Question word", "Asks: where? when? how? why?"),
        "C" to GrammarTerm("Comparative", "more, -er: greater, older."),
        "S" to GrammarTerm("Superlative", "most, -est: greatest, least."),
        "P" to GrammarTerm("Name of a person", "A proper name: Peter, Mary."),
        "L" to GrammarTerm("Name of a place", "A proper name for a place: Jerusalem, Galilee."),
        "T" to GrammarTerm("Title", "A word used as a name or title: God, Lord, Christ, the devil."),
        "PG" to GrammarTerm("Name of a people", "Jew, Israelite, Levite: what someone is by people or tribe."),
        "LG" to GrammarTerm("Name of a people", "Named after a place: Galilean, Samaritan."),
        "LI" to GrammarTerm("Letter", "A letter of the alphabet used as a word: Alpha, Omega."),
        "ARAM" to GrammarTerm("Aramaic word", "Kept in Aramaic, the language Jesus spoke: talitha, abba."),
        "HEB" to GrammarTerm("Hebrew word", "Kept in Hebrew: hosanna, amen, sabaoth.")
    )

    private val TENSES = mapOf(
        'P' to GrammarTerm("Present", "An action going on, or that keeps happening: “he loves”, “he is loving”."),
        'I' to GrammarTerm("Imperfect", "An action going on in the past: “he was loving”, “he used to love”."),
        'F' to GrammarTerm("Future", "What will happen: “he will love”."),
        'A' to GrammarTerm("Aorist", "An action seen as a whole, a completed act, usually in the past: “he loved”."),
        'R' to GrammarTerm("Perfect", "Done in the past with lasting results: “he has loved”, “it is written”."),
        'L' to GrammarTerm("Pluperfect", "Done before a time in the past, with lasting results: “he had loved”.")
    )

    private val VOICES = mapOf(
        'A' to GrammarTerm("Active", "The subject does the action: “he loved”."),
        'M' to GrammarTerm("Middle", "The subject acts for or on itself: “he washed (himself)”."),
        'P' to GrammarTerm("Passive", "The subject receives the action: “he was loved”."),
        'E' to GrammarTerm("Middle or passive", "The form is the same for both; the sentence shows which."),
        'D' to GrammarTerm("Middle form, active meaning", "Looks middle but means what an active verb would (“deponent”)."),
        'O' to GrammarTerm("Passive form, active meaning", "Looks passive but means what an active verb would (“deponent”)."),
        'N' to GrammarTerm("Middle or passive form, active meaning", "Looks middle or passive but means what an active verb would (“deponent”).")
    )

    private val MOODS = mapOf(
        'I' to GrammarTerm("Indicative", "States a fact or asks a question."),
        'S' to GrammarTerm("Subjunctive", "What may or might be: a possibility, a purpose (“that he might…”), or a gentle command."),
        'O' to GrammarTerm("Optative", "A wish: “may it be…”."),
        'M' to GrammarTerm("Imperative", "A command or request: “love!”, “go!”."),
        'N' to GrammarTerm("Infinitive", "“to love”: the verb used like a noun."),
        'P' to GrammarTerm("Participle", "“loving”, “having loved”: the verb used like an adjective or a noun (“the one who loves”).")
    )

    private val CASES = mapOf(
        'N' to GrammarTerm("Nominative", "The subject: who or what does the action, or is described."),
        'G' to GrammarTerm("Genitive", "“of”: whose it is, where it comes from, what kind it is."),
        'D' to GrammarTerm("Dative", "“to”, “for”, “with”, “by”: who it’s given to, or by what means."),
        'A' to GrammarTerm("Accusative", "The object: who or what receives the action."),
        'V' to GrammarTerm("Vocative", "Calling someone: “O Lord”, “Father!”.")
    )

    private val NUMBERS = mapOf(
        'S' to GrammarTerm("Singular", "One."),
        'P' to GrammarTerm("Plural", "More than one.")
    )

    private val GENDERS = mapOf(
        'M' to GrammarTerm("Masculine", "Grammatical gender. Words that go together share it; it isn’t always about male and female."),
        'F' to GrammarTerm("Feminine", "Grammatical gender. Words that go together share it; it isn’t always about male and female."),
        'N' to GrammarTerm("Neuter", "Grammatical gender, neither masculine nor feminine. Words that go together share it.")
    )

    private val PERSONS = mapOf(
        "1S" to GrammarTerm("1st person singular", "I, me, my."),
        "1P" to GrammarTerm("1st person plural", "we, us, our."),
        "2S" to GrammarTerm("2nd person singular", "you (one person), your."),
        "2P" to GrammarTerm("2nd person plural", "you (more than one), your."),
        "3S" to GrammarTerm("3rd person singular", "he, she or it."),
        "3P" to GrammarTerm("3rd person plural", "they."),
        // A personal pronoun's person with no number in its code.
        "1 " to GrammarTerm("1st person", "I, we."),
        "2 " to GrammarTerm("2nd person", "you."),
        "3 " to GrammarTerm("3rd person", "he, she, it, they.")
    )

    // Whose, for a possessive pronoun.
    private val OWNERS = mapOf(
        "1S" to GrammarTerm("my", "One owner, speaking: my, mine."),
        "1P" to GrammarTerm("our", "Owners speaking: our, ours."),
        "2S" to GrammarTerm("your (one person)", "One owner, spoken to: your, yours."),
        "2P" to GrammarTerm("your (more than one)", "Owners spoken to: your, yours.")
    )
}
