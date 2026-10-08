package com.example.mybible.data

import com.example.mybible.model.HebrewWord

/**
 * A Hebrew word of the interlinear in its parts, as TAHOT marks them with "/": בְּ/רֵאשִׁ֖ית is
 * בְּ, "in", and רֵאשִׁית, "beginning". Each part has its letters, gloss and grammar. [main] is
 * the word itself, the one the Strong's number is for; the others are its prefixes ("and",
 * "the", "in") and endings ("his", "-ward").
 */
data class HebrewWordPart(
    val hebrew: String,
    val gloss: String,
    val grammar: List<GrammarTerm>,
    val main: Boolean
)

/**
 * TAHOT's Hebrew and Aramaic: the grammar codes of the Open Scriptures Hebrew Bible (H or A for the
 * language, then a code for each part: "Hc/Vqw3ms" is "and" + a Qal narrative-past verb, he), the
 * parts of a word, and its transliteration written to read: "be./re.Shit" is be·re·SHIT, the
 * stressed syllable in capitals.
 */
object HebrewMorphology {

    /** The word's parts, or the whole word as one part when it has one, or its columns don't split alike. */
    fun parts(word: HebrewWord): List<HebrewWordPart> {
        val code = word.morphology.trim()
        val aramaic = code.startsWith("A")
        val codes = if (code.length > 1) code.substring(1).split('/') else emptyList()
        val letters = word.hebrew.split('/')
        val glosses = word.englishGloss.split('/')
        if (codes.size < 2 || letters.size != codes.size) {
            return listOf(HebrewWordPart(cleanWord(word.hebrew), cleanGloss(word.englishGloss), codes.flatMap { terms(it, aramaic) }, main = true))
        }
        // The word itself: the first part that's neither a prefix nor an ending.
        val main = codes.indexOfFirst { it.isNotEmpty() && it[0] !in PREFIX_CODES && it[0] != 'S' }.takeIf { it >= 0 } ?: 0
        // "//" is between two words written as one (בָּ֣א//גָ֑ד, "it has come//good fortune"): no part.
        return codes.indices.filter { codes[it].isNotEmpty() }.map { i ->
            HebrewWordPart(
                hebrew = cleanWord(letters[i]),
                gloss = partGloss(glosses.getOrElse(i) { "" }),
                grammar = terms(codes[i], aramaic),
                main = i == main
            )
        }
    }

    /** The word as one, without TAHOT's "/" between its parts and "\" before its punctuation. */
    fun cleanWord(raw: String): String = raw.split('/').joinToString("") { it.substringBefore('\\').trim() }

    /**
     * The gloss as one: "in/ beginning" is "in beginning", "give/ !" is "give!", and a question's
     * "¿/ has it heard" is "has it heard?".
     */
    fun cleanGloss(raw: String): String {
        val parts = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val out = StringBuilder()
        for (part in parts) {
            if (part == QUESTION) continue
            if (out.isNotEmpty() && part.any(Char::isLetterOrDigit)) out.append(' ')
            out.append(part)
        }
        if (QUESTION in parts && !out.endsWith("?")) out.append('?')
        return out.toString()
    }

    // A part's gloss: nothing for a question's "¿" or an ending's "!", which its grammar explains.
    private fun partGloss(raw: String): String = raw.trim().takeIf { it.any(Char::isLetterOrDigit) }.orEmpty()

    /**
     * STEPBible's transliteration, to read aloud: syllables were split by "." and the word's parts
     * by "/", and the stressed syllable has a capital ("be./re.Shit", "va/i.Yo.mer"). Now
     * syllables are split by "·" and the stressed one is in capitals: be·re·SHIT, vai·YO·mer.
     */
    fun cleanTransliteration(raw: String): String =
        raw.replace("/", "").replace('·', '.').split(' ').mapNotNull { word ->
            val syllables = word.substringBefore('\\').trim('-', '[', ']').split('.').filter { s -> s.any(Char::isLetter) }
            if (syllables.isEmpty()) null else syllables.joinToString("·") { s ->
                if (syllables.size > 1 && s.any(Char::isUpperCase)) s.uppercase() else s.lowercase()
            }
        }.joinToString(" ")

