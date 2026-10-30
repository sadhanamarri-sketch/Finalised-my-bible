package com.example.mybible.audio

/**
 * How loud a narrated recording is (see Loudness), measured once from a sample of it: about 20 seconds
 * of speech from each of a dozen chapters spread through it, from each chapter's second verse (so not
 * its spoken heading, or the music the KJV Dramatized Audio opens a book with).
 */
object RecordingLoudness {
    /** A stretch of one of the recording's files ([file], by its name), from [from] to [to] seconds. */
    data class Stretch(val file: String, val from: Double, val to: Double)

    /** Where to listen: up to [count] chapters spread through those whose files are [held], [seconds] of each. */
    fun stretches(timings: RecordingTimings, held: Set<String>, count: Int = CHAPTERS, seconds: Double = SECONDS): List<Stretch> {
        val chapters = timings.chapterNames.mapNotNull { timings.chapter(it) }.filter { it.file in held && it.verses > 0 }
        if (chapters.isEmpty() || count <= 0) return emptyList()
        val picked = if (chapters.size <= count || count == 1) chapters.take(count)
            else (0 until count).map { chapters[it * (chapters.size - 1) / (count - 1)] }
        return picked.map { chapter ->
            val from = chapter.start(if (chapter.verses >= 2) 2 else 1)
            Stretch(chapter.file, from, minOf(from + seconds, chapter.end(chapter.verses)))
        }
    }

    /**
     * How loud the recording is, in LUFS, from [stretches] each decoded by [read] (handing over its sound
     * in one channel and its sample rate; false when it couldn't be read); null when none could be.
     */
    fun measure(stretches: List<Stretch>, read: (Stretch, (FloatArray, Int) -> Unit) -> Boolean): Double? {
        val loudness = Loudness()
        for (stretch in stretches) {
            loudness.gap()
            read(stretch) { samples, rate -> loudness.add(samples, rate) }
        }
        return loudness.lufs()
    }

    private const val CHAPTERS = 12
    private const val SECONDS = 20.0
}
