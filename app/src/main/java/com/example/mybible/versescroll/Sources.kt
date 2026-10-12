package com.example.mybible.versescroll

import kotlin.random.Random

/**
 * Where Verse Scroll's verses come from: its modes. Full and Focused deal from Discover's pool; a
 * book or chapter goes through in reading order; your highlighted or studied verses, or a search's
 * or a topic's, come shuffled.
 */
sealed interface VerseSource {
    /** What the top bar calls it. */
    val label: String

    /** A few words on what it is: the toast on leaving a Rabbit hole. */
    val blurb: String

    /** A book or chapter, read in order (the others are dealt or shuffled). */
    val inOrder: Boolean get() = this is Book || this is Chapter

    /** Every verse other verses point to, the best known two cards in three (Discover as it began). */
    data object Full : VerseSource {
        override val label = "Full"
        override val blurb = "every verse other verses point to"
    }

    /** Only the best-known verses. */
    data object Focused : VerseSource {
        override val label = "Focused"
        override val blurb = "the best-known verses"
    }

    data class Book(val book: String) : VerseSource {
        override val label get() = book
        override val blurb get() = "$book, in order"
    }

    data class Chapter(val book: String, val chapter: Int) : VerseSource {
        override val label get() = "$book $chapter"
        override val blurb get() = "$book $chapter, in order"
    }

    data object Highlighted : VerseSource {
        override val label = "Highlighted"
        override val blurb = "your highlighted verses, shuffled"
    }

    data object Studied : VerseSource {
        override val label = "Studied"
        override val blurb = "your studied verses, shuffled"
    }

    /**
     * A search's results or a topic's verses ("Scroll these"): [title] names it ("“love”", "Faith"),
     * [from] where it came from ("Search", "Nave’s topic"). Never kept for the next visit.
     */
    data class Found(val title: String, val from: String, val refs: List<VerseRef>) : VerseSource {
        override val label get() = title
        override val blurb get() = (if (refs.size == 1) "1 verse" else "${refs.size} verses") + " from $from, shuffled"
    }

    companion object {
        /** How a source is kept between visits; null for one that isn't (a search's or a topic's). */
        fun encode(source: VerseSource): String? = when (source) {
            Full -> "full"
            Focused -> "focused"
            is Book -> "book|${source.book}"
            is Chapter -> "chapter|${source.book}|${source.chapter}"
            Highlighted -> "highlighted"
            Studied -> "studied"
            is Found -> null
        }

        fun decode(value: String?): VerseSource? {
            val parts = value?.split('|') ?: return null
            return when (parts.first()) {
                "full" -> Full
                "focused" -> Focused
                "book" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Book(it) }
                "chapter" -> {
                    val book = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                    val chapter = parts.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }
                    if (book != null && chapter != null) Chapter(book, chapter) else null
                }
                "highlighted" -> Highlighted
                "studied" -> Studied
                else -> null
            }
        }
    }
}

/** A source's verses one at a time (all but Full and Focused, which deal from Discover's pool). */
interface VerseDeck {
    /** How many verses it has. */
    val size: Int

    /** The next verse; null when there are none, or none left (the end of a book or chapter). */
    fun next(): VerseRef?
}

/**
 * A book or chapter, verse by verse, in reading order: [chapters] is each chapter's number and how many
 * verses it has. Ends after the last. With [startAt] (a chapter and verse), it picks up there instead
 * of at the beginning — where it was left last time.
 */
class ReadingOrderDeck(
    private val book: String,
    private val chapters: List<Pair<Int, Int>>,
    startAt: Pair<Int, Int>? = null
) : VerseDeck {
    override val size: Int = chapters.sumOf { it.second }

    // Where it began: a chapter (its place in [chapters]) and the verse before [startAt]'s.
    private val start: Pair<Int, Int> = startAt?.let { (chapter, verse) ->
        chapters.indexOfFirst { it.first == chapter }
            .takeIf { it >= 0 && verse in 1..chapters[it].second }
            ?.let { it to verse - 1 }
    } ?: (0 to 0)

    // The chapter it's in (its place in [chapters]) and the last verse dealt from it (0: none yet).
    private var chapterIndex = start.first
    private var verse = start.second

    /**
     * Carries on after [chapter]:[verse] (the last of its verses still in the feed), or, without one,
     * from where it began — when cards dealt ahead are dropped, say on going down a Rabbit hole, so none
     * of the book's verses are skipped.
     */
    fun continueAfter(chapter: Int?, verse: Int?) {
        val index = chapter?.let { c -> chapters.indexOfFirst { it.first == c } } ?: -1
        if (index < 0 || verse == null) {
            chapterIndex = start.first
            this.verse = start.second
        } else {
            chapterIndex = index
            this.verse = verse.coerceIn(0, chapters[index].second)
        }
    }

    override fun next(): VerseRef? {
        while (chapterIndex < chapters.size) {
            val (chapter, count) = chapters[chapterIndex]
            if (verse < count) {
                verse++
                return VerseRef(book, chapter, verse)
            }
            chapterIndex++
            verse = 0
        }
        return null
    }
}

/**
 * Verses in a shuffled order, every one once a round, then shuffled again for the next round — never
 * the same verse twice running across a round's end, when there's another.
 */
class ShuffledDeck(refs: List<VerseRef>, private val random: Random = Random.Default) : VerseDeck {
    private val all = refs.distinct()
    override val size: Int = all.size
    private val round = ArrayDeque<VerseRef>()
    private var last: VerseRef? = null

    override fun next(): VerseRef? {
        if (all.isEmpty()) return null
        if (round.isEmpty()) {
            val shuffled = all.shuffled(random).toMutableList()
            if (shuffled.size > 1 && shuffled.first() == last) {
                shuffled[0] = shuffled[shuffled.lastIndex].also { shuffled[shuffled.lastIndex] = shuffled[0] }
            }
            round.addAll(shuffled)
        }
        return round.removeFirst().also { last = it }
    }
}
