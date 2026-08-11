package com.example.mybible.data

/** Human-readable labels for the Robinson-style TAGNT morphology codes. */
object MorphologyParser {
    private val pos = mapOf(
        "N" to "Noun", "V" to "Verb", "A" to "Adjective", "D" to "Adverb",
        "P" to "Pronoun", "R" to "Preposition", "C" to "Conjunction",
        "T" to "Article", "I" to "Interjection", "X" to "Particle",
        "S" to "Demonstrative pronoun", "K" to "Adverbial conjunction"
    )
    private val tense = mapOf(
        "P" to "Present", "I" to "Imperfect", "F" to "Future", "A" to "Aorist",
        "X" to "Perfect", "Y" to "Pluperfect", "2" to "Second", "1" to "First",
        "3" to "Third"
    )
    private val voice = mapOf("A" to "Active", "M" to "Middle", "P" to "Passive", "E" to "Middle/Passive")
    private val mood = mapOf(
        "I" to "Indicative", "S" to "Subjunctive", "O" to "Optative", "M" to "Imperative",
        "N" to "Infinitive", "P" to "Participle"
    )
    private val case = mapOf("N" to "Nominative", "G" to "Genitive", "D" to "Dative", "A" to "Accusative", "V" to "Vocative")
    private val number = mapOf("S" to "Singular", "P" to "Plural")
    private val gender = mapOf("M" to "Masculine", "F" to "Feminine", "N" to "Neuter")

    fun describe(raw: String): String {
        val code = raw.trim().substringAfter('=', raw.trim()).uppercase()
        if (code.isBlank()) return ""
        val parts = code.split('-')
        if (parts.size < 2) return code

        val value = parts[1]
        val labels = mutableListOf<String>()
        pos[parts[0]]?.let(labels::add)

        when (parts[0]) {
            "V" -> {
                if (value.length >= 1) tense[value[0].toString()]?.let(labels::add)
                if (value.length >= 2) voice[value[1].toString()]?.let(labels::add)
                if (value.length >= 3) mood[value[2].toString()]?.let(labels::add)
            }
            "N", "A", "P", "S", "T" -> {
                if (value.length >= 1) case[value[0].toString()]?.let(labels::add)
                if (value.length >= 2) number[value[1].toString()]?.let(labels::add)
                if (value.length >= 3) gender[value[2].toString()]?.let(labels::add)
            }
        }
        return if (labels.size > 1) labels.joinToString(" ") else code
    }
}
