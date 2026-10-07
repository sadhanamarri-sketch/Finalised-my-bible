package com.example.mybible.versescroll

/** A Rabbit hole only follows a link on its own when at least this many readers voted it helpful. */
const val MIN_AUTO_VOTES = 5

/** Word overlap (Jaccard) at which two passages count as the same saying, e.g. Matthew and Luke's parallels. */
const val NEAR_DUPLICATE = 0.45

/** Links a verse's Links sheet lists. */
const val LINKS_SHOWN = 6

/** Links weaker than this aren't suggested at all (Cross References still lists them). */
const val MIN_SUGGESTED_VOTES = 3

/** Each hub verse hands the thread this many of its strongest links, one after another. */
private const val PER_HUB = 3

/**
 * Where a link points: one verse, or the first of a short passage ([count] verses, 2 or 3), and how
 * strong it is.
 */
data class Link(val ref: VerseRef, val votes: Int, val count: Int = 1) {
    val refs: List<VerseRef> get() = (0 until count).map { VerseRef(ref.book, ref.chapter, ref.verse + it) }
}

/** What the thread needs to know about verses: their links, strongest first, and their words. */
interface LinkSource {
    suspend fun links(ref: VerseRef): List<Link>

    /** The meaningful words of these verses (no "the", "and", "thee"...), for spotting near-duplicates. */
    suspend fun words(refs: List<VerseRef>): Set<String>
}

/**
 * The next card of a thread: the link to show, the verse it was linked from, and its place among that
 * verse's picks ("2 of 3").
 */
data class ThreadStep(
    val link: Link,
    val from: VerseRef,
    val fromRefs: List<VerseRef>,
    val nth: Int,
    val of: Int
)

/**
 * One Rabbit hole: a trail through cross references that starts at a verse (the root) and stays on its
 * topic. A verse's three strongest links come one after another, then the strongest of those becomes
 * the next verse whose links are followed. It never repeats a verse it has shown (or one seen before it
 * started), never shows two near-identical parallels, never sits in one chapter for long, and only
 * follows strong links. When a trail runs dry it backs up to an earlier verse with links left; when
 * none is left, the caller starts a new thread.
 */
class Thread internal constructor(
    val id: Int,
    val rootIndex: Int,
    seenKeys: Collection<String>,
    rootRefs: List<VerseRef>
) {
    internal data class Stop(val index: Int, val refs: List<VerseRef>) {
        val ref: VerseRef get() = refs.first()
    }

    private val seed: Set<String> = HashSet(seenKeys) + rootRefs.map { it.key }
    internal val path = mutableListOf(Stop(rootIndex, rootRefs))
    internal var hub: VerseRef = rootRefs.first()
    internal var nextHub: VerseRef? = null
    internal val queue = ArrayDeque<Triple<Link, Int, Int>>()
    internal val visited = HashSet<String>()
    internal val shown = mutableListOf<Set<String>>()

    val root: VerseRef get() = path.first().ref
    val rootRefs: List<VerseRef> get() = path.first().refs

    internal suspend fun rebuild(source: LinkSource) {
        visited.clear()
        visited.addAll(seed)
        shown.clear()
        path.forEach { stop ->
            stop.refs.forEach { visited.add(it.key) }
            shown.add(source.words(stop.refs))
        }
    }
}

class RabbitHole(private val source: LinkSource) {
    private var lastId = 0

    /** Starts a thread at the card at [rootIndex], remembering every verse already shown in the feed. */
    suspend fun start(rootIndex: Int, rootRefs: List<VerseRef>, seenKeys: Collection<String>): Thread {
        val thread = Thread(++lastId, rootIndex, seenKeys, rootRefs)
        thread.rebuild(source)
        return thread
    }

    /**
     * Picks the thread up again from the card at [index] (which must be on the thread's path): forgets
     * anything after it, so the next picks follow on from that verse.
     */
    suspend fun restartFrom(thread: Thread, index: Int, ref: VerseRef) {
        thread.path.retainAll { it.index <= index }
        thread.rebuild(source)
        thread.queue.clear()
        thread.nextHub = null
        thread.hub = ref
    }