    /** The grammar of one part: "Ncfsa" is a noun, feminine, singular, absolute. */
    fun terms(code: String, aramaic: Boolean = false): List<GrammarTerm> {
        if (code.isEmpty()) return emptyList()
        val out = ArrayList<GrammarTerm>()
        val rest = code.substring(1)
        when (code[0]) {
            'C' -> out += CONJUNCTION
            'c' -> out += CONSECUTIVE
            'D' -> out += ADVERB
            'R' -> {
                out += PREPOSITION
                if (rest.startsWith("d")) out += WITH_THE
            }
            'T' -> out += when (val kind = rest.firstOrNull()) {
                'a' -> if (aramaic) ARAMAIC_ARTICLE else AFFIRMATION
                else -> PARTICLES[kind] ?: PARTICLE
            }
            'N' -> noun(rest, out)
            'A' -> adjective(rest, out)
            'P' -> pronoun(rest, out)
            'S' -> ending(rest, out)
            'V' -> verb(rest, aramaic, out)
            else -> out += GrammarTerm(code)
        }
        return out
    }

    /** What a verb stem named in a definition means: "Qal", "(Niphal)", "Hiph", "P'al". */
    fun stem(name: String): GrammarTerm? {
        val key = name.trim().trim('(', ')').lowercase().filter { it.isLetter() }
        if (key.isEmpty()) return null
        return STEM_SPELLINGS[key]
            ?: (HEBREW_STEMS.values + ARAMAIC_STEMS.values).firstOrNull { it.label.lowercase().filter(Char::isLetter) == key }
    }

    // Nc: a noun; Nt: a title (Pharaoh); Ng: a people; Np: a name (m man, f woman, l place, t God's).
    private fun noun(rest: String, out: MutableList<GrammarTerm>) {
        when (rest.firstOrNull()) {
            'p' -> {
                out += NAMES[rest.getOrNull(1)] ?: GrammarTerm("Name", "A proper name.")
                return
            }
            'g' -> out += PEOPLE
            't' -> out += TITLE
            else -> out += NOUN
        }
        genderNumberState(rest.drop(1), out)
    }

    // Aa adjective, Ac number, Ao ordinal, Ag a people's name.
    private fun adjective(rest: String, out: MutableList<GrammarTerm>) {
        out += when (rest.firstOrNull()) {
            'c' -> CARDINAL
            'o' -> ORDINAL
            'g' -> PEOPLE
            else -> ADJECTIVE
        }
        genderNumberState(rest.drop(1), out)
    }

    // Pp personal (person, gender, number: Pp3ms "he"), Pi interrogative, Pd demonstrative, Pr relative, Pf indefinite.
    private fun pronoun(rest: String, out: MutableList<GrammarTerm>) {
        out += PRONOUNS[rest.firstOrNull()] ?: GrammarTerm("Pronoun", "Stands for a noun: he, this, who.")
        val inflection = rest.drop(1)
        if (inflection.firstOrNull()?.isDigit() == true) subject(inflection, out) else genderNumberState(inflection, out)
    }

    // Sp3ms "his/him", Sd "-ward", Sh and Sn an extra letter.
    private fun ending(rest: String, out: MutableList<GrammarTerm>) {
        out += when (rest.firstOrNull()) {
            'p' -> {
                val pgn = rest.drop(1)
                val meaning = ENDING_MEANINGS[commonGender(pgn)]
                GrammarTerm(
                    "Pronoun ending",
                    meaning?.let { "“$it” (${personGenderNumber(pgn)}), joined to the end of the word." }
                        ?: "A pronoun joined to the end of the word: “his”, “my”, “them”."
                )
            }
            'd' -> GrammarTerm("Direction ending", "-ward, toward: אַרְצָה, “to the ground”.")
            'h', 'n' -> GrammarTerm("Extra letter", "A letter added at the end, often for emphasis; it doesn’t change the meaning.")
            else -> GrammarTerm("Ending", "Something joined to the end of the word.")
        }
    }

