package com.example.mybible.data

import com.example.mybible.model.HighlightItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightsTest {

    private val promise = "#F1F1B1"
    private val prayer = "#B1D1F1"
    private val comfort = "#B1F1B1"
    private val praise = "#F1B1B1"

    private fun colors(list: List<HighlightItem>, verse: Int = 16) = list.onVerse("John", 3, verse).map { it.colorHex }

    @Test
    fun aVerseTakesUpToThreeColorsInTheOrderTheyWereAdded() {
        var list = emptyList<HighlightItem>()
        list = list.withColor("John", 3, 16, promise, now = 1)
        list = list.withColor("John", 3, 16, prayer, now = 2)
        list = list.withColor("John", 3, 17, praise, now = 3)
        list = list.withColor("John", 3, 16, comfort, now = 4)
        assertEquals(listOf(promise, prayer, comfort), colors(list))
        // A fourth doesn't go on; the verse beside it is its own.
        assertFalse(list.hasRoomFor("John", 3, 16, praise))
        assertTrue(list.hasRoomFor("John", 3, 16, prayer.lowercase()))
        assertEquals(list, list.withColor("John", 3, 16, praise, now = 5))
        assertEquals(listOf(praise), colors(list, 17))
    }

    @Test
    fun aColorAlreadyOnStaysPutAndTakesItsQuickNote() {
        var list = emptyList<HighlightItem>()
            .withColor("John", 3, 16, promise, now = 1)
            .withColor("John", 3, 16, prayer, now = 2)
        assertEquals(list, list.withColor("John", 3, 16, promise.lowercase(), now = 9))
        list = list.withColor("John", 3, 16, promise, noteId = 77, now = 10)
        assertEquals(listOf(promise, prayer), colors(list))
        assertEquals(77L, list.first().noteId)
        assertEquals(10L, list.first().updatedAt)
        assertEquals(null, list[1].noteId)
    }

    @Test
    fun colorsComeOffOneAtATimeOrAllTogetherAndCanBePutBack() {
        val start = emptyList<HighlightItem>()
            .withColor("John", 3, 16, promise, now = 1)
            .withColor("John", 3, 16, prayer, noteId = 5, now = 2)
            .withColor("John", 3, 17, comfort, now = 3)
        val withoutPromise = start.withoutColor("John", 3, 16, promise.lowercase())
        assertEquals(listOf(prayer), colors(withoutPromise))
        val cleared = start.withoutVerse("John", 3, 16)
        assertEquals(emptyList<String>(), colors(cleared))
        assertEquals(listOf(comfort), colors(cleared, 17))
        // Undo: the same highlights again, quick note and date included.
        val undone = start.onVerse("John", 3, 16).fold(cleared) { list, item -> list.withItem(item) }
        assertEquals(start.onVerse("John", 3, 16), undone.onVerse("John", 3, 16))
    }

    @Test
    fun aBackupMergesColorByColor() {
        val here = listOf(
            HighlightItem("John", 3, 16, promise, updatedAt = 10),
            HighlightItem("John", 3, 16, prayer, updatedAt = 10)
        )
        val backup = listOf(
            HighlightItem("John", 3, 16, promise, updatedAt = 20, noteId = 4), // newer: replaces
            HighlightItem("John", 3, 16, prayer, updatedAt = 5), // older: ignored
            HighlightItem("John", 3, 16, comfort, updatedAt = 6), // new color: added
            HighlightItem("Psalms", 23, 1, praise, updatedAt = 7)
        )
        val (merged, added) = mergeHighlights(here, backup, emptyMap())
        assertEquals(listOf(promise, prayer, comfort), colors(merged))
        assertEquals(4L, merged.first().noteId)
        assertEquals(10L, merged[1].updatedAt)
        assertEquals(2, added)
    }

    @Test
    fun whatWasTakenOffStaysOffUnlessHighlightedAgainSince() {
        val backup = listOf(
            HighlightItem("John", 3, 16, promise, updatedAt = 5),
            HighlightItem("John", 3, 16, prayer, updatedAt = 5),
            HighlightItem("John", 3, 17, comfort, updatedAt = 5),
            HighlightItem("John", 3, 17, praise, updatedAt = 50)
        )
        val tombstones = mapOf(
            // Prayer taken off John 3:16 after it was made...
            highlightColorTombstoneKey("John", 3, 16, prayer) to 30L,
            // ...and the whole of 3:17 cleared, but Praise put on it again later (on the other phone).
            highlightTombstoneKey("John", 3, 17) to 30L
        )
        val (merged, _) = mergeHighlights(emptyList(), backup, tombstones)
        assertEquals(listOf(promise), colors(merged))
        assertEquals(listOf(praise), colors(merged, 17))
    }

    @Test
    fun aMergeNeverLeavesAVerseInMoreThanThreeColors() {
        val here = listOf(
            HighlightItem("John", 3, 16, promise, updatedAt = 1),
            HighlightItem("John", 3, 16, prayer, updatedAt = 4)
        )
        val backup = listOf(
            HighlightItem("John", 3, 16, comfort, updatedAt = 2),
            HighlightItem("John", 3, 16, praise, updatedAt = 3)
        )
        val (merged, added) = mergeHighlights(here, backup, emptyMap())
        // The oldest (Promise) gives way; the rest keep their order.
        assertEquals(listOf(prayer, comfort, praise), colors(merged))
        assertEquals(1, added)
    }
}
