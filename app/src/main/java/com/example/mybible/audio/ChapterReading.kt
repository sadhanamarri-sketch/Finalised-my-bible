package com.example.mybible.audio

/**
 * The Reader's chapter, read aloud from verse [from] to the chapter's end — or, as [until] says when it
 * gets there, on through the rest of the book, or on from book to book to the end of Revelation, each
 * chapter after it ([chapterAfter] says which) loaded by [load] as it's come to. Next and previous go a verse on
 * or back (back into the chapter it came from), holding previous, back to the first verse of the chapter
 * it's in, and a tap on one of that chapter's verses goes there. Played from a recording, a chapter
 * plays on from one verse to the next without a break. [onEnd] hears when it ends: where it reads to
 * (finished), or stopped.
 */
class ChapterReading(
    book: String,
    chapter: Int,
    private val from: Int,
    private val until: () -> ReadUntil = { ReadUntil.CHAPTER },
    private val chapterAfter: (book: String, chapter: Int) -> Pair<String, Int>? = { _, _ -> null },
    private val onEnd: (finished: Boolean) -> Unit = {},
    private val load: suspend (book: String, chapter: Int) -> List<VerseToRead>
) : ReadingSession {
    override val place = ReadingPlace.READER

    /** The book of the chapter it's in: the one it started in, until it goes on into another. */
    var book: String = book
        private set

    /** The chapter it's in. */
    var chapter: Int = chapter
        private set

    override val title: String get() = "$book $chapter"

    private val startedIn = book to chapter

    // The chapters it has loaded, the last few: the one it's in, the one before (for previous) and the next.
    private val loaded = object : LinkedHashMap<Pair<String, Int>, List<VerseToRead>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, List<VerseToRead>>) = size > KEPT
    }

    // Each chapter it went on into, and the one it came from.
    private val cameFrom = HashMap<Pair<String, Int>, Pair<String, Int>>()

    private suspend fun versesOf(book: String, chapter: Int): List<VerseToRead> =
        loaded[book to chapter] ?: load(book, chapter).also { loaded[book to chapter] = it }

    // Where it is now: the chapter of the verse it gives to be read.
    private fun at(verse: VerseToRead): VerseToRead {
        book = verse.book
        chapter = verse.chapter
        return verse
    }

    override suspend fun next(after: VerseToRead?): VerseToRead? {
        if (after == null) return versesOf(startedIn.first, startedIn.second).firstOrNull { it.verse >= from }?.let(::at)
        val all = versesOf(after.book, after.chapter)
        val i = all.indexOfFirst { it.verse == after.verse }
        if (i < 0) return null
        all.getOrNull(i + 1)?.let { return at(it) }
        // The chapter's end: on into the next, as far as it reads to.
        val here = after.book to after.chapter
        val next = chapterAfter(after.book, after.chapter) ?: return null
        when (until()) {
            ReadUntil.CHAPTER -> return null
            ReadUntil.BOOK -> if (next.first != after.book) return null
            ReadUntil.CONTINUOUS -> Unit
        }
        cameFrom[next] = here
        return versesOf(next.first, next.second).firstOrNull()?.let(::at)
    }

    // A headset's "previous": the verse before, back to the chapter's first (even before [from]), and from
    // there into the chapter it came from.
    override suspend fun back(current: VerseToRead): VerseToRead? {
        val all = versesOf(current.book, current.chapter)
        val i = all.indexOfFirst { it.verse == current.verse }
        if (i > 0) return at(all[i - 1])
        val before = cameFrom[current.book to current.chapter] ?: return null
        return versesOf(before.first, before.second).lastOrNull()?.let(::at)
    }

    // Holding "previous": the first verse of the chapter it's in, wherever the reading started.
    override suspend fun first(): VerseToRead? = versesOf(book, chapter).firstOrNull()?.let(::at)

    // A tap on one of the verses of the chapter it's in, before or after the one being read.
    override suspend fun verse(book: String, chapter: Int, verse: Int): VerseToRead? {
        if (book != this.book || chapter != this.chapter) return null
        return versesOf(book, chapter).firstOrNull { it.verse == verse }?.let(::at)
    }

    // The rest of the verse's chapter, which a recording plays on through.
    override suspend fun following(verse: VerseToRead): List<VerseToRead> {
        val all = versesOf(verse.book, verse.chapter)
        val i = all.indexOfFirst { it.verse == verse.verse }
        return if (i < 0) emptyList() else all.drop(i + 1)
    }

    override fun onStopped(finished: Boolean) = onEnd(finished)

    private companion object {
        const val KEPT = 3
    }
}