    /** Puts a link the reader chose onto the thread, as the card at [index], linked from [from]. */
    suspend fun take(thread: Thread, link: Link, from: VerseRef, index: Int, nth: Int = 1, of: Int = 1): ThreadStep {
        val fromRefs = thread.path.find { it.ref == from }?.refs ?: listOf(from)
        val refs = link.refs
        thread.path.add(Thread.Stop(index, refs))
        refs.forEach { thread.visited.add(it.key) }
        thread.shown.add(source.words(refs))
        return ThreadStep(link, from, fromRefs, nth, of)
    }

    /** After a chosen link, the thread carries on from it. */
    fun continueFrom(thread: Thread, ref: VerseRef) {
        thread.hub = ref
    }

    /** The thread's next card, to be shown at [index]; null once nothing anywhere on it is left to follow. */
    suspend fun next(thread: Thread, index: Int): ThreadStep? {
        if (thread.queue.isEmpty()) {
            var hub = thread.nextHub ?: thread.hub
            var picked = pick(thread, hub, PER_HUB)
            // Dead end: back up along the trail to the nearest verse with strong links left.
            var j = thread.path.size - 1
            while (picked.isEmpty() && j >= 0) {
                hub = thread.path[j].ref
                picked = pick(thread, hub, PER_HUB)
                j--
            }
            if (picked.isEmpty()) return null
            thread.hub = hub
            picked.forEachIndexed { i, link -> thread.queue.addLast(Triple(link, i + 1, picked.size)) }
            thread.nextHub = picked.first().ref
        }
        val (link, nth, of) = thread.queue.removeFirst()
        return take(thread, link, thread.hub, index, nth, of)
    }

    /** The strongest acceptable links of one verse, also checked against each other. */
    private suspend fun pick(thread: Thread, from: VerseRef, max: Int): List<Link> {
        val picked = mutableListOf<Link>()
        val extraKeys = HashSet<String>()
        val extraWords = mutableListOf<Set<String>>()
        val extraChapters = mutableListOf<String>()
        for (link in source.links(from)) {
            if (picked.size >= max) break
            if (link.votes < MIN_AUTO_VOTES) break   // strongest first, so nothing after this qualifies
            val refs = link.refs
            if (refs.any { it.key in thread.visited || it.key in extraKeys }) continue
            val words = source.words(refs)
            if (thread.shown.any { similarity(words, it) >= NEAR_DUPLICATE } ||
                extraWords.any { similarity(words, it) >= NEAR_DUPLICATE }
            ) continue
            // At most two of the last four cards from one chapter.
            val recent = (thread.path.takeLast(4).map { chapterOf(it.ref) } + extraChapters).takeLast(4)
            if (recent.count { it == chapterOf(link.ref) } >= 2) continue
            picked.add(link)
            refs.forEach { extraKeys.add(it.key) }
            extraWords.add(words)
            extraChapters.add(chapterOf(link.ref))
        }
        return picked
    }

    companion object {
        fun chapterOf(ref: VerseRef) = "${ref.book} ${ref.chapter}"

        fun similarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() && b.isEmpty()) return 0.0
            val shared = a.count { it in b }
            val union = a.size + b.size - shared
            return if (union == 0) 0.0 else shared.toDouble() / union
        }

        private val STOP = (
            "the and of to that in he shall unto for i his a they be is him not them it with all thou was thy " +
                "which my me but ye have their as are from by thee this will hath upon were on when an or there so " +
                "no out also who what then into said one if do let our we us you your her she"
            ).split(' ').toSet()
        private val NON_LETTERS = Regex("[^a-z\\s]")
        private val SPACES = Regex("\\s+")

        /** The meaningful words of a text: lower case, letters only, without the commonest words. */
        fun wordsOf(text: String): Set<String> =
            text.lowercase().replace(NON_LETTERS, " ").split(SPACES).filter { it.isNotEmpty() && it !in STOP }.toSet()
    }
}
