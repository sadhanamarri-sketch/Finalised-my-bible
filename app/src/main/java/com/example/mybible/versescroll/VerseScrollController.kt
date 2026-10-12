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
import com.example.mybible.audio.ReadAloud
import com.example.mybible.audio.ReadAloudState
import com.example.mybible.audio.ReadingPlace
import com.example.mybible.audio.ReadingSession
import com.example.mybible.audio.VerseToRead
import com.example.mybible.data.BibleRepository
import com.example.mybible.model.MatchedWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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
    private val scope: CoroutineScope,
    private val readAloud: ReadAloud
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // ---- settings ----

    /** A nature photo behind each verse, or the plain theme background. */
    var backgroundPhotos by mutableStateOf(prefs.getBoolean(KEY_SCENES, true))
        private set

    /** The photos' slow drift and zoom (never when the phone asks for less motion). */
    var motion by mutableStateOf(prefs.getBoolean(KEY_MOTION, true))
        private set

    /** The color a double-tap highlights with. */
    var doubleTapColor by mutableStateOf(prefs.getString(KEY_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR)
        private set

    /** The Telugu translation under each verse. */
    var showTelugu by mutableStateOf(prefs.getBoolean(KEY_TELUGU, false))
        private set

    /** How big that Telugu is, in sp. */
    var teluguSize by mutableIntStateOf(prefs.getInt(KEY_TELUGU_SIZE, DEFAULT_TELUGU_SIZE).coerceIn(MIN_TELUGU_SIZE, MAX_TELUGU_SIZE))
        private set

    /** Whether the first-time "double-tap a verse" hint has been dismissed. */
    var hintSeen by mutableStateOf(prefs.getBoolean(KEY_HINT_SEEN, false))
        private set

    fun updateBackgroundPhotos(on: Boolean) {
        backgroundPhotos = on
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

    fun adjustTeluguSize(by: Int) {
        teluguSize = (teluguSize + by).coerceIn(MIN_TELUGU_SIZE, MAX_TELUGU_SIZE)
        prefs.edit().putInt(KEY_TELUGU_SIZE, teluguSize).apply()
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

    /** How many background photos there are (0 until the catalog has loaded). */
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
        discover?.let { pool ->
            fullCount = pool.size
            focusedCount = pool.familiarSize
        }
    }

    // ---- where the verses come from ----

    /**
     * Verse Scroll's mode: where its verses come from (see [VerseSource]). The one last chosen is kept
     * for the next visit — never a search's or a topic's, which lasts until Verse Scroll is next opened
     * from the Reader.
     */
    var source by mutableStateOf(VerseSource.decode(prefs.getString(KEY_SOURCE, null)) ?: VerseSource.Full)
        private set
    private var savedSource: VerseSource = source

    /** How many verses Full and Focused deal from, once the pool has loaded (0 until then). */
    var fullCount by mutableIntStateOf(0)
        private set
    var focusedCount by mutableIntStateOf(0)
        private set

    /** The chosen verses turned out to be none at all (no highlights yet, say). */
    var sourceEmpty by mutableStateOf(false)
        private set

    // A book's or chapter's place, or a shuffled list's round; built as the first card is dealt.
    private var deck: VerseDeck? = null
    private var endShown = false
    private var verseCounts: Map<String, List<Int>>? = null

    /** Shows [next]'s verses from the start, unless they're the ones already showing. */
    fun choose(next: VerseSource) {
        VerseSource.encode(next)?.let { encoded ->
            savedSource = next
            prefs.edit().putString(KEY_SOURCE, encoded).apply()
        }
        if (next == source && cards.isNotEmpty() && !sourceEmpty) return
        source = next
        reset()
        generateMore()
    }

    /**
     * "Scroll these" on a search's results or a topic: those verses, shuffled, what found each, and
     * the words picked to show the verses of (see VerseSource.Found).
     */
    fun showFound(
        title: String,
        from: String,
        refs: List<VerseRef>,
        matched: Map<String, List<MatchedWord>> = emptyMap(),
        only: Set<MatchedWord> = emptySet()
    ) {
        val found = VerseSource.Found(title, from, refs, matched)
        // Picked words that found none of these, or all of the words: the same as none.
        val picked = only.filterTo(HashSet()) { word -> found.words.any { it.first == word } }
        choose(if (picked.size in 1 until found.words.size) found.copy(only = picked) else found)
    }

    /** Opened from the Reader: back to the chosen mode, if a search's or a topic's verses were showing. */
    fun useSavedSource() {
        if (source is VerseSource.Found) choose(savedSource)
    }

    /** "Start again" at the end of a book or chapter. */
    fun startAgain() {
        prefs.edit().remove(KEY_ORDER_SOURCE).remove(KEY_ORDER_VERSE).apply()
        reset()
        generateMore()
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
        // A book or chapter keeps its place however long it's been (and resumes there even after the app
        // was closed, see buildDeck); the others start fresh after a while away.
        if ((stale && !source.inOrder) || waitingForBibleText || sourceEmpty) reset()
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
        sourceEmpty = false
        deck = null
        endShown = false
        // The card being read aloud is gone; with the speaker on, the new feed's first card is read instead.
        reading?.let { current ->
            reading = null
            if (readAloud.session === current) readAloud.stop()
        }
    }

    /** A card settled on screen. */
    fun onCardShown(index: Int) {
        currentIndex = index
        val card = cards.getOrNull(index)
        if (card is VerseFeedCard && viewedUids.add(card.uid)) {
            versesToday = bumpTodayCount()
            if (viewedUids.size % 10 == 0) saveRecent()
        }
        // A book or chapter read in order: where to pick up next time (its own verses, not a Rabbit hole's
        // links), or, at its end, from the start.
        if (source.inOrder) {
            when {
                card is VerseFeedCard && card.from == null -> prefs.edit()
                    .putString(KEY_ORDER_SOURCE, VerseSource.encode(source))
                    .putString(KEY_ORDER_VERSE, "${card.content.ref.chapter}:${card.content.ref.verse}")
                    .apply()
                card is EndFeedCard -> prefs.edit().remove(KEY_ORDER_SOURCE).remove(KEY_ORDER_VERSE).apply()
            }
        }
        if (cards.size - index < CARDS_AHEAD) generateMore()
        // The speaker on: the card that came up is read aloud (unless it's the one already being read,
        // moved to by Continue reading).
        if (speakerOn && card != null && reading?.uid != card.uid) readCard(index)
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
            // Nothing left anywhere on this thread: a fresh one from the next of the mode's own verses.
            return startNewThread()
        }
        return sourceCard()
    }

    // The next of the mode's own verses (Discover's, outside a Rabbit hole).
    private suspend fun sourceCard(newThread: Boolean = false): FeedCard? {
        // A verse that isn't in the database yet (the Bible text still downloading) is skipped.
        repeat(MAX_SKIPS) {
            val ref = nextRef() ?: return endOfSource()
            val content = loadContent(ref, 1) ?: return@repeat
            sinceCheckIn++
            waitingForBibleText = false
            return VerseFeedCard(nextUid++, content, pickScene(content), newThread = newThread)
        }
        if (cards.none { it is VerseFeedCard }) waitingForBibleText = true
        return null
    }

    private suspend fun nextRef(): VerseRef? = when (val s = source) {
        VerseSource.Full -> discover?.next()
        VerseSource.Focused -> discover?.next(familiarOnly = true)
        else -> (deck ?: buildDeck(s).also { deck = it }).next()
    }

    // No more verses: a book or chapter read to its end gets one last card; a mode with no verses at all
    // says so (or, for a book, waits for the Bible text).
    private fun endOfSource(): FeedCard? {
        val current = deck ?: return null
        if (current.size == 0) {
            if (source.inOrder) {
                waitingForBibleText = cards.none { it is VerseFeedCard }
                deck = null
            } else {
                sourceEmpty = true
            }
            return null
        }
        if (source.inOrder && !endShown) {
            endShown = true
            return EndFeedCard(nextUid++, source.label)
        }
        return null
    }

    private suspend fun buildDeck(s: VerseSource): VerseDeck = when (s) {
        is VerseSource.Book, is VerseSource.Chapter -> {
            val book = if (s is VerseSource.Book) s.book else (s as VerseSource.Chapter).book
            val chapters = chaptersOf(book).filter { s !is VerseSource.Chapter || it.first == s.chapter }
            // Back where it was left, when it's the same book or chapter.
            val resume = prefs.getString(KEY_ORDER_VERSE, null)
                ?.takeIf { prefs.getString(KEY_ORDER_SOURCE, null) == VerseSource.encode(s) }
                ?.split(':')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
            ReadingOrderDeck(book, chapters, resume?.let { it[0] to it[1] })
        }
        VerseSource.Highlighted -> ShuffledDeck(repository.allHighlights.first().map { VerseRef(it.book, it.chapter, it.verse) })
        VerseSource.Studied -> ShuffledDeck(repository.allCompletedVerses.first().map { VerseRef(it.book, it.chapter, it.verse) })
        is VerseSource.Found -> ShuffledDeck(s.shown)
        VerseSource.Full, VerseSource.Focused -> ShuffledDeck(emptyList())
    }

    // Each chapter of [book] with its verse count; empty until the Bible text has been imported.
    private suspend fun chaptersOf(book: String): List<Pair<Int, Int>> {
        val counts = verseCounts ?: repository.getChapterVerseCounts().takeIf { it.isNotEmpty() }?.also { verseCounts = it }
        return counts?.get(book).orEmpty().mapIndexed { i, n -> (i + 1) to n }.filter { it.second > 0 }
    }

    private suspend fun startNewThread(): FeedCard? {
        val next = sourceCard(newThread = true)
        val card = next as? VerseFeedCard ?: return next
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
        // A book or chapter read in order carries on from the last of its own verses still here, not
        // after the ones just dropped.
        (deck as? ReadingOrderDeck)?.let { inOrder ->
            val last = cards.asReversed().firstNotNullOfOrNull { card ->
                (card as? VerseFeedCard)?.takeIf { it.from == null }?.content?.ref
            }
            inOrder.continueAfter(last?.chapter, last?.verse)
            endShown = cards.any { it is EndFeedCard }
        }
    }

    // ---- reading aloud ----

    /** The speaker button: each card is read aloud as it comes up (and with Continue reading, it moves on). */
    var speakerOn by mutableStateOf(false)
        private set

    // The cards' reading going on (or just finished, the speaker still on), if any.
    private var reading: CardReading? = null

    fun toggleSpeaker() {
        if (speakerOn) {
            stopReading()
            return
        }
        speakerOn = true
        // Paused on the card still on screen: on from where it stopped. Otherwise the card on screen.
        val paused = reading?.takeIf {
            readAloud.session === it && readAloud.state.value.status == ReadAloudState.Status.PAUSED &&
                it.uid == cards.getOrNull(currentIndex)?.uid
        }
        if (paused != null) readAloud.resume() else readCard(currentIndex)
    }

    /** The speaker off: stops reading the cards aloud. */
    fun stopReading() {
        speakerOn = false
        val current = reading ?: return
        reading = null
        if (readAloud.session === current) readAloud.stop()
    }

    // Reads the card at [index] aloud; one that isn't a verse (a check-in, the end) stops the one before.
    private fun readCard(index: Int) {
        val previous = reading
        reading = null
        val card = cards.getOrNull(index) as? VerseFeedCard
        if (card == null) {
            if (previous != null && readAloud.session === previous) readAloud.stop()
            return
        }
        val next = CardReading(index, card.uid)
        reading = next
        readAloud.start(next)
    }

    /**
     * Cards read aloud: a card's verses one at a time, then, with Continue reading, on to the next verse
     * card (past a check-in) — moving the screen along with it, or with the screen off, just the feed,
     * which the screen catches up with when it's back. A headset's next and previous go a card on or
     * back, the same way.
     */
    private inner class CardReading(var index: Int, var uid: Long) : ReadingSession {
        override val place = ReadingPlace.VERSE_SCROLL
        override val title = "Verse Scroll \u00b7 ${source.label}"

        // Which of the card's verses (a passage has several) is being read.
        private var line = -1

        override suspend fun next(after: VerseToRead?): VerseToRead? {
            val card = cards.getOrNull(index) as? VerseFeedCard
            if (card != null && card.uid == uid && line + 1 < card.content.lines.size) {
                line++
                return toRead(card, line)
            }
            if (after == null || !readAloud.prefs.continueReading) return null
            return moveTo(nextVerseCard(index) ?: return null)
        }

        // The next card, whatever's left of this one and with Continue reading on or off.
        override suspend fun skip(current: VerseToRead): VerseToRead? {
            return moveTo(nextVerseCard(index) ?: return null)
        }

        // The verse card before this one, past check-ins; none before the feed's first.
        override suspend fun back(current: VerseToRead): VerseToRead? {
            val before = (index - 1 downTo 0).firstOrNull { cards.getOrNull(it) is VerseFeedCard } ?: return null
            return moveTo(before)
        }

        // On to the verse card at [to], from its first verse, the screen going with it.
        private suspend fun moveTo(to: Int): VerseToRead {
            val card = cards[to] as VerseFeedCard
            index = to
            uid = card.uid
            line = 0
            onCardShown(to)
            scrollRequest = ScrollRequest(to, ++scrollIds)
            return toRead(card, 0)
        }

        override fun onStopped(finished: Boolean) {
            // Read to the end of its card (Continue reading off): the speaker stays on for the next one.
            if (reading !== this || finished) return
            reading = null
            speakerOn = false
        }

        override fun onPaused() {
            if (reading === this) speakerOn = false
        }

        override fun onResumed() {
            if (reading === this) speakerOn = true
        }
    }

    private suspend fun toRead(card: VerseFeedCard, line: Int): VerseToRead {
        val ref = card.content.ref
        val verse = card.content.lines[line]
        val telugu = verse.telugu ?: repository.getTeluguChapterVerses(ref.book, ref.chapter)[verse.number]
        return VerseToRead(ref.book, ref.chapter, verse.number, verse.text, telugu)
    }

    // The next verse card after [from], past check-ins, dealing more if the feed hasn't got that far; null
    // at the end of a book or chapter (whose end card the screen moves to) or when no more come.
    private suspend fun nextVerseCard(from: Int): Int? {
        var i = from + 1
        while (true) {
            when (val card = cardAt(i) ?: return null) {
                is VerseFeedCard -> return i
                is EndFeedCard -> {
                    scrollRequest = ScrollRequest(i, ++scrollIds)
                    return null
                }
                else -> i++
            }
        }
    }

    // The card at [index], dealing more cards first if need be; null when no more come.
    private suspend fun cardAt(index: Int): FeedCard? {
        while (index >= cards.size) {
            val before = cards.size
            generating?.join()
            if (index < cards.size) break
            generateMore()
            generating?.join()
            if (cards.size == before) return null
        }
        return cards[index]
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
     * and repeats.
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
            if (found[first.verse] == null) continue
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
        val rows = repository.getVerseRange(ref.book, ref.chapter, ref.verse, last).associateBy { it.number }
        val lines = (ref.verse..last).map { n ->
            val row = rows[n] ?: return null
            VerseLine(n, cleanVerseText(row.text), row.teluguText?.trim()?.takeIf { it.isNotEmpty() })
        }
        if (lines.any { it.text.isEmpty() }) return null
        return VerseCardContent(
            ref = ref,
            lines = lines,
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
    // not the app's heap, so a handful of full-screen photos costs the app nothing it needs.
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
        // Named for the painted scenes the photos replaced; kept so the setting carries over.
        private const val KEY_SCENES = "painted_scenes"
        private const val KEY_MOTION = "motion"
        private const val KEY_COLOR = "double_tap_color"
        private const val KEY_TELUGU = "show_telugu"
        private const val KEY_TELUGU_SIZE = "telugu_size"
        private const val KEY_HINT_SEEN = "hint_seen"
        private const val KEY_COUNT_DATE = "count_date"
        private const val KEY_COUNT = "count"
        private const val KEY_RECENT = "recent"
        private const val KEY_SOURCE = "source"
        private const val KEY_ORDER_SOURCE = "order_source"
        private const val KEY_ORDER_VERSE = "order_verse"

        /** Sunshine, the lightest of the twelve highlight colors. */
        const val DEFAULT_COLOR = "#F1F1B1"

        // The Telugu under a verse: the Reader's own size to start with, against the verse's 26.
        const val DEFAULT_TELUGU_SIZE = 18
        const val MIN_TELUGU_SIZE = 14
        const val MAX_TELUGU_SIZE = 24

        // A link naming a few verses is shown as that passage when it's this short; otherwise its first verse.
        private const val MAX_PASSAGE = 3
        private const val MAX_PASSAGE_WORDS = 90

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