    // Vqw3ms: stem (q Qal), conjugation (w narrative past), person, gender, number. A participle
    // has gender, number and state instead; an infinitive, nothing more.
    private fun verb(rest: String, aramaic: Boolean, out: MutableList<GrammarTerm>) {
        out += VERB
        val stem = rest.getOrNull(0) ?: return
        (if (aramaic) ARAMAIC_STEMS[stem] else HEBREW_STEMS[stem])?.let(out::add)
        val conjugation = rest.getOrNull(1) ?: return
        CONJUGATIONS[conjugation]?.let(out::add)
        val inflection = rest.drop(2)
        when (conjugation) {
            'r', 's' -> genderNumberState(inflection, out)
            'a', 'c' -> Unit
            else -> subject(inflection, out)
        }
    }

    private fun genderNumberState(code: String, out: MutableList<GrammarTerm>) {
        code.getOrNull(0)?.let { GENDERS[it] }?.let(out::add)
        code.getOrNull(1)?.let { NUMBERS[it] }?.let(out::add)
        code.getOrNull(2)?.let { STATES[it] }?.let(out::add)
    }

    // Who a verb's action is by, or who a personal pronoun is: "3ms" he, "1cs" I, "2fp" you (women).
    private fun subject(pgn: String, out: MutableList<GrammarTerm>) {
        val who = SUBJECTS[commonGender(pgn)] ?: return
        out += GrammarTerm(who, "${personGenderNumber(pgn).replaceFirstChar(Char::uppercase)}.")
    }

    // "3ms": "3rd person, masculine, singular".
    private fun personGenderNumber(pgn: String): String = listOfNotNull(
        when (pgn.getOrNull(0)) { '1' -> "1st person"; '2' -> "2nd person"; '3' -> "3rd person"; else -> null },
        when (pgn.getOrNull(1)) { 'm' -> "masculine"; 'f' -> "feminine"; else -> null },
        when (pgn.getOrNull(2)) { 's' -> "singular"; 'p' -> "plural"; 'd' -> "dual"; else -> null }
    ).joinToString(", ")

    // "Both" genders and "common" gender read alike: 1bs is 1cs, "I".
    private fun commonGender(pgn: String): String = if (pgn.getOrNull(1) == 'b') pgn.replaceRange(1, 2, "c") else pgn

    // Prefixes: conjunctions, prepositions and particles ("the"), which come before the word.
    private const val PREFIX_CODES = "CcRT"
    // TAHOT's gloss for הֲ, which makes a question.
    private const val QUESTION = "¿"

    private val CONJUNCTION = GrammarTerm("Conjunction", "and, but, or, then. A וְ at the start of a word is “and”.")
    private val CONSECUTIVE = GrammarTerm("Conjunction", "“and”: the וַ or וְ joined to a verb that carries a story or a series of actions on.")
    private val ADVERB = GrammarTerm("Adverb", "Says how, when or where: now, there, again.")
    private val PREPOSITION = GrammarTerm("Preposition", "in, to, from, like, on: בְּ, לְ, מִ and כְּ are joined to the next word.")
    private val WITH_THE = GrammarTerm("with “the”", "The “the” is inside the preposition: בַּ is “in the”.")
    private val PARTICLE = GrammarTerm("Particle", "A small word that does a job in the sentence.")
    private val AFFIRMATION = GrammarTerm("Affirmation", "surely, indeed.")
    private val ARAMAIC_ARTICLE = GrammarTerm("Article", "“the”: in Aramaic, an א at the end of the word.")
    private val NOUN = GrammarTerm("Noun", "A word for a person, place or thing.")
    private val TITLE = GrammarTerm("Title", "A noun used as a name or title: Pharaoh, Baal, the Passover.")
    private val PEOPLE = GrammarTerm("Name of a people", "Levite, Philistine, Hebrew: what someone is by people or tribe.")
    private val ADJECTIVE = GrammarTerm("Adjective", "Describes a noun: good, great, holy. Used alone, it can stand for one: “the wicked”.")
    private val CARDINAL = GrammarTerm("Number", "one, two, ten.")
    private val ORDINAL = GrammarTerm("Ordinal number", "first, second, tenth.")
    private val VERB = GrammarTerm("Verb", "A word for an action or a state.")

