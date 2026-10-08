package com.example.mybible.data

import com.example.mybible.data.LexiconDefinitionFormatter.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A lexicon entry's lines: Greek senses, and the outline of a Hebrew entry with its verb stems. */
class LexiconDefinitionFormatterTest {

    @Test
    fun hebrewOutlineByLevelWithItsStems() {
        val lines = LexiconDefinitionFormatter.parse(
            listOf(
                "1) to lift, bear up, carry, take",
                "1a) (Qal)",
                "1a1) to lift, lift up",
                "1b) (Niphal) to be lifted up, be exalted",
                "2) (TWOT) (Hiphil) producing thousands",
                "3)(BDB) chief"
            ).joinToString("\n")
        )
        val outline = lines.filterIsInstance<Line.Outline>()
        assertEquals(lines.size, outline.size)
        assertEquals(listOf(1, 2, 3, 2, 1, 1), outline.map { it.depth })
        assertEquals(listOf("1", "a", "1", "b", "2", "3"), outline.map { it.marker })
        assertEquals(listOf(null, "Qal", null, "Niphal", "Hiphil", null), outline.map { it.stem?.label })
        assertEquals(listOf("to lift, bear up, carry, take", "", "to lift, lift up", "to be lifted up, be exalted", "(TWOT) producing thousands", "(BDB) chief"), outline.map { it.body })
    }

    @Test
    fun greekSensesStayAsTheyAre() {
        val lines = LexiconDefinitionFormatter.parse("δοῦλος, -η, -ον,\n1. in bondage, subject to\n(a) fem.\nSYN.: διάκονος")
        assertTrue(lines[0] is Line.Heading)
        assertTrue(lines[1] is Line.Sense)
        assertTrue(lines[2] is Line.SubSense)
        assertTrue(lines[3] is Line.Synonyms)
    }
}
