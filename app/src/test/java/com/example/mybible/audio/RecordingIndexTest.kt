package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingIndexTest {

    @Test
    fun aFileIsFoundThoughItsNameLostASpaceOrItsExtensionChanged() {
        val timed = RecordingIndex.key("Book of Genesis  - ఆదికాండము  Telugu Audio Bible   YGFM - Apostle Ratnaker.mp3")
        assertEquals(timed, RecordingIndex.key("book of genesis - ఆదికాండము Telugu Audio Bible YGFM - Apostle Ratnaker.m4a"))
        // Telugu written either way Unicode allows: కై with its vowel sign as one, or as its two parts.
        assertEquals(RecordingIndex.key("\u0C15\u0C48.mp3"), RecordingIndex.key("\u0C15\u0C46\u0C56.mp3"))
        assertNotEquals(timed, RecordingIndex.key("Book of Exodus - నిర్గమకాండము Telugu Audio Bible YGFM - Apostle Ratnaker.mp3"))
        assertEquals("a01___01_genesis_____engkjvo1da", RecordingIndex.key("A01___01_Genesis_____ENGKJVO1DA.mp3"))
    }

    @Test
    fun chaptersAreSaidBriefly() {
        val all = (1..4).map { "Ruth $it" } + (1..22).map { "1 Kings $it" } + listOf("Jude 1") + (1..150).map { "Psalms $it" }
        val missing = (1..4).map { "Ruth $it" } + (1..8).map { "1 Kings $it" } + listOf("1 Kings 20", "1 Kings 21", "1 Kings 22", "Jude 1", "Psalms 23")
        assertEquals(listOf("Ruth", "1 Kings 1–8, 20–22", "Jude", "Psalms 23"), RecordingIndex.summarize(missing, all))
        assertTrue(RecordingIndex.summarize(emptyList(), all).isEmpty())
    }
}