    private val PARTICLES = mapOf(
        'd' to GrammarTerm("Article", "“the”: הַ joined to the front of a word."),
        'o' to GrammarTerm("Object marker", "אֵת marks what the verb is done to. English leaves it out."),
        'n' to GrammarTerm("Negative", "not, no."),
        'r' to GrammarTerm("Relative", "who, which, that: אֲשֶׁר or שֶׁ."),
        'i' to GrammarTerm("Question marker", "הֲ at the start of a word makes a question."),
        'm' to GrammarTerm("Demonstrative", "this, that, these."),
        'j' to GrammarTerm("Interjection", "behold!, please!, alas!"),
        'c' to GrammarTerm("Conjunction", "for, because, that, if, when."),
        'e' to GrammarTerm("Exhortation", "“please”, “now”: a word that urges.")
    )

    private val NAMES = mapOf(
        'm' to GrammarTerm("Name of a man", "A proper name: Abraham, David."),
        'f' to GrammarTerm("Name of a woman", "A proper name: Sarah, Ruth."),
        'l' to GrammarTerm("Name of a place", "A proper name for a place: Jerusalem, Egypt."),
        't' to GrammarTerm("God’s name", "YHWH, which the King James prints as “the LORD”, or another name for God.")
    )

    private val PRONOUNS = mapOf(
        'p' to GrammarTerm("Personal pronoun", "I, you, he, she, we, they."),
        'd' to GrammarTerm("Demonstrative pronoun", "this, that, these."),
        'i' to GrammarTerm("Interrogative pronoun", "who? what? why?"),
        'r' to GrammarTerm("Relative pronoun", "who, which, that."),
        'f' to GrammarTerm("Indefinite pronoun", "someone, anything.")
    )

    private val GENDERS = mapOf(
        'm' to GrammarTerm("Masculine", "Grammatical gender. Words that go together share it."),
        'f' to GrammarTerm("Feminine", "Grammatical gender. Words that go together share it."),
        'b' to GrammarTerm("Masculine or feminine", "This word is used with both genders."),
        'c' to GrammarTerm("Either gender", "The same form for masculine and feminine.")
    )

    private val NUMBERS = mapOf(
        's' to GrammarTerm("Singular", "One."),
        'p' to GrammarTerm("Plural", "More than one."),
        'd' to GrammarTerm("Dual", "Two, or a pair: hands, eyes.")
    )

    private val STATES = mapOf(
        'a' to GrammarTerm("Absolute", "The word on its own, not joined to the next one."),
        'c' to GrammarTerm("Construct", "Joined to the next word, “X of Y”: דְּבַר יְהוָה, “word of the LORD”."),
        'd' to GrammarTerm("With “the”", "In Aramaic, “the” is an ending on the word (the “emphatic” state).")
    )

    private val CONJUGATIONS = mapOf(
        'p' to GrammarTerm("Perfect", "A completed action, usually past: “he said”, “he has said” (qatal)."),
        'q' to GrammarTerm("Perfect with “and”", "וְ + perfect: carries a future or a command on: “and he will say” (weqatal)."),
        'i' to GrammarTerm("Imperfect", "An action not complete: future, repeated, or wished: “he will say”, “he used to say” (yiqtol)."),
        'w' to GrammarTerm("Narrative past", "וַ + imperfect: how Hebrew tells a story: “and he said” (wayyiqtol)."),
        'u' to GrammarTerm("Imperfect with “and”", "וְ + imperfect, often a purpose: “so that he may…” (weyiqtol)."),
        'h' to GrammarTerm("Cohortative", "“let me…”, “let us…”."),
        'j' to GrammarTerm("Jussive", "“let him…”, “may he…”: a wish or mild command."),
        'v' to GrammarTerm("Imperative", "A command: “say!”, “go!”."),
        'r' to GrammarTerm("Participle", "“saying”, “one who says”: ongoing action, or the verb used like a noun."),
        's' to GrammarTerm("Passive participle", "“said”, “blessed”: what has been done to someone or something."),
        'a' to GrammarTerm("Infinitive absolute", "The bare verb, often doubling another for emphasis: “you shall surely die”."),
        'c' to GrammarTerm("Infinitive construct", "“to say”, “when he said”: the verb used like a noun.")
    )

