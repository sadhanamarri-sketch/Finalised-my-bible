package com.example.mybible.audio

import java.text.Normalizer
import java.util.Locale

/** Matching a folder's files to a recording's by name, and saying briefly which chapters have nothing to play. */
object RecordingIndex {

    /**
     * A file's name as it's matched: without its extension, its letters as Unicode composes them,
     * lowercase, each run of spaces one space. So a copy whose name lost a double space, or that was
     * renamed from .mp3 to .m4a, is still found.
     */
    fun key(name: String): String {
        val base = name.substringBeforeLast('.').ifEmpty { name }
        return Normalizer.normalize(base, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(SPACES, " ").trim()
    }

    /**
     * How many of a recording's chapters ([timings]) have their files in a folder, the folder's files
     * given by [keys] (their names as [key] makes them).
     */
    fun chaptersHeld(timings: RecordingTimings, keys: Set<String>): Int =
        timings.chaptersByFile.entries.sumOf { (file, chapters) -> if (key(file) in keys) chapters.size else 0 }

    /**
     * [chapters] said briefly, a book at a time in the order of [all] (every chapter of the recording,
     * "Genesis 1" to "Revelation 22"): a whole book by its name ("2 Samuel"), else its chapters, runs of
     * them joined ("1 Kings 1–8, 21–22").
     */
    fun summarize(chapters: Collection<String>, all: List<String>): List<String> {
        if (chapters.isEmpty()) return emptyList()
        val wanted = chapters.toSet()
        val books = LinkedHashMap<String, MutableList<Pair<Int, Boolean>>>()
        for (name in all) {
            val book = name.substringBeforeLast(' ')
            val number = name.substringAfterLast(' ').toIntOrNull() ?: continue
            books.getOrPut(book) { mutableListOf() } += number to (name in wanted)
        }
        val out = mutableListOf<String>()
        for ((book, numbers) in books) {
            val picked = numbers.filter { it.second }.map { it.first }
            if (picked.isEmpty()) continue
            if (picked.size == numbers.size) {
                out += book
                continue
            }
            val runs = mutableListOf<String>()
            var first = picked[0]
            var last = picked[0]
            for (n in picked.drop(1) + Int.MIN_VALUE) {
                if (n == last + 1) {
                    last = n
                    continue
                }
                runs += if (first == last) "$first" else "$first–$last"
                first = n
                last = n
            }
            out += "$book ${runs.joinToString(", ")}"
        }
        return out
    }

    private val SPACES = Regex("\\s+")
}
