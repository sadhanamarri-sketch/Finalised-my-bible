package com.example.mybible.data

import java.text.Normalizer

/**
 * How a Greek or Hebrew word sounds, spelled for an English reader: its syllables joined by hyphens,
 * the stressed one in capitals — ἀγάπη is ah-GAH-pay, בְּרֵאשִׁית (be·re·SHIT) is beh-reh-SHEET. In
 * the pronunciation usually taught for Biblical Greek and Hebrew, for the word as the verse has it.
 */
object Pronunciation {

    // ---- Greek: from the word itself, its accent marking the stress ----

    private const val GRAVE = '̀'
    private const val ACUTE = '́'
    private const val CIRCUMFLEX = '͂'
    private const val ROUGH = '̔'
    private const val DIAERESIS = '̈'

    private class Letter(
        val c: Char,
        var accent: Boolean = false,
        var circumflex: Boolean = false,
        var rough: Boolean = false,
        var diaeresis: Boolean = false
    ) {
        val vowel: Boolean get() = c in GREEK_VOWELS
    }

    // A syllable: the consonants before its vowel sound, the vowel sound (one or two letters), those after.
    private class Syllable(val onset: List<Letter>, val nucleus: List<Letter>, var coda: List<Letter> = emptyList()) {
        val stressed: Boolean get() = nucleus.any { it.accent }
    }

    /** The Greek word [word] (any spelling: with or without accents, capitals, a final ς) as it sounds. */
    fun greek(word: String): String = word.split(' ', '-').filter { it.isNotBlank() }.joinToString(" ") { greekWord(it) }.trim()

    private fun greekWord(word: String): String {
        val letters = greekLetters(word)
        if (letters.isEmpty()) return ""
        val syllables = greekSyllables(letters)
        if (syllables.isEmpty()) return letters.joinToString("") { consonantSound(it.c, null) }
        // The word's own accent: a second one (from a little word leaning on it, δοῦλόν μου) isn't stressed.
        val stress = syllables.indexOfFirst { it.stressed }.takeIf { syllables.size > 1 }
        return syllables.mapIndexed { i, s ->
            val next = syllables.getOrNull(i + 1)?.let { it.onset.firstOrNull() ?: it.nucleus.first() }
            val text = greekSyllableSound(s, i == 0, next)
            if (i == stress) text.uppercase() else text
        }.joinToString("-")
    }

    // The word's letters, lowercase, each with its accent, breathing and diaeresis; anything else dropped.
    private fun greekLetters(word: String): List<Letter> {
        val out = ArrayList<Letter>()
        for (ch in Normalizer.normalize(word, Normalizer.Form.NFD)) {
            when (ch) {
                GRAVE, ACUTE -> out.lastOrNull()?.accent = true
                CIRCUMFLEX -> out.lastOrNull()?.let { it.accent = true; it.circumflex = true }
                ROUGH -> out.lastOrNull()?.rough = true
                DIAERESIS -> out.lastOrNull()?.diaeresis = true
                else -> {
                    val lower = ch.lowercaseChar().let { if (it == 'ς') 'σ' else it }
                    if (lower in 'α'..'ω') out += Letter(lower)
                }
            }
        }
        return out
    }

    private fun greekSyllables(letters: List<Letter>): List<Syllable> {
        // The vowel sounds: one vowel, or two making one sound (αι, ου...) unless the second has a diaeresis.
        val nuclei = ArrayList<IntRange>()
        var i = 0
        while (i < letters.size) {
            if (!letters[i].vowel) {
                i++
                continue
            }
            val pair = letters.getOrNull(i + 1)?.takeIf { it.vowel && !it.diaeresis }?.let { "${letters[i].c}${it.c}" }
            val end = if (pair != null && pair in GREEK_DIPHTHONGS) i + 1 else i
            nuclei += i..end
            i = end + 1
        }
        if (nuclei.isEmpty()) return emptyList()
        val syllables = ArrayList<Syllable>()
        var onsetStart = 0
        for ((n, range) in nuclei.withIndex()) {
            val onset = letters.subList(onsetStart, range.first)
            val syllable = Syllable(onset, letters.subList(range.first, range.last + 1))
            syllables += syllable
            val nextStart = nuclei.getOrNull(n + 1)?.first ?: letters.size
            val between = letters.subList(range.last + 1, nextStart)
            if (n == nuclei.lastIndex) {
                syllable.coda = between
            } else {
                // Consonants that can begin a word go with the next vowel (Χρι-στός); the rest close
                // this syllable (ἐκ-κλη-σί-α, πάν-τα). A single one always goes with the next.
                val split = (0..between.size).first { k ->
                    between.size - k <= 1 || between.subList(k, between.size).joinToString("") { it.c.toString() } in GREEK_ONSETS
                }
                syllable.coda = between.subList(0, split)
                onsetStart = range.last + 1 + split
            }
        }
        return syllables
    }

