package com.example.mybible.ui.versescroll

import org.junit.Assert.assertEquals
import org.junit.Test

class CardColorsTest {

    @Test
    fun aVersesColorsAreEachAChip() {
        assertEquals(
            listOf(CardColor("#FFD54F", null), CardColor("#81C784", null)),
            cardColors(listOf(16), listOf(listOf("#FFD54F", "#81C784")))
        )
        assertEquals(emptyList<CardColor>(), cardColors(listOf(16), listOf(emptyList())))
    }

    @Test
    fun onAPassageAColorOnSomeOfItsVersesSaysWhich() {
        val chips = cardColors(
            listOf(16, 17, 18),
            listOf(listOf("#FFD54F", "#64B5F6"), listOf("#ffd54f"), listOf("#FFD54F", "#64B5F6", "#E57373"))
        )
        // On all three (whatever the case of its hex), then those on some, in the order they come.
        assertEquals(
            listOf(CardColor("#FFD54F", null), CardColor("#64B5F6", listOf(16, 18)), CardColor("#E57373", listOf(18))),
            chips
        )
    }
}
