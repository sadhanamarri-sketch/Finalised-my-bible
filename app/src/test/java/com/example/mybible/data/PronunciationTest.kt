package com.example.mybible.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationTest {

    @Test
    fun greekIsSaidSyllableBySyllableWithItsAccentStressed() {
        assertEquals("ah-GAH-pay", Pronunciation.greek("ἀγάπη"))
        assertEquals("ay-GAH-pay-sen", Pronunciation.greek("ἠγάπησεν"))
        assertEquals("theh-OS", Pronunciation.greek("θεός"))
        assertEquals("LO-gos", Pronunciation.greek("λόγος"))
        assertEquals("PNYOO-mah", Pronunciation.greek("πνεῦμα"))
        assertEquals("ee-ay-SOOS", Pronunciation.greek("Ἰησοῦς"))
    }

    @Test
    fun consonantsThatCanStartAWordStayTogether() {
        // στ starts words (στόμα), so Χρι-στός; κκ doesn't, so ἐκ-κλη-σί-α.
        assertEquals("khree-STOS", Pronunciation.greek("Χριστός"))
        assertEquals("ek-klay-SEE-ah", Pronunciation.greek("ἐκκλησία"))
        assertEquals("PAHN-tah", Pronunciation.greek("πάντα"))
    }

    @Test
    fun breathingsDiphthongsAndTheNasalGamma() {
        // A rough breathing is an h; ου one sound; a γ before γ said n.
        assertEquals("HOO-tohs", Pronunciation.greek("οὕτως"))
        assertEquals("hwee-OS", Pronunciation.greek("υἱός"))
        assertEquals("AHN-geh-los", Pronunciation.greek("ἄγγελος"))
        // αι as in aisle; ξ is ks; a circumflex makes a closed ι long; only the word's own accent stressed.
        assertEquals("ai-TAY-soh-men", Pronunciation.greek("αἰτήσωμέν"))
        assertEquals("rom-FAI-ah", Pronunciation.greek("ῥομφαία"))
        assertEquals("eh-KSEH-pnyoo-sen", Pronunciation.greek("ἐξέπνευσεν"))
        assertEquals("hoo-MEEN", Pronunciation.greek("ὑμῖν"))
        assertEquals("DOO-lon", Pronunciation.greek("δοῦλόν"))
        // One syllable: nothing to stress.
        assertEquals("gahr", Pronunciation.greek("γὰρ"))
        assertEquals("", Pronunciation.greek("·"))
    }

    @Test
    fun hebrewFollowsItsTransliterationsSyllablesAndStress() {
        assertEquals("beh-reh-SHEET", Pronunciation.hebrew("be·re·SHIT"))
        assertEquals("eh-loh-HEEM", Pronunciation.hebrew("'e·lo·HIM"))
        assertEquals("bah-RAH", Pronunciation.hebrew("ba·RA'"))
        assertEquals("KHEH-sed", Pronunciation.hebrew("CHE·sed"))
        assertEquals("hah-shah-MAH-yeem", Pronunciation.hebrew("ha·sha·MA·yim"))
        assertEquals("veh-KHOH-shekh", Pronunciation.hebrew("ve·CHO·shekh"))
        assertEquals("oo-VAYN", Pronunciation.hebrew("u·VEIN"))
        // A capital for starting the verse isn't the stress; ai and a closing ay as in aisle.
        assertEquals("hah-mah-khah-NOHT", Pronunciation.hebrew("HA·ma·cha·NOT"))
        assertEquals("vai-yeh-kheh-ZAHK", Pronunciation.hebrew("vai·ye·che·ZAK"))
        assertEquals("vai-nah-SHEK", Pronunciation.hebrew("vay·na·SHEK"))
        // STEPBible's own spelling of it, before the app tidies it.
        assertEquals("beh-reh-SHEET", Pronunciation.hebrew("be./re.Shit"))
    }

    @Test
    fun aHebrewWordAsTheLexiconHasIt() {
        // No stress marked: the last syllable, unless the word's ֫ marks the one before it.
        assertEquals("ah-hah-VAH", Pronunciation.hebrewDictionaryForm("a.ha.vah", "אַהֲבָה"))
        assertEquals("KHEH-sed", Pronunciation.hebrewDictionaryForm("che.sed", "חֶ֫סֶד"))
        assertEquals("BAH-yeet", Pronunciation.hebrewDictionaryForm("ba.yit", "בַּ֫יִת"))
        assertEquals("dah-VAHR", Pronunciation.hebrewDictionaryForm("da.var", "דָּבָר"))
        assertEquals("bet", Pronunciation.hebrewDictionaryForm("bet", "בֵּית"))
        // oy closing a syllable is one sound, as oi is.
        assertEquals("goy", Pronunciation.hebrew("goy"))
    }

    @Test
    fun whatAnEnglishVoiceIsGivenToSayIt() {
        // Lowercase (capitals can be read out as letters), the stressed syllable starting a part of its own.
        assertEquals("ah gah-pay", Pronunciation.spoken("ah-GAH-pay"))
        assertEquals("gahr", Pronunciation.spoken("gahr"))
        // A lone ay or ai spelled so it isn't read "eye" or as letters.
        assertEquals("eigh gah-pay-sen", Pronunciation.spoken("ay-GAH-pay-sen"))
        assertEquals("ee-eigh sooss", Pronunciation.spoken("ee-ay-SOOS"))
        assertEquals("eye tay-soh-men", Pronunciation.spoken("ai-TAY-soh-men"))
        assertEquals("rohm fai-ah", Pronunciation.spoken("rom-FAI-ah"))
        // o as in "go" (not "to"), and a final s said s (lohss, not lohz).
        assertEquals("loh-gohss", Pronunciation.spoken("LO-gos"))
        assertEquals("theh ohss", Pronunciation.spoken("theh-OS"))
        // Clusters English can't begin a word with: closing the syllable before, or not said starting it.
        assertEquals("kree stohss", Pronunciation.spoken("khree-STOS"))
        assertEquals("nyew-mah", Pronunciation.spoken("PNYOO-mah"))
        assertEquals("ek sep-nyew-sen", Pronunciation.spoken("eh-KSEH-pnyoo-sen"))
        assertEquals("soo kay", Pronunciation.spoken("psoo-KHAY"))
        assertEquals("whee ohss", Pronunciation.spoken("hwee-OS"))
        // An eh before a consonant closed by it; g before e as in "get".
        assertEquals("ahn-ghel-lohss", Pronunciation.spoken("AHN-geh-los"))
        assertEquals("ghee noh-sket-teh", Pronunciation.spoken("gee-NOH-skeh-teh"))
        assertEquals("bare-resh sheet", Pronunciation.spoken("beh-reh-SHEET"))
        assertEquals("kess-sed", Pronunciation.spoken("KHEH-sed"))
        assertEquals("el-loh heem", Pronunciation.spoken("eh-loh-HEEM"))
        assertEquals("air ray-moh", Pronunciation.spoken("eh-RAY-moh"))
        assertEquals("hah yezh", Pronunciation.spoken("hah-YESH"))
        assertEquals("ah-hah vah", Pronunciation.spoken("ah-hah-VAH"))
        assertEquals("vai-yek-kez zahk", Pronunciation.spoken("vai-yeh-kheh-ZAHK"))
        // Two words, and nothing to say.
        assertEquals("hoh kree stohss", Pronunciation.spoken("ho khree-STOS"))
        assertEquals("", Pronunciation.spoken(""))
    }
}
