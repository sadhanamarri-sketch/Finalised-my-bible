package com.example.mybible.data

/**
 * TBESG/TBESH definition text arrives from TbesgImporter/TbeshImporter as
 * newline-separated lines — cleanTbesgText turns the source's <BR> tags
 * into "\n", and each resulting line is already one semantic unit: the
 * opening principal-parts heading, a bracketed etymology/LXX note, a
 * numbered sense, a lettered sub-sense, or the closing "SYN.:" synonym
 * list. Rendered as one flat paragraph (the original approach) all of
 * that structure is thrown away and everything reads as a single dense
 * block. This classifies each line so the UI can give it distinct visual
 * weight instead.
 */
object LexiconDefinitionFormatter {

    sealed class Line(val text: String) {
        /** The dictionary's principal-parts citation, e.g. "δοῦλος, -η, -ον,". */
        class Heading(text: String) : Line(text)

        /** A bracketed etymology / LXX-usage note, e.g. "[in LXX, ...]". */
        class Note(text: String) : Line(text)

        /**
         * A Roman-numeral top-level division grouping several numbered
         * senses under it, e.g. "I. Of that by which the inward thought is
         * expressed..." — Abbott-Smith's convention for words with enough
         * breadth of meaning to need a level above the plain "1. / 2. / 3."
         * senses (λόγος is the canonical example: I/II/III, each with its
         * own 1/2/3, some of those with (a)/(b) under them). Rare (under 1%
         * of entries) but disproportionately likely to matter, since it's
         * exactly the richest, most commonly looked-up words that need it.
         */
        class MajorDivision(val numeral: String, val body: String, text: String) : Line(text)

        /** A top-level numbered sense, e.g. "1. in bondage to, subject to: Rom.6:19." */
        class Sense(val number: String, val body: String, text: String) : Line(text)

        /** A lettered sub-sense under a numbered sense, e.g. "(a) fem., ..." */
        class SubSense(val label: String, val body: String, text: String) : Line(text)

        /** The closing "SYN.:" synonym list. */
        class Synonyms(val body: String, text: String) : Line(text)

        /** Anything else — rendered as a plain paragraph. */
        class Plain(text: String) : Line(text)
    }

    private val SENSE_RE = Regex("""^(\d{1,2})\.\s*(.*)$""")
    private val SUBSENSE_RE = Regex("""^\(([a-z]{1,3}|[ivx]{1,4})\)\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val SYN_RE = Regex("""^SYN\.?:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    // Upper-case Roman numerals only — SUBSENSE_RE's lower-case "(i)/(ii)"
    // sub-senses live inside parens and never collide with this, which
    // requires bare numerals (no parens) at the very start of the line.
    private val MAJOR_DIVISION_RE = Regex("""^([IVX]{1,4})\.\s*(.*)$""")
    private val GREEK_RE = Regex("\\p{InGreek}")

    // TbesgImporter/TbeshImporter preserve Scripture citations as
    // "⟦book.chapter.verse|display text⟧" markers instead of discarding
    // them (see TbesgImporter's REF_TAG_RE doc). Left intact through
    // classification here — LexiconDefinitionView renders them as tappable
    // spans (via ScriptureRefResolver) rather than plain text, so the
    // marker's key half needs to survive into the rendering layer.

    fun parse(definition: String): List<Line> {
        val rawLines = definition
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        return rawLines.mapIndexed { index, line ->
            val synMatch = SYN_RE.find(line)
            val majorMatch = MAJOR_DIVISION_RE.find(line)
            val senseMatch = SENSE_RE.find(line)
            val subMatch = SUBSENSE_RE.find(line)
            when {
                line.startsWith("[") -> Line.Note(line)
                synMatch != null -> Line.Synonyms(synMatch.groupValues[1], line)
                majorMatch != null -> Line.MajorDivision(majorMatch.groupValues[1], majorMatch.groupValues[2], line)
                senseMatch != null -> Line.Sense(senseMatch.groupValues[1], senseMatch.groupValues[2], line)
                subMatch != null -> Line.SubSense(subMatch.groupValues[1], subMatch.groupValues[2], line)
                index == 0 && GREEK_RE.containsMatchIn(line) -> Line.Heading(line)
                else -> Line.Plain(line)
            }
        }
    }

    /**
     * Matches Scripture-citation tokens like "Rom.6:19", "Mat.8:9 18:23,",
     * or "Mat.10:24 13:27, 28" so the UI can pick them out in an accent
     * color — the abbreviation clusters are otherwise the hardest part of
     * a lexicon entry to visually locate while scanning.
     */
    val SCRIPTURE_REF_RE = Regex(
        """\b[1-3]?[A-Z][a-z]{1,5}\.(?:\s?\d{1,3}(?::\d{1,3})?)(?:[\s,]+\d{1,3}(?::\d{1,3})?)*"""
    )
}