    private fun greekSyllableSound(s: Syllable, first: Boolean, next: Letter?): String {
        val sb = StringBuilder()
        val all = s.onset + s.nucleus + s.coda
        fun following(letter: Letter): Letter? = all.getOrNull(all.indexOf(letter) + 1) ?: next
        for (c in s.onset) sb.append(consonantSound(c.c, following(c)?.c))
        // A rough breathing is an h before the word's first vowel.
        if (first && s.onset.isEmpty() && s.nucleus.any { it.rough }) sb.append('h')
        // A short vowel in a closed syllable is said short (ἐκ, ek); a circumflex marks it long (ὑμῖν, hoo-MEEN).
        val short = s.coda.isNotEmpty() && s.nucleus.none { it.circumflex }
        val nucleus = s.nucleus.joinToString("") { it.c.toString() }
        sb.append(GREEK_DIPHTHONG_SOUNDS[nucleus] ?: vowelSound(nucleus.first(), short))
        for (c in s.coda) sb.append(consonantSound(c.c, following(c)?.c))
        return sb.toString()
    }

    private fun vowelSound(c: Char, closed: Boolean): String = when (c) {
        'α' -> "ah"
        'ε' -> if (closed) "e" else "eh"
        'η' -> "ay"
        'ι' -> if (closed) "i" else "ee"
        'ο' -> "o"
        'υ' -> "oo"
        'ω' -> "oh"
        else -> ""
    }

    private fun consonantSound(c: Char, next: Char?): String = when (c) {
        'β' -> "b"
        // Before another γ, κ, ξ or χ, a γ is said n: ἄγγελος, ang-geh-los.
        'γ' -> if (next == 'γ' || next == 'κ' || next == 'ξ' || next == 'χ') "n" else "g"
        'δ' -> "d"
        'ζ' -> "z"
        'θ' -> "th"
        'κ' -> "k"
        'λ' -> "l"
        'μ' -> "m"
        'ν' -> "n"
        'ξ' -> "ks"
        'π' -> "p"
        'ρ' -> "r"
        'σ' -> "s"
        'τ' -> "t"
        'φ' -> "f"
        'χ' -> "kh"
        'ψ' -> "ps"
        else -> ""
    }

    private const val GREEK_VOWELS = "αεηιουω"
    private val GREEK_DIPHTHONGS = setOf("αι", "ει", "οι", "υι", "αυ", "ευ", "ηυ", "ου")
    private val GREEK_DIPHTHONG_SOUNDS = mapOf(
        "αι" to "ai", "ει" to "ay", "οι" to "oy", "υι" to "wee",
        "αυ" to "ow", "ευ" to "yoo", "ηυ" to "ayoo", "ου" to "oo"
    )

    // Consonants a Greek word can begin with, which stay together at the start of a syllable.
    private val GREEK_ONSETS = setOf(
        "β", "γ", "δ", "ζ", "θ", "κ", "λ", "μ", "ν", "ξ", "π", "ρ", "σ", "τ", "φ", "χ", "ψ",
        "βλ", "βρ", "γλ", "γν", "γρ", "δρ", "θλ", "θν", "θρ", "κλ", "κν", "κρ", "κτ", "μν", "πλ", "πν", "πρ",
        "πτ", "σβ", "σθ", "σκ", "σμ", "σπ", "στ", "σφ", "σχ", "τμ", "τρ", "φθ", "φλ", "φρ", "χθ", "χλ", "χν",
        "χρ", "σκλ", "σκρ", "σπλ", "στρ", "σφρ"
    )

    // ---- Hebrew: from its transliteration, which has the syllables and the stressed one ----

