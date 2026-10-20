package com.example.mybible.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The recordings' timings as the app carries them (data/audio_timing): every chapter, its verses in order. */
class BundledTimingsTest {

    private fun load(name: String) = RecordingTimings.parse(File("../data/audio_timing/$name").readText())

    @Test
    fun everyChapterIsThereItsVersesInOrderWithinTheirFile() {
        val kjv = load("kjv.json")
        for (name in listOf("kjv.json", "kjv_dramatized.json", "telugu.json")) {
            val timings = load(name)
            assertEquals(name, 1189, timings.chapterNames.size)
            for (chapter in timings.chapterNames) {
                val book = chapter.substringBeforeLast(' ')
                val number = chapter.substringAfterLast(' ').toInt()
                val timing = timings.chapter(book, number)!!
                val where = "$name, $chapter"
                // The English recordings are of the same verses (the app's Telugu Exodus 7 has one fewer).
                if (name != "telugu.json") assertEquals(where, kjv.chapter(book, number)!!.verses, timing.verses)
                var last = timing.start
                for (verse in 1..timing.verses) {
                    assertTrue("$where:$verse starts before the verse before ends", timing.start(verse) >= last - 1e-6)
                    assertTrue("$where:$verse ends before it starts", timing.end(verse) > timing.start(verse))
                    last = timing.end(verse)
                }
                assertTrue("$where ends after its chapter", last <= timing.end + 1e-6)
                // Times are given to the hundredth of a second, file lengths to the thousandth.
                assertTrue("$where ends after its file", timing.end <= timings.files.getValue(timing.file) + 0.01)
            }
        }
    }
}