    // The stems: the same root in different patterns, each with its own kind of meaning.
    private val HEBREW_STEMS = mapOf(
        'q' to GrammarTerm("Qal", "The simple pattern of the verb: “he broke”.", "simple"),
        'N' to GrammarTerm("Niphal", "Usually passive or reflexive: “it was broken”, “he hid himself”.", "passive"),
        'p' to GrammarTerm("Piel", "Often intensive, or making something so: “he smashed”, “he made holy”.", "intensive"),
        'P' to GrammarTerm("Pual", "The passive of the Piel: “it was smashed”.", "passive of Piel"),
        'h' to GrammarTerm("Hiphil", "Usually causative: “he made (someone) do it”, “he brought out”.", "causative"),
        'H' to GrammarTerm("Hophal", "The passive of the Hiphil: “he was made to…”, “he was brought out”.", "passive of Hiphil"),
        't' to GrammarTerm("Hithpael", "Reflexive or mutual: “he made himself holy”, “they met one another”.", "reflexive"),
        'v' to GrammarTerm("Hishtaphel", "A rare pattern, mostly for “bow down, worship”.", "bow down"),
        'u' to GrammarTerm("Hothpaal", "A rare passive of the Hithpael.", "passive"),
        'D' to GrammarTerm("Nithpael", "A rare reflexive pattern, like the Hithpael.", "reflexive"),
        'c' to GrammarTerm("Tiphil", "A rare causative pattern.", "causative"),
        'o' to GrammarTerm("Polel", "A pattern like the Piel, for some verbs.", "intensive"),
        'O' to GrammarTerm("Polal", "The passive of the Polel.", "passive"),
        'r' to GrammarTerm("Hithpolel", "A reflexive pattern like the Hithpael, for some verbs.", "reflexive"),
        'm' to GrammarTerm("Poel", "A pattern like the Piel, for some verbs.", "intensive"),
        'M' to GrammarTerm("Poal", "The passive of the Poel.", "passive"),
        'k' to GrammarTerm("Palel", "A rare intensive pattern.", "intensive"),
        'K' to GrammarTerm("Pulal", "The passive of the Palel.", "passive"),
        'Q' to GrammarTerm("Qal passive", "An old passive of the simple pattern.", "simple passive"),
        'l' to GrammarTerm("Pilpel", "A doubled pattern for repeated action.", "repeated"),
        'L' to GrammarTerm("Polpal", "The passive of the Pilpel.", "passive"),
        'f' to GrammarTerm("Hithpalpel", "A doubled reflexive pattern.", "reflexive"),
        'j' to GrammarTerm("Pealal", "A rare intensive pattern.", "intensive"),
        'i' to GrammarTerm("Pilel", "A rare intensive pattern.", "intensive"),
        'w' to GrammarTerm("Nithpalel", "A rare reflexive pattern.", "reflexive"),
        'y' to GrammarTerm("Nithpoel", "A rare reflexive pattern.", "reflexive"),
        'z' to GrammarTerm("Hithpoel", "A reflexive pattern like the Hithpael, for some verbs.", "reflexive")
    )