    /**
     * The Hebrew word with transliteration [transliteration] — syllables split by "·" or ".", the
     * stressed one in capitals, as STEPBible writes it (be·re·SHIT, 'e·lo·HIM) — as it sounds.
     */
    fun hebrew(transliteration: String): String =
        transliteration.split(' ').filter { it.isNotBlank() }.joinToString(" ") { hebrewWord(it) }.trim()

    /**
     * A Hebrew word as the lexicon has it: its [transliteration] in syllables with none stressed
     * (che.sed, a.ha.vah), and the [word], whose ֫ marks the syllable before the last as stressed
     * (חֶ֫סֶד, CHE-sed). Without it, as in most Hebrew words, the last is: ah-hah-VAH.
     */
    fun hebrewDictionaryForm(transliteration: String, word: String): String {
        val syllables = transliteration.trim().replace('·', '.').split('.').filter { s -> s.any(Char::isLetter) }
        if (syllables.isEmpty() || ' ' in transliteration.trim()) return hebrew(transliteration)
        val stress = if (OLE in word && syllables.size > 1) syllables.lastIndex - 1 else syllables.lastIndex
        return hebrew(syllables.mapIndexed { i, s -> if (i == stress) s.uppercase() else s.lowercase() }.joinToString("·"))
    }

    // The mark a lexicon puts on a stressed syllable that isn't the last.
    private const val OLE = '֫'

    private fun hebrewWord(word: String): String {
        val syllables = word.replace('·', '.').replace('/', '.').split('.')
            .map { it.trim('-', '\'', '[', ']') }.filter { s -> s.any(Char::isLetter) }
        if (syllables.isEmpty()) return ""
        // A syllable with no vowel (a glide: va/y.hi) joins the one before it.
        val merged = ArrayList<String>()
        for (s in syllables) {
            if (merged.isNotEmpty() && s.none { it.lowercaseChar() in "aeiou" }) merged[merged.lastIndex] = merged.last() + s
            else merged += s
        }
        // The stress is the last capital: a first syllable can have one just for starting the verse.
        val stress = merged.indexOfLast { s -> s.any(Char::isUpperCase) }.takeIf { merged.size > 1 && it >= 0 }
        return merged.mapIndexed { i, s ->
            val sound = hebrewSyllableSound(s.lowercase().replace("'", ""))
            if (i == stress) sound.uppercase() else sound
        }.joinToString("-")
    }

    private fun hebrewSyllableSound(s: String): String {
        val sb = StringBuilder()
        var i = 0
        val vowels = "aeiou"
        while (i < s.length) {
            val two = s.substring(i, minOf(i + 2, s.length))
            when {
                two == "ch" || two == "kh" -> { sb.append("kh"); i += 2 }
                two == "tz" || two == "ts" -> { sb.append("ts"); i += 2 }
                two == "sh" -> { sb.append("sh"); i += 2 }
                two == "ei" -> { sb.append("ay"); i += 2 }
                two == "ai" -> { sb.append("ai"); i += 2 }
                // ay closing a syllable (vay·na·SHEK) is the same sound.
                two == "ay" && (i + 2 == s.length || s[i + 2] !in vowels) -> { sb.append("ai"); i += 2 }
                two == "oi" -> { sb.append("oy"); i += 2 }
                // So is oy closing one (גּוֹי, goy).
                two == "oy" && (i + 2 == s.length || s[i + 2] !in vowels) -> { sb.append("oy"); i += 2 }
                s[i] in vowels -> {
                    // Closed by a consonant after it (not a silent final h): sed, shekh.
                    val rest = s.substring(i + 1)
                    val closed = rest.isNotEmpty() && rest != "h"
                    sb.append(
                        when (s[i]) {
                            'a' -> "ah"
                            'e' -> if (closed) "e" else "eh"
                            'i' -> "ee"
                            'o' -> "oh"
                            else -> "oo"
                        }
                    )
                    i++
                }
                // A final h after a vowel is silent (חַיָּה, hai·YAH).
                s[i] == 'h' && i == s.lastIndex && i > 0 && s[i - 1] in vowels -> i++
                else -> { sb.append(s[i]); i++ }
            }
        }
        return sb.toString()
    }

    // ---- Saying it: the sounds-like spelling, for an English voice ----

