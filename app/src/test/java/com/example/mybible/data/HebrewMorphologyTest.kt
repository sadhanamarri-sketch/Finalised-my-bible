package com.example.mybible.data

import com.example.mybible.model.HebrewWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Hebrew word of the interlinear: its parts, their grammar, and its transliteration to read. */
class HebrewMorphologyTest {

    private fun labels(code: String, aramaic: Boolean = false) = HebrewMorphology.terms(code, aramaic).map { it.label }

    @Test
    fun aWordInItsParts() {
        // וַיֹּאמֶר, "and he said": and + amar.
        val parts = HebrewMorphology.parts(HebrewWord("וַ/יֹּ֥אמֶר", "va/i.Yo.mer", "and/ he said", "H0559", "Hc/Vqw3ms"))
        assertEquals(listOf("וַ", "יֹּ֥אמֶר"), parts.map { it.hebrew })
        assertEquals(listOf("and", "he said"), parts.map { it.gloss })
        assertEquals(listOf(false, true), parts.map { it.main })
        assertEquals(listOf("Conjunction"), parts[0].grammar.map { it.label })
        assertEquals(listOf("Verb", "Qal", "Narrative past", "he"), parts[1].grammar.map { it.label })
        // A word in one part, and one with no grammar.
        assertEquals(1, HebrewMorphology.parts(HebrewWord("שָׁנָֽה\\׃", "sha.Nah", "a year", "H8141", "HNcfsa")).size)
        assertEquals(emptyList<GrammarTerm>(), HebrewMorphology.parts(HebrewWord("א", "", "", null, "")).single().grammar)
        // בָּ֣א//גָ֑ד, two words written as one: no empty part between them.
        val twoWords = HebrewMorphology.parts(HebrewWord("בָּ֣א//גָ֑ד", "be.//gad", "it has come//good fortune", "H0935G", "HVqp3ms//Ncmsa"))
        assertEquals(listOf("בָּ֣א", "גָ֑ד"), twoWords.map { it.hebrew })
    }

    @Test
    fun grammarInPlainEnglish() {
        assertEquals(listOf("Noun", "Feminine", "Singular", "Absolute"), labels("Ncfsa"))
        assertEquals(listOf("Noun", "Masculine", "Singular", "Construct"), labels("Ncmsc"))
        assertEquals(listOf("Title", "Masculine", "Singular", "Absolute"), labels("Ntmsa"))
        assertEquals(listOf("God’s name"), labels("Npt"))
        assertEquals(listOf("Preposition", "with “the”"), labels("Rd"))
        assertEquals(listOf("Object marker"), labels("To"))
        assertEquals(listOf("Verb", "Piel", "Imperfect", "you (all)"), labels("Vpi2mp"))
        assertEquals(listOf("Verb", "Qal", "Participle", "Masculine", "Plural", "Absolute"), labels("Vqrmpa"))
        assertEquals(listOf("Verb", "Hiphil", "Infinitive construct"), labels("Vhcc"))
        assertEquals(listOf("Personal pronoun", "she"), labels("Pp3fs"))
        // An ending: "his" on a noun, "him" on a verb.
        val ending = HebrewMorphology.terms("Sp3ms").single()
        assertEquals("Pronoun ending", ending.label)
        assertTrue(ending.explanation!!.contains("his, him, its"))
        // Aramaic: its own stems, and "the" as an ending.
        assertEquals(listOf("Verb", "Peal", "Perfect", "he"), labels("Vqp3ms", aramaic = true))
        assertEquals(listOf("Article"), labels("Ta", aramaic = true))
        assertTrue(HebrewMorphology.terms("Vqw3ms").all { it.explanation != null })
    }

    @Test
    fun readableTextOfTheWord() {
        assertEquals("be·re·SHIT", HebrewMorphology.cleanTransliteration("be./re.Shit"))
        assertEquals("vai·YO·mer", HebrewMorphology.cleanTransliteration("va/i.Yo.mer"))
        assertEquals("zar·'ov", HebrewMorphology.cleanTransliteration("zar.'o/v-"))
        assertEquals("YAH·weh", HebrewMorphology.cleanTransliteration("Yah.weh"))
        assertEquals("hu'", HebrewMorphology.cleanTransliteration("Hu'"))
        assertEquals("pe·dah tzur", HebrewMorphology.cleanTransliteration("pe.dah/ /tzur"))
        assertEquals("בְּרֵאשִׁ֖ית", HebrewMorphology.cleanWord("בְּ/רֵאשִׁ֖ית"))
        assertEquals("שָׁנָֽה", HebrewMorphology.cleanWord("שָׁנָֽה\\׃"))
        assertEquals("in beginning", HebrewMorphology.cleanGloss("in/ beginning"))
        assertEquals("give!", HebrewMorphology.cleanGloss("give/ !"))
        assertEquals("has it heard?", HebrewMorphology.cleanGloss("¿/ has it heard"))
    }

    @Test
    fun stemsAsTheLexiconWritesThem() {
        assertEquals("simple", HebrewMorphology.stem("Qal")?.hint)
        assertEquals("Niphal", HebrewMorphology.stem("(Niphal)")?.label)
        assertEquals("Hiphil", HebrewMorphology.stem("Hiph")?.label)
        assertEquals("Peal", HebrewMorphology.stem("P'al")?.label)
        assertEquals("Ithpaal", HebrewMorphology.stem("Ithpa'al")?.label)
        assertNull(HebrewMorphology.stem("TWOT"))
        assertNull(HebrewMorphology.stem("fig."))
    }
}
