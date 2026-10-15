package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingTimingsTest {

    private val timings = RecordingTimings.parse(
        """
        {"recording": "A test", "how": "By hand",
         "files": {"ruth.mp3": 100.0, "jude.mp3": 50.5},
         "chapters": {
           "Ruth 1": {"file": "ruth.mp3", "start": 0.0, "end": 40.0, "verses": [[3.0, 9.0], [9.5, 20.0], [20.02, 39.0]], "unsure": [2]},
           "Ruth 2": {"file": "ruth.mp3", "start": 40.0, "end": 100.0, "verses": [[44.0, 60.0], [61.0, 99.0]]},
           "Jude 1": {"file": "jude.mp3", "start": 0.0, "end": 50.5, "verses": [[2.0, 50.0]]}
         }}
        """.trimIndent()
    )

    @Test
    fun aVerseIsPlayedFromAMomentBeforeItNeverIntoTheOneBefore() {
        // Just before it starts.
        assertEquals(VerseSpan("ruth.mp3", 9.46, 9.5, 20.0), timings.verse("Ruth", 1, 2)?.rounded())
        // Not back into the verse before, ending just before it.
        assertEquals(VerseSpan("ruth.mp3", 20.0, 20.02, 39.0), timings.verse("Ruth", 1, 3)?.rounded())
        // A chapter's first: from its spoken heading, or not.
        assertEquals(VerseSpan("ruth.mp3", 40.0, 44.0, 60.0), timings.verse("Ruth", 2, 1, heading = true)?.rounded())
        assertEquals(VerseSpan("ruth.mp3", 43.96, 44.0, 60.0), timings.verse("Ruth", 2, 1)?.rounded())
        // Nothing for what the recording hasn't.
        assertNull(timings.verse("Ruth", 2, 3))
        assertNull(timings.verse("Ruth", 3, 1))
        assertNull(timings.verse("Ruth", 1, 0))
    }

    @Test
    fun itsFilesAndChaptersAreKeptInOrder() {
        assertEquals(mapOf("ruth.mp3" to 100.0, "jude.mp3" to 50.5), timings.files)
        assertEquals(listOf("Ruth 1", "Ruth 2", "Jude 1"), timings.chapterNames)
        assertEquals(mapOf("ruth.mp3" to listOf("Ruth 1", "Ruth 2"), "jude.mp3" to listOf("Jude 1")), timings.chaptersByFile)
        assertEquals(3, timings.chapter("Ruth", 1)?.verses)
        assertEquals(99.0, timings.chapter("Ruth", 2)!!.end(2), 0.0)
    }

    private fun VerseSpan.rounded() = copy(from = Math.round(from * 1000) / 1000.0)
}
