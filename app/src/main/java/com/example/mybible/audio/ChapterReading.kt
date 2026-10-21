package com.example.mybible.audio

/**
 * The Reader's chapter, read aloud from verse [from] to the chapter's end — where it stops (it doesn't
 * go on to the next chapter); next and previous go a verse on or back, holding previous, back to the
 * chapter's first verse, and a tap on one of its verses goes there. Its verses are loaded as it starts,
 * by [load]. Played from a recording, the chapter plays on from one verse to the next without a break.
 * [onEnd] hears when it ends: at the chapter's end (finished), or stopped.
 */
class ChapterReading(
    val book: String,
    val chapter: Int,
    private val from: Int,
    private val onEnd: (finished: Boolean) -> Unit = {},
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

    // A headset's "previous": the verse before, back to the chapter's first (even before [from]).
    override suspend fun back(current: VerseToRead): VerseToRead? {
        val all = verses ?: load().also { verses = it }
        val i = all.indexOfFirst { it.verse == current.verse }
        return if (i <= 0) null else all[i - 1]
    }

    // Holding "previous": the chapter's first verse, wherever the reading started.
    override suspend fun first(): VerseToRead? = (verses ?: load().also { verses = it }).firstOrNull()

    // A tap on one of the chapter's verses, before or after [from].
    override suspend fun verse(book: String, chapter: Int, verse: Int): VerseToRead? {
        if (book != this.book || chapter != this.chapter) return null
        return (verses ?: load().also { verses = it }).firstOrNull { it.verse == verse }
    }

    // The rest of the chapter, which a recording plays on through.
    override suspend fun following(verse: VerseToRead): List<VerseToRead> {
        val all = verses ?: load().also { verses = it }
        val i = all.indexOfFirst { it.verse == verse.verse }
        return if (i < 0) emptyList() else all.drop(i + 1)
    }

    override fun onStopped(finished: Boolean) = onEnd(finished)
}
