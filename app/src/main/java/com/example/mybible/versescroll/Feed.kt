package com.example.mybible.versescroll

import kotlin.random.Random

/** A verse as the app's database names it, e.g. Isaiah 41:10. */
data class VerseRef(val book: String, val chapter: Int, val verse: Int) {
    val key: String get() = "$book $chapter:$verse"
}

/** One verse of a card, with its Telugu translation when the app has it. */
data class VerseLine(val number: Int, val text: String, val telugu: String?)

/** What a verse card shows: the verse (or a short passage), the verses either side, and its link count. */
data class VerseCardContent(
    val ref: VerseRef,
    val lines: List<VerseLine>,
    val before: VerseLine?,
    val after: VerseLine?,
    val linkCount: Int
) {
    val refs: List<VerseRef> get() = lines.map { VerseRef(ref.book, ref.chapter, it.number) }
    val wordCount: Int get() = lines.sumOf { line -> line.text.split(' ').count { it.isNotBlank() } }

    /** "Isaiah 41:10", or "Isaiah 40:29–31" for a passage. */
    val label: String get() = labelOf(refs)
}

sealed interface FeedCard {
    /** Unique within a feed, so the same verse shown twice gets two cards. */
    val uid: Long
}

data class VerseFeedCard(
    override val uid: Long,
    val content: VerseCardContent,
    val sceneId: String?,
    /** On a Rabbit hole: which thread, and the index of the card it started from. */
    val threadId: Int? = null,
    val rootIndex: Int? = null,
    /** On a Rabbit hole: the verse this one is linked from. */
    val from: LinkedFrom? = null,
    /** The first card of a fresh thread, started because the last one ran out of links to follow. */
    val newThread: Boolean = false
) : FeedCard

/** "Linked from Isaiah 41:10 · 2 of 3". */
data class LinkedFrom(val ref: VerseRef, val label: String, val nth: Int, val of: Int)

/**
 * A pause every [CHECK_IN_EVERY] verses: how far you've come, and an offer to read a chapter slowly.
 * Inside a Rabbit hole it also offers the way back to Discover.
 */
data class CheckInFeedCard(override val uid: Long, val threadId: Int? = null, val rootIndex: Int? = null) : FeedCard

enum class FeedMode { DISCOVER, RABBIT_HOLE }

/** Asks the screen to move to a card (after following a link, or on leaving a Rabbit hole). */
data class ScrollRequest(val index: Int, val id: Long)

/** A label for a verse or short passage: "Isaiah 41:10", "Isaiah 40:29–31". */
fun labelOf(refs: List<VerseRef>): String =
    if (refs.size > 1) "${refs.first().key}\u2013${refs.last().verse}" else refs.first().key

const val CHECK_IN_EVERY = 20

/** Verses longer than this get the easier-reading treatment: a soft panel, bigger text, no context lines. */
const val LONG_VERSE_WORDS = 40

data class DiscoverEntry(val ref: VerseRef, val weight: Double)

/** Reads assets/verse_scroll/discover.tsv: book, chapter, verse and weight per line, "#" for comments. */
fun parseDiscoverPool(tsv: String): List<DiscoverEntry> =
    tsv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) return@mapNotNull null
            val chapter = p[1].toIntOrNull() ?: return@mapNotNull null
            val verse = p[2].toIntOrNull() ?: return@mapNotNull null
            val weight = p[3].toDoubleOrNull() ?: return@mapNotNull null
            DiscoverEntry(VerseRef(p[0], chapter, verse), weight)
        }
        .toList()

/**
 * Discover's supply of verses: widely referenced verses from across the Bible, the best known coming up
 * most often. Each verse is used once per round through the pool, and never again until [recentLimit]
 * other verses have been shown (the recent list is kept between visits by the caller).
 */
class DiscoverPool(
    private val entries: List<DiscoverEntry>,
    private val random: Random = Random.Default,
    private val recentLimit: Int = 400
) {
    private val pool = mutableListOf<DiscoverEntry>()
    private val recent = ArrayDeque<String>()

    val isEmpty: Boolean get() = entries.isEmpty()

    fun restoreRecent(keys: List<String>) {
        recent.clear()
        keys.takeLast(recentLimit).forEach { recent.addLast(it) }
        pool.clear()
    }

    fun recentKeys(): List<String> = recent.toList()

    fun next(): VerseRef? {
        if (entries.isEmpty()) return null
        if (pool.isEmpty()) refill()
        val total = pool.sumOf { it.weight }
        var r = random.nextDouble() * total
        var i = 0
        while (i < pool.size - 1) {
            r -= pool[i].weight
            if (r <= 0) break
            i++
        }
        val picked = pool.removeAt(i)
        recent.addLast(picked.ref.key)
        while (recent.size > recentLimit) recent.removeFirst()
        return picked.ref
    }

    private fun refill() {
        val avoid = recent.toHashSet()
        pool.addAll(entries.filter { it.ref.key !in avoid })
        if (pool.isEmpty()) pool.addAll(entries)
    }
}
