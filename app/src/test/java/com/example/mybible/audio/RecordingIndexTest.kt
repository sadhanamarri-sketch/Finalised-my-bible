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
    fun aFolderHoldsTheRecordingItHasMostChaptersOf() {
        // One file a chapter, or one a book.
        val narrated = timings((1..4).associate { "Ruth $it" to "B08___0${it}_Ruth________ENGKJVO1DA.mp3" })
        val dramatized = timings((1..4).associate { "Ruth $it" to "The Holy Bible - Book 08 - Ruth - KJV Dramatized Audio.mp4" })
        val folder = listOf("The Holy Bible - Book 08 - Ruth - KJV Dramatized Audio.mp4", "B08___01_Ruth________ENGKJVO1DA.mp3", "cover.jpg")
            .map(RecordingIndex::key).toSet()
        assertEquals(4, RecordingIndex.chaptersHeld(dramatized, folder))
        assertEquals(1, RecordingIndex.chaptersHeld(narrated, folder))
        assertEquals(0, RecordingIndex.chaptersHeld(narrated, emptySet()))
    }

    // Timings for [chapters], each in the file given, a verse each.
    private fun timings(chapters: Map<String, String>): RecordingTimings {
        val files = chapters.values.distinct().joinToString(", ") { "\"$it\": 100.0" }
        val entries = chapters.entries.joinToString(", ") { (name, file) ->
            "\"$name\": {\"file\": \"$file\", \"start\": 0.0, \"end\": 100.0, \"verses\": [[1.0, 2.0]]}"
        }
        return RecordingTimings.parse("{\"files\": {$files}, \"chapters\": {$entries}}")
    }

    @Test
    fun chaptersAreSaidBriefly() {
        val all = (1..4).map { "Ruth $it" } + (1..22).map { "1 Kings $it" } + listOf("Jude 1") + (1..150).map { "Psalms $it" }
        val missing = (1..4).map { "Ruth $it" } + (1..8).map { "1 Kings $it" } + listOf("1 Kings 20", "1 Kings 21", "1 Kings 22", "Jude 1", "Psalms 23")
        assertEquals(listOf("Ruth", "1 Kings 1–8, 20–22", "Jude", "Psalms 23"), RecordingIndex.summarize(missing, all))
        assertTrue(RecordingIndex.summarize(emptyList(), all).isEmpty())
    }
}
