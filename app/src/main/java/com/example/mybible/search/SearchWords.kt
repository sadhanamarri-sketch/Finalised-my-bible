package com.example.mybible.search

import java.text.Normalizer

private val whitespace = Regex("\\s+")

/**
 * Function words a query can do without: "come to me" also finds "Come unto me", and "do not
 * worry" finds "Take no thought". They still count towards ranking. Same list as
 * tools/search/make_search_data.py, which never makes one a base for word forms or a rendering.
 */
internal val OPTIONAL_WORDS: Set<String> = """
    a about after all also am an and any are art as at be been before being but by
    can could did do does doest doeth doth dost even every for from had has hast hath have how if in into
    is may might more most much must no nor not now o of on one or out own same shall shalt should so some
    such than that the then there these this those to unto up upon very was wast were what when where which
    who whom whose why will wilt with would yea yet
""".trim().split(whitespace).toSet()

/** Pronouns: a query needs them ("I love you"), but they get no forms and no Greek or Hebrew. */
internal val PRONOUNS: Set<String> = """
    me my mine we us our ours you your yours he him his she her hers it its they them
    their theirs thee thou thy thine ye
""".trim().split(whitespace).toSet()

internal val STOPWORDS: Set<String> = OPTIONAL_WORDS + PRONOUNS

private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

/** True when [text] has a letter a to z: an English search rather than a Telugu one. */
internal fun hasLatinLetter(text: String) = text.any(::isAsciiLetter)

internal fun isGreekLetter(c: Char) = (c in '\u0370'..'\u03FF' || c in '\u1F00'..'\u1FFF') && Character.isLetter(c)

internal fun isHebrewLetter(c: Char) = c in '\u05D0'..'\u05EA' || c in '\uFB1D'..'\uFB4F'

/** True when [text] has a Greek or Hebrew letter: a search for a word of the original text. */
internal fun hasOriginalLetter(text: String) = text.any { isGreekLetter(it) || isHebrewLetter(it) }

// Hebrew's final letters, and Greek's final sigma, as the plain letter.
private val plainLetter = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ', 'ς' to 'σ')

/**
 * A Greek or Hebrew word with no accents, breathings or vowel points, lowercase, its final
 * letters as the plain ones: ἀγάπης → αγαπησ, חֶ֫סֶד → חסד. Same as make_search_data.py's
 * script_key, which writes the spellings in original_forms.tsv this way.
 */
internal fun originalKey(text: String): String {
    val out = StringBuilder(text.length)
    for (c in Normalizer.normalize(text, Normalizer.Form.NFD)) {
        if (Character.isLetter(c)) out.append(plainLetter[c] ?: c.lowercaseChar())
    }
    return out.toString()
}

/** A transliteration's letters a to z, without accents or the dots between syllables: agapē → agape. */
internal fun latinKey(text: String): String {
    val out = StringBuilder(text.length)
    for (c in Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)) {
        if (c in 'a'..'z') out.append(c)
    }
    return out.toString()
}

/**
 * Calls [onWord] with each run of the letters a to z in [text], lowercased, and where it starts
 * and ends. "God's" is "god" and "s", as the data builder splits it.
 */
internal inline fun forEachWord(text: String, onWord: (word: String, start: Int, end: Int) -> Unit) {
    var i = 0
    val n = text.length
    while (i < n) {
        if (text[i] in 'a'..'z' || text[i] in 'A'..'Z') {
            val start = i
            while (i < n && (text[i] in 'a'..'z' || text[i] in 'A'..'Z')) i++
            onWord(text.substring(start, i).lowercase(), start, i)
        } else {
            i++
        }
    }
}

internal fun wordsOf(text: String): List<String> {
    val out = ArrayList<String>()
    forEachWord(text) { word, _, _ -> out += word }
    return out
}

/**
 * [word] and the plain words it could be an ending of: worried → worry, loving → love,
 * running → run, wives → wife. For words the King James text doesn't have, whose forms the
 * bundled word families can't give (see SearchLexicon.kin).
 */
internal fun plainForms(word: String): Set<String> {
    val out = linkedSetOf(word)
    val n = word.length
    fun undoubled(stem: String): String? =
        if (stem.length >= 3 && stem[stem.length - 1] == stem[stem.length - 2] && stem.last() !in "aeiouls") {
            stem.dropLast(1)
        } else {
            null
        }
    if (n > 4 && word.endsWith("ies")) out += word.dropLast(3) + "y"
    if (n > 4 && word.endsWith("ves")) {
        out += word.dropLast(3) + "f"
        out += word.dropLast(3) + "fe"
    }
    if (n > 3 && word.endsWith("es")) out += word.dropLast(2)
    if (n > 3 && word.endsWith("s") && !word.endsWith("ss")) out += word.dropLast(1)
    if (n > 4 && word.endsWith("ied")) out += word.dropLast(3) + "y"
    if (n > 3 && word.endsWith("ed")) {
        out += word.dropLast(2)
        out += word.dropLast(1)
        undoubled(word.dropLast(2))?.let { out += it }
    }
    if (n > 4 && word.endsWith("ing")) {
        out += word.dropLast(3)
        out += word.dropLast(3) + "e"
        if (word.endsWith("ying")) out += word.dropLast(4) + "ie"
        undoubled(word.dropLast(3))?.let { out += it }
    }
    return out
}

/**
 * Edit distance between [a] and [b], counting two neighbouring letters swapped as one edit
 * (fiath → faith is 1). Stops early, returning limit + 1, once it can't be within [limit].
 */
internal fun editDistance(a: String, b: String, limit: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
    var beforePrevious = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var d = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d = minOf(d, beforePrevious[j - 2] + 1)
            }
            current[j] = d
            if (d < rowMin) rowMin = d
        }
        if (rowMin > limit) return limit + 1
        val recycled = beforePrevious
        beforePrevious = previous
        previous = current
        current = recycled
    }
    return previous[b.length]
}