    /**
     * A sounds-like spelling ([greek]'s or [hebrew]'s) as text an English voice says as it reads: the
     * same sounds spelled as English spells them, so the voice doesn't guess — lowercase (capitals can
     * be read out as letters), "eye" for a lone ai and "eigh" for a lone ay, "gh" before e, an eh closed
     * by the sound after it (tet-tah: English has no eh ending a syllable), "ss" for a final s, and the
     * first sound of a cluster English can't begin a word with said with the syllable before it, or
     * left out starting the word, as English does (pneuma). The stressed syllable starts a part of its
     * own, where an English voice puts the stress: ah-GAH-pay is "ah gah-pay", PNYOO-mah "nyew-mah".
     */
    fun spoken(soundsLike: String): String =
        soundsLike.split(' ').filter { it.isNotBlank() }.joinToString(" ") { spokenWord(it) }.trim()

    // A syllable to be said: the consonants before its vowel sound, the vowel sound, those after.
    private class Said(val onset: MutableList<String>, val vowel: String, val coda: MutableList<String>, val stressed: Boolean)

    private fun spokenWord(word: String): String {
        val raw = word.split('-').filter { it.isNotEmpty() }
        val stress = raw.indexOfFirst { s -> s.any(Char::isUpperCase) }.takeIf { raw.size > 1 && it >= 0 }
        val said = ArrayList<Said>()
        val pending = ArrayList<String>()
        for ((n, syllable) in raw.withIndex()) {
            val units = soundUnits(syllable.lowercase().filter { it in 'a'..'z' })
            val vowels = units.indices.filter { units[it] in SAID_VOWELS }
            // A consonant on its own (a stray one in a transliteration) goes with the syllable before.
            if (vowels.isEmpty()) {
                if (said.isNotEmpty()) said.last().coda += units else pending += units
                continue
            }
            // Two vowel sounds run together (ro'eh, roheh) are two syllables to say.
            var start = 0
            for ((v, at) in vowels.withIndex()) {
                val onset = (pending + units.subList(start, at)).toMutableList()
                pending.clear()
                val coda = if (v == vowels.lastIndex) units.subList(at + 1, units.size).toMutableList() else ArrayList()
                said += Said(onset, units[at], coda, n == stress && v == 0)
                start = at + 1
            }
        }
        if (said.isEmpty()) return ""
        said.last().coda += pending
        for ((i, s) in said.withIndex()) {
            // A cluster English can't begin a word with: its first sound closes the syllable before
            // (ek-seh), or, beginning the word, isn't said (nyew-mah).
            while (!speakableOnset(s.onset)) {
                val first = s.onset.removeAt(0)
                if (i > 0) said[i - 1].coda += first
            }
        }
        for (i in 0 until said.lastIndex) {
            // An eh ending a syllable before a consonant is said closed by it: ghel-lohss.
            val next = said[i + 1].onset.firstOrNull()
            val open = said[i].vowel == "eh" || said[i].vowel == "e"
            if (open && said[i].coda.isEmpty() && next != null && next !in listOf("h", "y", "w")) said[i].coda += next
        }
        val parts = ArrayList<MutableList<String>>()
        for (s in said) {
            if (s.stressed || parts.isEmpty()) parts += ArrayList<String>()
            parts.last() += spokenSyllable(s)
        }
        return parts.joinToString(" ") { it.joinToString("-") }
    }