    private val ARAMAIC_STEMS = mapOf(
        'q' to GrammarTerm("Peal", "Aramaic’s simple pattern, like the Hebrew Qal.", "simple"),
        'Q' to GrammarTerm("Peil", "Aramaic’s simple passive.", "simple passive"),
        'u' to GrammarTerm("Hithpeel", "Aramaic’s passive or reflexive of the simple pattern.", "passive"),
        'p' to GrammarTerm("Pael", "Aramaic’s intensive pattern, like the Hebrew Piel.", "intensive"),
        'P' to GrammarTerm("Ithpaal", "The passive or reflexive of the Pael.", "passive of Pael"),
        'M' to GrammarTerm("Hithpaal", "The passive or reflexive of the Pael.", "passive of Pael"),
        'a' to GrammarTerm("Aphel", "Aramaic’s causative pattern, like the Hebrew Hiphil.", "causative"),
        'h' to GrammarTerm("Haphel", "Aramaic’s causative pattern, like the Hebrew Hiphil.", "causative"),
        's' to GrammarTerm("Saphel", "A rare Aramaic causative.", "causative"),
        'e' to GrammarTerm("Shaphel", "A rare Aramaic causative.", "causative"),
        'H' to GrammarTerm("Hophal", "The passive of the causative.", "passive of causative"),
        'i' to GrammarTerm("Ithpeel", "Aramaic’s passive or reflexive of the simple pattern.", "passive"),
        't' to GrammarTerm("Hishtaphel", "A rare reflexive causative.", "reflexive causative"),
        'v' to GrammarTerm("Ishtaphel", "A rare reflexive causative.", "reflexive causative"),
        'w' to GrammarTerm("Hithaphel", "A rare reflexive causative.", "reflexive causative"),
        'o' to GrammarTerm("Polel", "A pattern like the Pael, for some verbs.", "intensive"),
        'z' to GrammarTerm("Ithpoel", "A reflexive pattern for some verbs.", "reflexive"),
        'r' to GrammarTerm("Hithpolel", "A reflexive pattern for some verbs.", "reflexive"),
        'f' to GrammarTerm("Hithpalpel", "A doubled reflexive pattern.", "reflexive"),
        'b' to GrammarTerm("Hephal", "A rare passive.", "passive"),
        'c' to GrammarTerm("Tiphel", "A rare causative.", "causative"),
        'm' to GrammarTerm("Poel", "A pattern like the Pael, for some verbs.", "intensive"),
        'l' to GrammarTerm("Palpel", "A doubled pattern.", "repeated"),
        'L' to GrammarTerm("Ithpalpel", "A doubled reflexive pattern.", "reflexive"),
        'O' to GrammarTerm("Ithpolel", "A reflexive pattern for some verbs.", "reflexive"),
        'G' to GrammarTerm("Ittaphal", "A rare passive.", "passive")
    )

    // How the lexicon spells stems the codes don't: "(Hiph)", "(P'al)", "(Ithp'el)", "(Hothpael)".
    private val STEM_SPELLINGS: Map<String, GrammarTerm> =
        mapOf(
            "hiph" to HEBREW_STEMS.getValue('h'), "hoph" to HEBREW_STEMS.getValue('H'), "niph" to HEBREW_STEMS.getValue('N'),
            "hithp" to HEBREW_STEMS.getValue('t'), "hothpael" to HEBREW_STEMS.getValue('u'),
            "tiphel" to HEBREW_STEMS.getValue('c'), "hithpalel" to HEBREW_STEMS.getValue('r'),
            "pal" to ARAMAIC_STEMS.getValue('q'), "pil" to ARAMAIC_STEMS.getValue('Q'),
            "ithpael" to ARAMAIC_STEMS.getValue('P'), "ithpaal" to ARAMAIC_STEMS.getValue('P'),
            "ithpal" to ARAMAIC_STEMS.getValue('i'), "ithpel" to ARAMAIC_STEMS.getValue('i'), "ithpil" to ARAMAIC_STEMS.getValue('i'),
            "hithpal" to ARAMAIC_STEMS.getValue('u'), "hithpil" to ARAMAIC_STEMS.getValue('u')
        )

    // Who a verb's action is by, or who a personal pronoun is.
    private val SUBJECTS = mapOf(
        "1cs" to "I", "1cp" to "we",
        "2ms" to "you (a man)", "2fs" to "you (a woman)", "2mp" to "you (all)", "2fp" to "you (women)", "2cp" to "you (all)",
        "3ms" to "he", "3fs" to "she", "3mp" to "they", "3fp" to "they (women)", "3cp" to "they"
    )

    // A pronoun ending's meaning.
    private val ENDING_MEANINGS = mapOf(
        "1cs" to "my, me", "1cp" to "our, us",
        "2ms" to "your, you (a man)", "2fs" to "your, you (a woman)", "2mp" to "your, you (all)", "2fp" to "your, you (women)",
        "3ms" to "his, him, its", "3fs" to "her, its", "3mp" to "their, them", "3fp" to "their, them (women)"
    )
}
