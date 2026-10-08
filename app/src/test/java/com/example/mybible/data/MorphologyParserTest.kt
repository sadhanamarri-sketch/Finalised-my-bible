package com.example.mybible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Greek word's grammar from its TAGNT code, in plain English. */
class MorphologyParserTest {

    private fun labels(code: String) = MorphologyParser.terms(code).map { it.label }

    @Test
    fun verbs() {
        assertEquals(listOf("Verb", "Aorist", "Active", "Indicative", "3rd person singular"), labels("V-AAI-3S"))
        // As the interlinear stores it, after the Strong's number: ἀφῆτε, "you shall forgive".
        assertEquals(listOf("Verb", "Aorist (second form)", "Active", "Subjunctive", "2nd person plural"), labels("G0863H=V-2AAS-2P"))
        assertEquals(listOf("Verb", "Present", "Active", "Participle", "Nominative", "Singular", "Masculine"), labels("V-PAP-NSM"))
        assertEquals(listOf("Verb", "Aorist", "Active", "Infinitive"), labels("V-AAN"))
        assertEquals(listOf("Verb", "Present", "Middle or passive form, active meaning", "Indicative", "3rd person singular"), labels("V-PNI-3S"))
        // ἐφφαθά, kept in Aramaic.
        assertEquals(listOf("Verb", "Aorist", "Active", "Imperative", "2nd person singular", "Aramaic word"), labels("V-AAM-2S-ARAM"))
        val aorist = MorphologyParser.terms("V-AAI-3S")
        assertTrue(aorist.all { it.explanation != null })
        assertTrue(aorist[1].explanation!!.contains("completed act"))
    }

    @Test
    fun nounsPronounsAndOtherWords() {
        assertEquals(listOf("Noun", "Genitive", "Singular", "Masculine", "Title"), labels("N-GSM-T"))
        assertEquals(listOf("Article", "Nominative", "Singular", "Masculine"), labels("T-NSM"))
        assertEquals(listOf("Personal pronoun", "1st person singular", "Genitive"), labels("P-1GS"))
        assertEquals(listOf("Reflexive pronoun", "3rd person singular", "Genitive", "Masculine"), labels("F-3GSM"))
        assertEquals(listOf("Possessive pronoun", "my", "Nominative", "Singular", "Masculine"), labels("S-1SNSM"))
        assertEquals(listOf("Number"), labels("A-NUI"))
        assertEquals(listOf("Adjective", "Accusative", "Singular", "Neuter", "Comparative"), labels("A-ASN-C"))
        assertEquals(listOf("Particle", "Negative"), labels("PRT-N"))
        assertEquals(listOf("Interjection", "Hebrew word"), labels("INJ-HEB"))
        // Ἑβραϊστί, "in Hebrew": an adverb from a people's name, not a title.
        val hebraisti = MorphologyParser.terms("ADV-T")
        assertEquals(listOf("Adverb"), hebraisti.map { it.label })
        assertTrue(hebraisti[0].explanation!!.contains("in Hebrew"))
    }

    @Test
    fun twoWordsWrittenAsOne() {
        // κἀκεῖνος, "and that one".
        assertEquals(
            listOf("Conjunction", "+", "Demonstrative pronoun", "Nominative", "Singular", "Masculine"),
            labels("CONJ + G1565=D-NSM")
        )
        assertEquals("Verb · Aorist · Active · Indicative · 3rd person singular", MorphologyParser.describe("V-AAI-3S"))
    }
}
