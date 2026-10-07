package com.example.mybible.versescroll

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.mybible.data.BibleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything behind the Verse Scroll screen that should outlive it: the feed itself (so a trip to the
 * Reader via "Read" and back lands on the same card), its settings, today's verse count, and the scene
 * pictures. Owned by MainViewModel; the screen only reads this state and calls back into it.
 */
class VerseScrollController(
    private val context: Context,
    private val repository: BibleRepository,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // ---- settings ----

    /** Painted scenes behind each verse, or the plain theme background. */
    var paintedScenes by mutableStateOf(prefs.getBoolean(KEY_SCENES, true))
        private set

    /** The slow drift and zoom plus each scene's small moving touch (never when the phone asks for less motion). */
    var motion by mutableStateOf(prefs.getBoolean(KEY_MOTION, true))
        private set

    /** The color a double-tap highlights with. */
    var doubleTapColor by mutableStateOf(prefs.getString(KEY_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR)
        private set

    /** The Telugu translation under each verse. */
    var showTelugu by mutableStateOf(prefs.getBoolean(KEY_TELUGU, false))
        private set

    /** Whether the first-time "double-tap a verse" hint has been dismissed. */
    var hintSeen by mutableStateOf(prefs.getBoolean(KEY_HINT_SEEN, false))
        private set

    fun updatePaintedScenes(on: Boolean) {
        paintedScenes = on
        prefs.edit().putBoolean(KEY_SCENES, on).apply()
    }

    fun updateMotion(on: Boolean) {
        motion = on
        prefs.edit().putBoolean(KEY_MOTION, on).apply()
    }

    fun updateDoubleTapColor(hex: String) {
        doubleTapColor = hex
        prefs.edit().putString(KEY_COLOR, hex).apply()
    }

    fun updateShowTelugu(on: Boolean) {
        showTelugu = on
        prefs.edit().putBoolean(KEY_TELUGU, on).apply()
    }

    fun dismissHint() {
        if (hintSeen) return
        hintSeen = true
        prefs.edit().putBoolean(KEY_HINT_SEEN, true).apply()
    }

    // ---- bundled data ----

    private var scenes: List<SceneSpec> = emptyList()
    private val sceneById = HashMap<String, SceneSpec>()
    private var discover: DiscoverPool? = null
    private val loadLock = Mutex()

    fun scene(id: String?): SceneSpec? = id?.let { sceneById[it] }

    /** How many painted scenes there are (0 until the catalog has loaded). */
    val sceneCount: Int get() = scenes.size

    private suspend fun ensureLoaded() = loadLock.withLock {
        if (discover != null) return@withLock
        withContext(Dispatchers.IO) {
            val catalog = context.assets.open("verse_scroll/scenes.json").bufferedReader().use { it.readText() }
            scenes = json.decodeFromString(SceneCatalog.serializer(), catalog).scenes
            scenes.forEach { sceneById[it.id] = it }
            val tsv = context.assets.open("verse_scroll/discover.tsv").bufferedReader().use { it.readText() }
            discover = DiscoverPool(parseDiscoverPool(tsv)).also { pool ->
                pool.restoreRecent(prefs.getString(KEY_RECENT, "").orEmpty().split('|').filter { it.isNotBlank() })
            }
        }
    }

    // ---- the feed ----

    val cards = mutableStateListOf<FeedCard>()

    /** Goes up whenever the feed starts over, so the screen starts its pager over too. */
    var feedId by mutableIntStateOf(0)
        private set

    /** The card on screen. */
    var currentIndex by mutableIntStateOf(0)
        private set

    /** True when not a single verse could be loaded — the Bible text hasn't finished downloading yet. */
    var waitingForBibleText by mutableStateOf(false)
        private set

    /** Verse cards seen today, across visits. */
    var versesToday by mutableIntStateOf(0)
        private set

    /** Verses highlighted in Verse Scroll during this visit, for the check-in card. */
    var highlightedThisVisit by mutableIntStateOf(0)
        private set
    private val highlightedKeys = HashSet<String>()

    /** Discover, or following links down a Rabbit hole. */
    var mode by mutableStateOf(FeedMode.DISCOVER)
        private set

    /** A card the screen should move to; it calls [consumeScrollRequest] once it has. */
    var scrollRequest by mutableStateOf<ScrollRequest?>(null)
        private set

    fun consumeScrollRequest() {
        scrollRequest = null
    }

    private var thread: Thread? = null
    private var scrollIds = 0L

    private var nextUid = 1L
    private var sinceCheckIn = 0
    private var lastSceneType: String? = null
    private val viewedUids = HashSet<Long>()
    private var generating: Job? = null
    private var leftAtMillis = 0L

    /** The screen opened: keep the feed from earlier in this session, unless it was left a while ago. */
    fun onOpen() {
        val stale = leftAtMillis > 0 && System.currentTimeMillis() - leftAtMillis > FRESH_FEED_AFTER_MS
        if (stale || waitingForBibleText) reset()
        leftAtMillis = 0
        versesToday = readTodayCount()
        if (cards.size - currentIndex < CARDS_AHEAD) generateMore()
    }

    /** The screen closed (to the Reader, or out of the app's view). */
    fun onLeave() {
        leftAtMillis = System.currentTimeMillis()
        saveRecent()
    }

    private fun reset() {
        generating?.cancel()
        feedId++
        cards.clear()
        mode = FeedMode.DISCOVER
        thread = null
        scrollRequest = null
        currentIndex = 0
        sinceCheckIn = 0
        lastSceneType = null
        viewedUids.clear()
        highlightedKeys.clear()
        highlightedThisVisit = 0
        waitingForBibleText = false
    }

    /** A card settled on screen. */
    fun onCardShown(index: Int) {
        currentIndex = index
        val card = cards.getOrNull(index)
        if (card is VerseFeedCard && viewedUids.add(card.uid)) {
            versesToday = bumpTodayCount()
            if (viewedUids.size % 10 == 0) saveRecent()
        }
        if (cards.size - index < CARDS_AHEAD) generateMore()
    }

    fun noteHighlighted(keys: List<String>, on: Boolean) {
        keys.forEach { if (on) highlightedKeys.add(it) else highlightedKeys.remove(it) }
        highlightedThisVisit = highlightedKeys.size
    }

    // [afterFirst] runs once the first new card is in place (e.g. to move on to it).
    private fun generateMore(count: Int = CARDS_PER_BATCH, afterFirst: (() -> Unit)? = null) {
        if (generating?.isActive == true) return
        generating = scope.launch {
            ensureLoaded()
            repeat(count) { i ->
                val card = nextCard() ?: return@launch
                cards.add(card)
                if (i == 0) afterFirst?.invoke()
            }
        }
    }

    private suspend fun nextCard(): FeedCard? {
        val inThread = thread.takeIf { mode == FeedMode.RABBIT_HOLE }
        if (sinceCheckIn >= CHECK_IN_EVERY) {
            sinceCheckIn = 0
            return CheckInFeedCard(nextUid++, inThread?.id, inThread?.rootIndex)
        }
        if (inThread != null) {
            val step = rabbitHole.next(inThread, cards.size)
            val content = step?.let { loadContent(it.link.ref, it.link.count) }
            if (step != null && content != null) {
                sinceCheckIn++
                return VerseFeedCard(
                    nextUid++, content, pickScene(content), inThread.id, inThread.rootIndex,
                    LinkedFrom(step.from, labelOf(step.fromRefs), step.nth, step.of)
                )
            }
            // Nothing left anywhere on this thread: a fresh one from a Discover verse.
            return startNewThread()
        }
        return discoverCard()
    }

    private suspend fun discoverCard(newThread: Boolean = false): VerseFeedCard? {
        val pool = discover ?: return null
        // A verse that isn't in the database yet (the Bible text still downloading) is skipped.
        repeat(MAX_SKIPS) {
            val ref = pool.next() ?: return null
            val content = loadContent(ref, 1) ?: return@repeat
            sinceCheckIn++
            waitingForBibleText = false
            return VerseFeedCard(nextUid++, content, pickScene(content), newThread = newThread)
        }
        if (cards.none { it is VerseFeedCard }) waitingForBibleText = true
        return null
    }

    private suspend fun startNewThread(): FeedCard? {
        val card = discoverCard(newThread = true) ?: return null
        val index = cards.size
        val started = rabbitHole.start(index, card.content.refs, seenKeys())
        thread = started
        return card.copy(threadId = started.id, rootIndex = index)
    }

    private fun seenKeys(): List<String> =
        cards.flatMap { (it as? VerseFeedCard)?.content?.refs.orEmpty() }.map { it.key }

    // Drops every card after [index] (ones not yet seen), e.g. to change where the feed goes next.
    private fun truncateAfter(index: Int) {
        while (cards.size > index + 1) cards.removeAt(cards.lastIndex)
        sinceCheckIn = cards.asReversed().takeWhile { it is VerseFeedCard }.size
    }

    // ---- the Rabbit hole ----

    private val rabbitHole by lazy { RabbitHole(linkSource) }

    /**
     * Goes down a Rabbit hole from the card at [index]: with [link], that verse comes next; without,
     * the verse's own strongest links do. [advance] moves on to the next card as soon as it's ready.
     */
    fun followFrom(index: Int, link: Link?, advance: Boolean) {
        val card = cards.getOrNull(index) as? VerseFeedCard ?: return
        generating?.cancel()
        generating = scope.launch {
            ensureLoaded()
            val upto = maxOf(index, currentIndex)
            truncateAfter(upto)
            var current = thread
            if (current == null || card.threadId != current.id) {
                current = rabbitHole.start(index, card.content.refs, seenKeys())
                thread = current
                cards[index] = card.copy(threadId = current.id, rootIndex = index)
            } else {
                rabbitHole.restartFrom(current, upto, card.content.ref)
            }
            mode = FeedMode.RABBIT_HOLE
            if (link != null) {
                val content = loadContent(link.ref, link.count)
                if (content != null) {
                    val step = rabbitHole.take(current, link, card.content.ref, cards.size)
                    sinceCheckIn++
                    cards.add(
                        VerseFeedCard(
                            nextUid++, content, pickScene(content), current.id, current.rootIndex,
                            LinkedFrom(step.from, labelOf(step.fromRefs), step.nth, step.of)
                        )
                    )
                    rabbitHole.continueFrom(current, link.ref)
                }
            }
            // The next card first, so the screen can move to it straight away; then a few more after it.
            if (cards.size == upto + 1) nextCard()?.let { cards.add(it) }
            if (advance && cards.size > upto + 1) scrollRequest = ScrollRequest(upto + 1, ++scrollIds)
            repeat(CARDS_PER_BATCH - 1) {
                val next = nextCard() ?: return@launch
                cards.add(next)
            }
        }
    }

    /**
     * Leaves the Rabbit hole: back to the verse it started from ([toRoot]: the "Back to …" button and
     * the back gesture), or right where you are (the Discover tab, a check-in's button). Discover takes
     * over from that card on. Returns the card to show, or null when not in a Rabbit hole. With
     * [advance], moves on to the first Discover card once it's ready.
     */
    fun leaveRabbitHole(toRoot: Boolean, advance: Boolean = false): Int? {
        if (mode != FeedMode.RABBIT_HOLE) return null
        val root = rootIndexOf(cards.getOrNull(currentIndex))?.takeIf { it <= currentIndex && it < cards.size }
        val target = if (toRoot && root != null) root else currentIndex
        generating?.cancel()
        mode = FeedMode.DISCOVER
        truncateAfter(target)
        currentIndex = target
        generateMore(afterFirst = { if (advance) scrollRequest = ScrollRequest(target + 1, ++scrollIds) })
        return target
    }

    private fun rootIndexOf(card: FeedCard?): Int? = when (card) {
        is VerseFeedCard -> card.rootIndex
        is CheckInFeedCard -> card.rootIndex
        else -> null
    }

    /** The verse the Rabbit hole on screen started from, while the verse card at [index] is past it. */
    fun rabbitHoleRoot(index: Int): VerseFeedCard? {
        if (mode != FeedMode.RABBIT_HOLE) return null
        val root = (cards.getOrNull(index) as? VerseFeedCard)?.rootIndex?.takeIf { it < index } ?: return null
        return cards.getOrNull(root) as? VerseFeedCard
    }

    /** Whether a check-in card belongs to the Rabbit hole being followed (and so offers the way out). */
    fun isInRabbitHole(card: CheckInFeedCard): Boolean =
        mode == FeedMode.RABBIT_HOLE && card.threadId != null && card.threadId == thread?.id

    /** The nearest verse card at or before [index]. */
    fun verseIndexAtOrBefore(index: Int): Int? =
        (minOf(index, cards.lastIndex) downTo 0).firstOrNull { cards[it] is VerseFeedCard }

    data class LinkPreview(val link: Link, val content: VerseCardContent, val seen: Boolean)

    /** A verse's strongest links for its Links sheet, with their text and whether they've been shown already. */
    suspend fun linkPreviews(card: VerseFeedCard): List<LinkPreview> {
        val seen = cards.take(currentIndex + 1)
            .flatMap { (it as? VerseFeedCard)?.content?.refs.orEmpty() }
            .mapTo(HashSet()) { it.key }
        return linkSource.links(card.content.ref).take(LINKS_SHOWN).mapNotNull { link ->
            loadContent(link.ref, link.count)?.let { LinkPreview(link, it, link.ref.key in seen) }
        }
    }

    // Verse texts, link lists and word sets are looked up over and over while a thread picks its way
    // along, so they're kept for a while.
    private val verseTextCache = LruCache<String, String>(2000)
    private val linkCache = LruCache<String, List<Link>>(300)
    private val wordCache = LruCache<String, Set<String>>(1000)

    private suspend fun verseTexts(book: String, chapter: Int, first: Int, last: Int): Map<Int, String> {
        val cached = (first..last).associateWith { verseTextCache.get(VerseRef(book, chapter, it).key) }
        if (cached.values.all { it != null }) return cached.mapValues { it.value!! }
        val rows = repository.getVerseRange(book, chapter, first, last)
        return rows.associate { row ->
            val text = cleanVerseText(row.text)
            verseTextCache.put(VerseRef(book, chapter, row.number).key, text)
            row.number to text
        }
    }

    private val linkSource = object : LinkSource {
        override suspend fun links(ref: VerseRef): List<Link> =
            linkCache.get(ref.key) ?: resolveLinks(ref).also { linkCache.put(ref.key, it) }

        override suspend fun words(refs: List<VerseRef>): Set<String> {
            val key = refs.joinToString("|") { it.key }
            wordCache.get(key)?.let { return it }
            val first = refs.first()
            val text = verseTexts(first.book, first.chapter, first.verse, refs.last().verse).values.joinToString(" ")
            return RabbitHole.wordsOf(text).also { wordCache.put(key, it) }
        }
    }

    /**
     * A verse's links worth suggesting, strongest first: each to one verse, or to a short passage when
     * the link names 2 or 3 verses that together stay short. Leaves out links back to the verse itself,
     * repeats, and very long verses.
     */
    private suspend fun resolveLinks(ref: VerseRef): List<Link> {
        val out = mutableListOf<Link>()
        val seen = HashSet<String>()
        for (row in repository.getCrossReferenceRows(ref.book, ref.chapter, ref.verse)) {
            if (row.votes < MIN_SUGGESTED_VOTES) break
            val first = VerseRef(row.toBook, row.toChapter, row.toVerse)
            if (first == ref || !seen.add(first.key)) continue
            val span = row.toVerseEnd - row.toVerse + 1
            val found = verseTexts(first.book, first.chapter, first.verse, first.verse + (span - 1).coerceIn(0, MAX_PASSAGE - 1))
            val firstText = found[first.verse] ?: continue
            if (wordCount(firstText) > MAX_LINKED_VERSE_WORDS) continue
            val passage = (0 until span).map { found[first.verse + it] }
            val isPassage = span in 2..MAX_PASSAGE && passage.all { it != null } &&
                passage.sumOf { wordCount(it!!) } <= MAX_PASSAGE_WORDS
            out.add(Link(first, row.votes, if (isPassage) span else 1))
        }
        return out
    }

    private fun wordCount(text: String) = text.split(' ').count { it.isNotBlank() }

    private fun pickScene(content: VerseCardContent): String? {
        if (scenes.isEmpty()) return null
        val text = content.lines.joinToString(" ") { it.text }
        val scene = SceneMatcher.pick(scenes, text, content.ref.key, lastSceneType)
        lastSceneType = scene.type
        return scene.id
    }

    /** The card content for [count] verses from [ref], or null if any of them isn't in the database yet. */
    suspend fun loadContent(ref: VerseRef, count: Int): VerseCardContent? {
        val last = ref.verse + count - 1
        val rows = repository.getVerseRange(ref.book, ref.chapter, ref.verse - 1, last + 1).associateBy { it.number }
        val lines = (ref.verse..last).map { n ->
            val row = rows[n] ?: return null
            VerseLine(n, cleanVerseText(row.text), row.teluguText?.trim()?.takeIf { it.isNotEmpty() })
        }
        if (lines.any { it.text.isEmpty() }) return null
        fun context(n: Int) = rows[n]?.let { VerseLine(n, cleanVerseText(it.text), null) }?.takeIf { it.text.isNotEmpty() }
        return VerseCardContent(
            ref = ref,
            lines = lines,
            before = context(ref.verse - 1),
            after = context(last + 1),
            linkCount = repository.countCrossReferencesFrom(ref.book, ref.chapter, ref.verse)
        )
    }

    // ---- today's count, and verses shown recently (kept so a new visit doesn't repeat them) ----

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun readTodayCount(): Int =
        if (prefs.getString(KEY_COUNT_DATE, null) == today()) prefs.getInt(KEY_COUNT, 0) else 0

    private fun bumpTodayCount(): Int {
        val n = readTodayCount() + 1
        prefs.edit().putString(KEY_COUNT_DATE, today()).putInt(KEY_COUNT, n).apply()
        return n
    }

    private fun saveRecent() {
        val pool = discover ?: return
        prefs.edit().putString(KEY_RECENT, pool.recentKeys().joinToString("|")).apply()
    }

    // ---- scene pictures ----

    // Decoded pictures for the cards around the one on screen. Hardware bitmaps live in graphics memory,
    // not the app's heap, so a handful of full-screen paintings costs the app nothing it needs.
    private val images = LruCache<String, ImageBitmap>(5)

    /** Tests that draw the screen in software (which can't draw hardware bitmaps) switch this. */
    @VisibleForTesting
    internal var imageConfig: Bitmap.Config = Bitmap.Config.HARDWARE

    fun cachedImage(sceneId: String): ImageBitmap? = images.get(sceneId)

    suspend fun loadImage(sceneId: String): ImageBitmap? {
        images.get(sceneId)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open("verse_scroll/scenes/$sceneId.webp").use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                        inPreferredConfig = imageConfig
                    })
                }
            } catch (e: Exception) {
                null
            }
        } ?: return null
        return bitmap.asImageBitmap().also { images.put(sceneId, it) }
    }

    companion object {
        private const val PREFS_NAME = "verse_scroll"
        private const val KEY_SCENES = "painted_scenes"
        private const val KEY_MOTION = "motion"
        private const val KEY_COLOR = "double_tap_color"
        private const val KEY_TELUGU = "show_telugu"
        private const val KEY_HINT_SEEN = "hint_seen"
        private const val KEY_COUNT_DATE = "count_date"
        private const val KEY_COUNT = "count"
        private const val KEY_RECENT = "recent"

        /** Sunshine, the lightest of the twelve highlight colors. */
        const val DEFAULT_COLOR = "#F1F1B1"

        // A link naming a few verses is shown as that passage when it's this short; otherwise its first verse.
        private const val MAX_PASSAGE = 3
        private const val MAX_PASSAGE_WORDS = 90

        // Links to verses longer than this aren't suggested: too much to take in mid-scroll.
        private const val MAX_LINKED_VERSE_WORDS = 70

        private const val CARDS_AHEAD = 4
        private const val CARDS_PER_BATCH = 6
        private const val MAX_SKIPS = 8

        // Coming back after longer than this starts a fresh feed rather than the old position.
        private const val FRESH_FEED_AFTER_MS = 30 * 60_000L

        private val PILCROW = Regex("¶")
        private val SPACES = Regex("\\s+")

        /** KJV text carries paragraph marks (¶) that read as noise on a card on their own. */
        fun cleanVerseText(text: String): String = text.replace(PILCROW, "").replace(SPACES, " ").trim()
    }
}