    // A syllable's letters as its sounds: vowel sounds (ah, oo...) and consonants (sh, k...).
    private fun soundUnits(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val unit = SAID_VOWELS.firstOrNull { s.startsWith(it, i) } ?: SAID_CONSONANTS.firstOrNull { s.startsWith(it, i) }
            if (unit == null) {
                i++
                continue
            }
            when (unit) {
                "a" -> out += "ah"
                "u" -> out += "oo"
                "kh", "c", "q" -> out += "k"
                "x" -> out += listOf("k", "s")
                else -> out += unit
            }
            i += unit.length
        }
        return out
    }

    private fun speakableOnset(onset: List<String>): Boolean = when {
        onset.size <= 1 -> true
        // A consonant and a y, said as one (nyew); or a cluster and a y (skew).
        onset.last() == "y" -> onset.size == 2 || onset.dropLast(1) in SAID_ONSETS
        else -> onset in SAID_ONSETS
    }

    private fun spokenSyllable(s: Said): String {
        val onset = s.onset
        val coda = s.coda.toMutableList()
        var vowel = s.vowel
        when {
            vowel == "o" -> vowel = "oh"
            vowel == "eh" && coda.isNotEmpty() -> vowel = "e"
            vowel == "e" && coda.isEmpty() -> vowel = "eh"
            vowel == "i" && coda.isEmpty() -> vowel = "ee"
        }
        if (vowel == "oh" && coda.firstOrNull() == "y") {
            vowel = "oy"
            coda.removeAt(0)
        }
        // A y gliding into oo (PNYOO, nyew) is spelled with it.
        val glide = vowel == "oo" && onset.size > 1 && onset.last() == "y"
        var start = spelledOnset(if (glide) onset.dropLast(1) else onset)
        val rest = coda.drop(1).joinToString("")
        var end = coda.joinToString("")
        val sound = when (vowel) {
            "e" -> when {
                // Before an r, as in "bare": ber would be said bur.
                coda[0] == "r" -> {
                    end = rest
                    if (onset.isEmpty() || onset.last() == "z") "air" else "are"
                }
                coda == listOf("s") -> {
                    end = ""
                    if (start == "y") "es" else "ess"
                }
                coda[0] == "g" -> { end = "gg$rest"; "e" }
                coda[0] == "sh" && (start == "y" || start == "d") -> { end = "zh$rest"; "e" }
                coda == listOf("d") && start == "y" -> { end = "dd"; "e" }
                else -> "e"
            }.also { if (start == "b" && coda[0] in listOf("sh", "f", "b")) start = "bh" }
            "eh" -> "eh"
            "ah" -> "ah"
            "ay" -> if (onset.isEmpty()) "eigh" else "ay"
            "ai" -> when {
                onset.isEmpty() -> "eye"
                coda.isEmpty() -> if (start == "th") "igh" else "ai"
                // Closed, as in "kyne": kain would be said like Cain.
                else -> {
                    end = rest
                    "y" + if (coda[0] == "s") "ce" else coda[0] + "e"
                }
            }
            "ee" -> "ee".also { if (start == "r" && coda.isNotEmpty()) start = "rh" }
            "oo" -> when {
                glide -> when {
                    onset[onset.size - 2] == "b" -> "ue"
                    onset[onset.size - 2] == "d" -> "yue"
                    onset.size > 2 && onset[0] == "s" -> "ew"
                    else -> "yew"
                }
                coda.firstOrNull() == "k" -> { end = "c$rest"; "oo" }
                coda == listOf("d") || coda.firstOrNull() == "r" -> "ooe"
                else -> "oo"
            }
            "ow" -> "ao"
            else -> vowel
        }
        // A g before e, i or y is said as in "get", not "gem".
        if (start.endsWith("g") && sound.first() in "eiy") start += "h"
        // A final s is said s, not z.
        if (coda == listOf("s") && end == "s") end = "ss"
        return start + sound + end
    }

    private fun spelledOnset(onset: List<String>): String = when {
        onset.take(2) == listOf("h", "w") -> "wh" + onset.drop(2).joinToString("")
        onset.take(2) == listOf("s", "f") -> "sph" + onset.drop(2).joinToString("")
        else -> onset.joinToString("")
    }

    private val SAID_VOWELS = listOf("ah", "eh", "ay", "ai", "ee", "oh", "oo", "oy", "ow", "e", "i", "o", "a", "u")
    private val SAID_CONSONANTS = listOf(
        "sh", "th", "kh", "ts", "b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "q", "r", "s", "t", "v", "w", "x", "y", "z"
    )

    // Consonants an English word can begin with together.
    private val SAID_ONSETS: Set<List<String>> = buildSet {
        listOf("k", "t", "p", "m", "n", "l", "w").forEach { add(listOf("s", it)) }
        addAll(listOf(listOf("s", "k", "r"), listOf("s", "t", "r"), listOf("s", "p", "r"), listOf("s", "p", "l"), listOf("s", "k", "l")))
        listOf("b", "d", "f", "g", "k", "p", "t", "th", "sh").forEach { add(listOf(it, "r")) }
        listOf("b", "f", "g", "k", "p", "s").forEach { add(listOf(it, "l")) }
        listOf("d", "t", "k", "g", "s", "th").forEach { add(listOf(it, "w")) }
        add(listOf("h", "w"))
        add(listOf("s", "f"))
    }
}
