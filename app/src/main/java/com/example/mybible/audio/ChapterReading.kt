package com.example.mybible.audio

/**
 * The Reader's chapter, read aloud from verse [from] to the chapter's end — where it stops (it doesn't
 * go on to the next chapter). Its verses are loaded as it starts, by [load].
 */
class ChapterReading(
    val book: String,
    val chapter: Int,
    private val from: Int,
    private val load: suspend () -> List<VerseToRead>
) : ReadingSession {
    override val place = ReadingPlace.READER
    override val title = "$book $chapter"

    private var verses: List<VerseToRead>? = null

    override suspend fun next(after: VerseToRead?): VerseToRead? {
        val all = verses ?: load().also { verses = it }
        if (after == null) return all.firstOrNull { it.verse >= from }
        val i = all.indexOfFirst { it.verse == after.verse }
        return if (i < 0) null else all.getOrNull(i + 1)
    }
}
