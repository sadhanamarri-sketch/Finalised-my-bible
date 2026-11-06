package com.example.mybible.model

import com.example.mybible.audio.VerseToRead
import com.example.mybible.audio.withPrintedTogether
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Backlog 56: verses the Telugu Bible prints together (Genesis 1:17-18), shown, read and found as one. */
class PrintedTogetherTest {

    @Test
    fun theGroupsFile() {
        val groups = PrintedTogetherData.parse("""{"Genesis": {"1": [[17, 18, "ఒకటి రెండు"]], "2": [[4, 6, "మూడు"]]}, "Psalms": {"13": [[5, 6, "నాలుగు"]]}}""")
        assertEquals(listOf(PrintedTogether(17, 18, "ఒకటి రెండు")), groups.getValue("Genesis").getValue(1))
        val genesis2 = groups.getValue("Genesis").getValue(2).single()
        assertEquals("4–6", genesis2.label)
        assertTrue(5 in genesis2)
        assertEquals(listOf(4, 5, 6), PrintedTogetherData.byVerse(listOf(genesis2)).keys.sorted())
        assertEquals(listOf(4, 5, 6), genesis2.of((1..8).toList()) { it })
    }

    @Test
    fun theAppsGroupsAreThere() {
        val groups = PrintedTogetherData.parse(File("src/main/assets/telugu_together.json").readText())
        assertEquals(621, groups.values.sumOf { book -> book.values.sumOf { it.size } })
        assertEquals(17, groups.getValue("Genesis").getValue(1).first().first)
        for (book in groups.values) for (chapter in book.values) for (g in chapter) {
            assertTrue(g.first < g.last && g.telugu.isNotBlank())
        }
    }

    @Test
    fun aGroupsTeluguIsReadOnceAfterItsLastVersesEnglish() {
        val verses = (16..19).map { VerseToRead("Genesis", 1, it, "English $it", "తెలుగు $it") }
        val read = withPrintedTogether(verses, listOf(PrintedTogether(17, 18, "ఒకటి రెండు")))
        assertEquals("తెలుగు 16", read[0].telugu)
        assertNull(read[1].telugu)
        assertEquals("ఒకటి రెండు", read[2].telugu)
        assertEquals(17, read[2].teluguFrom)
        assertEquals("తెలుగు 19", read[3].telugu)
        // The English is each verse's own.
        assertEquals(verses.map { it.english }, read.map { it.english })
    }

    @Test
    fun aGroupOnlyPartlyThereKeepsItsVersesOwnTelugu() {
        // A card of 18 alone (its group's other verse not on it): its own Telugu.
        val card = listOf(VerseToRead("Genesis", 1, 18, "English 18", "తెలుగు 18"))
        assertEquals(card, withPrintedTogether(card, listOf(PrintedTogether(17, 18, "ఒకటి రెండు"))))
    }
}
