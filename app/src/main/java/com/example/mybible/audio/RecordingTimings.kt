package com.example.mybible.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Where each verse is in a narrated recording of the Bible, worked out once for the whole recording
 * (data/audio_timing, made by tools/audio_timing): its files and how long each plays, and for each
 * chapter the file it's in, where it starts and ends there (its spoken heading first) and where each
 * verse starts and ends, in seconds from the file's start. Chapters are the app's ("Psalms 23").
 */
class RecordingTimings(
    /** Each of the recording's files, by name, and how long it plays, in seconds. */
    val files: Map<String, Double>,
    private val chapters: LinkedHashMap<String, Chapter>
) {
    /** A chapter: the file it's in, its place there, and each of its verses' (verse 1 first). */
    class Chapter(val file: String, val start: Double, val end: Double, private val spans: DoubleArray) {
        val verses: Int get() = spans.size / 2

        fun start(verse: Int): Double = spans[(verse - 1) * 2]

        fun end(verse: Int): Double = spans[(verse - 1) * 2 + 1]
    }

    fun chapter(book: String, chapter: Int): Chapter? = chapters["$book $chapter"]

    /** A chapter by its name ("Psalms 23"). */
    fun chapter(name: String): Chapter? = chapters[name]

    /** Every chapter ("Genesis 1" to "Revelation 22"), in the Bible's order. */
    val chapterNames: List<String> get() = chapters.keys.toList()

    /** The chapters each file holds ("Ruth 1", "Ruth 2", ...), in the Bible's order. */
    val chaptersByFile: Map<String, List<String>> by lazy {
        chapters.entries.groupBy({ it.value.file }, { it.key })
    }

    /**
     * Where [verse] of [book] [chapter] is: null when the recording hasn't it. It's played from a moment
     * before it starts, never back into the verse before; a chapter's first verse, with [heading], from
     * the chapter's spoken heading ("Genesis chapter 1").
     */
    fun verse(book: String, chapter: Int, verse: Int, heading: Boolean = false): VerseSpan? {
        val timing = chapter(book, chapter) ?: return null
        if (verse < 1 || verse > timing.verses) return null
        val start = timing.start(verse)
        val before = if (verse == 1) timing.start else timing.end(verse - 1)
        val from = if (verse == 1 && heading) timing.start else maxOf(before, start - LEAD_SECONDS).coerceAtMost(start)
        return VerseSpan(timing.file, from, start, timing.end(verse))
    }

    companion object {
        // How long before a verse's first sound it's played from: an MP3's frames are 26 ms each, and
        // playing starts at the first whole one, so none of the first word is lost.
        const val LEAD_SECONDS = 0.04

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): RecordingTimings {
            val root = json.parseToJsonElement(text).jsonObject
            val files = root.getValue("files").jsonObject.mapValues { it.value.jsonPrimitive.double }
            val chapters = LinkedHashMap<String, Chapter>()
            for ((name, value) in root.getValue("chapters").jsonObject) {
                val chapter = value.jsonObject
                val verses = chapter.getValue("verses").jsonArray
                val spans = DoubleArray(verses.size * 2)
                verses.forEachIndexed { i, span ->
                    val pair = span.jsonArray
                    spans[i * 2] = pair[0].jsonPrimitive.double
                    spans[i * 2 + 1] = pair[1].jsonPrimitive.double
                }
                chapters[name] = Chapter(
                    file = chapter.getValue("file").jsonPrimitive.content,
                    start = chapter.getValue("start").jsonPrimitive.double,
                    end = chapter.getValue("end").jsonPrimitive.double,
                    spans = spans
                )
            }
            return RecordingTimings(files, chapters)
        }
    }
}

/**
 * A verse's place in one of a recording's files ([file] by its name), in seconds: where to play it from
 * ([from], a moment before it [start]s) and where it ends.
 */
data class VerseSpan(val file: String, val from: Double, val start: Double, val end: Double)
