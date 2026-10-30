package com.example.mybible.versescroll

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.mybible.audio.ReadAloud
import com.example.mybible.audio.ReadAloudState
import com.example.mybible.audio.ReadLanguage
import com.example.mybible.audio.ReadingPlace
import com.example.mybible.audio.ReadingSession
import com.example.mybible.audio.VerseToRead
import com.example.mybible.data.BibleRepository
import com.example.mybible.model.MatchedWord
import com.example.mybible.pictures.PictureShelf
import com.example.mybible.pictures.PictureVeil
import com.example.mybible.pictures.Pictures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything behind the Verse Scroll screen that should outlive it: the feed itself (so a trip to the
 * Reader via "Read" and back lands on the same card), its settings, today's verse count, the nature
 * photos, and your own pictures (see [pictures]), each shown first, alone, the first time its card comes
 * up. Owned by MainViewModel; the screen only reads this state and calls back into it.
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

    /** Your own pictures (from the pictures folder) on the cards they're for, in place of the nature photos. */
    var showPictures by mutableStateOf(prefs.getBoolean(KEY_PICTURES, true))
        private set

    /** A card with its own picture shows the picture alone first, then its verse comes in. */
    var pictureFirst by mutableStateOf(prefs.getBoolean(KEY_PICTURE_FIRST, true))
        private set

    /** The Telugu translation under each verse. */
    var showTelugu by mutableStateOf(prefs.getBoolean(KEY_TELUGU, false))
        private set

    /** How big that Telugu is, in sp. */
    var teluguSize by mutableIntStateOf(prefs.getInt(KEY_TELUGU_SIZE, DEFAULT_TELUGU_SIZE).coerceIn(MIN_TELUGU_SIZE, MAX_TELUGU_SIZE))
        private set

    /** Whether the first-time "double-tap a verse to choose a color" hint has been dismissed. */
    var hintSeen by mutableStateOf(prefs.getBoolean(KEY_HINT_SEEN, false))
        private set

    /** The shape a verse was last shared in as a picture, offered first the next time. */
    var shareShape by mutableStateOf(ShareShape.named(prefs.getString(KEY_SHARE_SHAPE, null)))
        private set

    fun chooseShareShape(shape: ShareShape) {
        shareShape = shape
        prefs.edit().putString(KEY_SHARE_SHAPE, shape.name).apply()
    }

    fun updateBackgroundPhotos(on: Boolean) {
        backgroundPhotos = on
        prefs.edit().putBoolean(KEY_SCENES, on).apply()
    }

    fun updateMotion(on: Boolean) {
        motion = on
        prefs.edit().putBoolean(KEY_MOTION, on).apply()
    }

    fun updateShowPictures(on: Boolean) {
        showPictures = on
        prefs.edit().putBoolean(KEY_PICTURES, on).apply()
    }

    fun updatePictureFirst(on: Boolean) {
        pictureFirst = on
        prefs.edit().putBoolean(KEY_PICTURE_FIRST, on).apply()
        // Off: a card waiting to show its verse shows it now.
        if (!on) revealing?.let { uid -> (cards.firstOrNull { it.uid == uid } as? VerseFeedCard)?.let(::showVerse) }
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
        // What was in the pictures folder last time, so the first cards get theirs.
        pictures.ready()
        takeShelf()
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

    /** A card the screen should move to; it calls [consumeScrollRequest] once it has, or a swipe took over. */
    var scrollRequest by mutableStateOf<ScrollRequest?>(null)
        private set

    // The last scroll request the screen has moved to, for a reading waiting to read the card it slid to.
    private val settled = MutableStateFlow(0L)

    fun consumeScrollRequest(request: ScrollRequest) {
        if (request.id > settled.value) settled.value = request.id
        if (scrollRequest?.id == request.id) scrollRequest = null
    }

    /** Whether Verse Scroll is on screen (started and not stopped), to slide to the cards a reading moves to. */
    var screenShowing by mutableStateOf(false)

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
        // Pictures put in the folder since show up; a place's other pictures take their turn.
        pictureTurn = prefs.getInt(KEY_PICTURE_TURN, 0) + 1
        prefs.edit().putInt(KEY_PICTURE_TURN, pictureTurn).apply()
        pictures.lookAgain()
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
        lastPicture = null
        endReveal()
        revealed.clear()
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
        // A picture showing alone, swiped past before its verse came: shown first all the same.
        if (revealing != null && revealing != card?.uid) endReveal()
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
                    LinkedFrom(step.from, labelOf(step.fromRefs), step.nth, step.of),
                    picture = pickPicture(content)
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
            return VerseFeedCard(nextUid++, content, pickScene(content), newThread = newThread, picture = pickPicture(content))
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

    /**
     * The cards being read aloud (a tap on one plays it): each card is read as it comes up (and with Continue
     * reading, it moves on). Off while paused.
     */
    var speakerOn by mutableStateOf(false)
        private set

    // The cards' reading going on (or just finished, the speaker still on), if any.
    private var reading: CardReading? = null

    /**
     * Read in Telugu or both, the Telugu shows under each verse, as the Reader's అ comes on for its
     * readings: as the reading starts, or its language is changed while it reads.
     */
    fun showTeluguForReading() {
        if (readAloud.prefs.language != ReadLanguage.ENGLISH && !showTelugu) updateShowTelugu(true)
    }

    /**
     * A tap on a card, away from its words: plays or pauses the cards' reading, or, with nothing being
     * read (or what's paused another card's), starts reading the card on screen.
     */
    fun playOrPause() {
        val current = reading?.takeIf { readAloud.session === it }
        val status = readAloud.state.value.status
        when {
            current != null && status == ReadAloudState.Status.PLAYING -> readAloud.pause()
            current != null && status == ReadAloudState.Status.PAUSED && current.uid == cards.getOrNull(currentIndex)?.uid -> {
                showTeluguForReading()
                readAloud.resume()
            }
            cards.getOrNull(currentIndex) is VerseFeedCard -> {
                speakerOn = true
                showTeluguForReading()
                readCard(currentIndex)
            }
        }
    }

    /** Whether [card] is the one being read aloud (playing or paused): its progress bar shows. */
    fun reads(card: VerseFeedCard): Boolean = reading?.let { readAloud.session === it && it.uid == card.uid } == true

    /** Off to the Reader for a look ("Read"): the cards' reading pauses where it is, to be played on back here. */
    fun pauseReading() {
        val current = reading ?: return
        if (readAloud.session === current && readAloud.state.value.status == ReadAloudState.Status.PLAYING) readAloud.pause()
    }

    /** Off to another tab: stops reading the cards aloud. */
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

        // The pause between cards, while it lasts: completed to go on at once (a headset's next).
        private var pausing: CompletableDeferred<Unit>? = null

        override val waiting: Boolean get() = pausing != null

        override suspend fun next(after: VerseToRead?): VerseToRead? {
            val card = cards.getOrNull(index) as? VerseFeedCard
            if (card != null && card.uid == uid && line + 1 < card.content.lines.size) {
                // The card's first verse, once its picture has been shown alone.
                if (line < 0) awaitReveal(card)
                line++
                return toRead(card, line)
            }
            if (after == null || !readAloud.prefs.continueReading) return null
            val to = nextVerseCard(index) ?: return null
            // A picture shown first on the next card counts toward the pause, so it isn't waited for twice.
            val next = cards.getOrNull(to) as? VerseFeedCard
            pauseBetweenCards(shorter = if (next != null && screenShowing && holdsVerse(next)) REVEAL_MS else 0)
            return moveTo(to, gentle = true)
        }

        // Going on by itself: a breath before the next card (Off, or a few seconds; [shorter] by so many
        // milliseconds), cut short by a headset's next. Paused meanwhile, it stays on this card until it's
        // played again.
        private suspend fun pauseBetweenCards(shorter: Long = 0) {
            val wait = CompletableDeferred<Unit>().also { pausing = it }
            try {
                val ms = readAloud.prefs.pauseBetweenCards * 1000L - shorter
                if (ms > 0) withTimeoutOrNull(ms) { wait.await() }
                if (!wait.isCompleted && readAloud.state.value.status == ReadAloudState.Status.PAUSED) {
                    merge(flow { emit(wait.await()) }, readAloud.state.filter { it.status != ReadAloudState.Status.PAUSED }.map { }).first()
                }
            } finally {
                pausing = null
            }
        }

        override fun hurry() {
            pausing?.complete(Unit)
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

        // One of the card's verses (a passage's), for the progress bar's move to it.
        override suspend fun verse(book: String, chapter: Int, verse: Int): VerseToRead? {
            val card = cards.getOrNull(index) as? VerseFeedCard ?: return null
            if (card.uid != uid || card.content.ref.book != book || card.content.ref.chapter != chapter) return null
            val at = card.content.lines.indexOfFirst { it.number == verse }.takeIf { it >= 0 } ?: return null
            line = at
            return toRead(card, at)
        }

        // The rest of the card's verses (a passage's), which a recording plays on through.
        override suspend fun following(verse: VerseToRead): List<VerseToRead> {
            val card = cards.getOrNull(index) as? VerseFeedCard ?: return emptyList()
            if (card.uid != uid) return emptyList()
            return (line + 1 until card.content.lines.size).map { toRead(card, it) }
        }

        // On to the verse card at [to], from its first verse, the screen going with it. [gentle]: going on
        // by itself, it slides there slowly, and the card is read once it has settled.
        private suspend fun moveTo(to: Int, gentle: Boolean = false): VerseToRead {
            val card = cards[to] as VerseFeedCard
            index = to
            uid = card.uid
            line = 0
            onCardShown(to)
            val request = ScrollRequest(to, ++scrollIds, gentle)
            scrollRequest = request
            if (gentle && screenShowing) withTimeoutOrNull(SETTLE_LIMIT_MS) { settled.first { it >= request.id } }
            awaitReveal(card)
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
                            LinkedFrom(step.from, labelOf(step.fromRefs), step.nth, step.of),
                            picture = pickPicture(content)
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
     * the link names 2 or 3 verses that together stay short; a quotation however weak, from the verses
     * holding it (of a longer passage). Leaves out links back to the verse itself, and repeats.
     */
    private suspend fun resolveLinks(ref: VerseRef): List<Link> {
        val out = mutableListOf<Link>()
        val seen = HashSet<String>()
        for (row in repository.getCrossReferenceRows(ref.book, ref.chapter, ref.verse)) {
            val quoted = repository.quotations.quotedPart(ref.book, ref.chapter, ref.verse, row.toBook, row.toChapter, row.toVerse, row.toVerseEnd)
            val quote = if (quoted != null) repository.quotations.sideOf(ref.book, ref.chapter, ref.verse) else null
            if (row.votes < MIN_SUGGESTED_VOTES && quote == null) continue
            val first = VerseRef(row.toBook, row.toChapter, quoted?.first ?: row.toVerse)
            if (first == ref || !seen.add(first.key)) continue
            val span = (quoted?.last ?: row.toVerseEnd) - first.verse + 1
            val found = verseTexts(first.book, first.chapter, first.verse, first.verse + (span - 1).coerceIn(0, MAX_PASSAGE - 1))
            if (found[first.verse] == null) continue
            val passage = (0 until span).map { found[first.verse + it] }
            val isPassage = span in 2..MAX_PASSAGE && passage.all { it != null } &&
                passage.sumOf { wordCount(it!!) } <= MAX_PASSAGE_WORDS
            out.add(Link(first, row.votes, if (isPassage) span else 1, quote))
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

    // ---- your own pictures ----

    /** The pictures folder: a picture for each scene, made in ChatGPT and added on the Pictures page. */
    val pictures: Pictures by lazy {
        Pictures(context, scope).also { folder ->
            scope.launch { folder.shelf.collect { takeShelf() } }
            scope.launch { folder.veils.collect { pictureVeils = it } }
        }
    }

    /** The pictures found in the folder. */
    var shelf by mutableStateOf(PictureShelf.Empty)
        private set
    private var pictureVeils by mutableStateOf(emptyMap<String, PictureVeil.Veil>())

    // The picture on the card dealt last, so a book's picture isn't on two cards running; and which of a
    // place's pictures has its turn this visit.
    private var lastPicture: String? = null
    private var pictureTurn = prefs.getInt(KEY_PICTURE_TURN, 0)

    /**
     * What's behind [card]: its own picture (dimmed as it was measured to need), else a nature photo,
     * else nothing (the plain page).
     */
    fun sceneFor(card: VerseFeedCard): SceneSpec? {
        val picture = card.picture?.takeIf { showPictures }?.let { shelf[it] }
        if (picture != null) {
            val veil = pictureVeils[picture.uri] ?: UNMEASURED
            // Changed (written again under the same name), it's another scene, read afresh.
            return SceneSpec("$PICTURE${picture.modified}|${picture.uri}", veil.dark, veil.light)
        }
        return if (backgroundPhotos) scene(card.sceneId) else null
    }

    // A card's own picture: the one for its verse that fits best (its scene's), one of a place's
    // several in turn; a book's not on two cards running; one for any card ("Any…") now and then.
    private fun pickPicture(content: VerseCardContent): String? {
        val ref = content.ref
        val fit = shelf.forVerse(ref.book, ref.chapter, ref.verse)
        val picture = when {
            fit.isNotEmpty() -> {
                val name = fit.first().name!!
                fit[Math.floorMod(pictureTurn + fnv1a(name.place), fit.size)].takeUnless { name.rank == 2 && it.uri == lastPicture }
            }
            shelf.general.isNotEmpty() && Math.floorMod(fnv1a(ref.key), GENERAL_EVERY) == 0 ->
                shelf.general[Math.floorMod(fnv1a(ref.key) / GENERAL_EVERY, shelf.general.size)].takeUnless { it.uri == lastPicture }
            else -> null
        }
        lastPicture = picture?.uri
        return picture?.uri
    }

    // The folder's pictures as they are now, when they've changed (looked over, one added or taken away):
    // the card on screen and those after it get theirs. The one on screen already shows its verse, so its
    // picture comes without being shown first.
    private fun takeShelf() {
        val now = pictures.shelf.value
        if (now === shelf) return
        shelf = now
        pictureVeils = pictures.veils.value
        refreshPictures()
    }

    private fun refreshPictures() {
        if (cards.isEmpty()) return
        lastPicture = (cards.getOrNull(currentIndex - 1) as? VerseFeedCard)?.picture
        for (i in currentIndex until cards.size) {
            val card = cards[i] as? VerseFeedCard ?: continue
            val picture = pickPicture(card.content)
            if (picture == card.picture) continue
            if (i == currentIndex && picture != null && revealing != card.uid) revealed[picture] = true
            cards[i] = card.copy(picture = picture)
        }
    }

    // ---- a picture first, then its verse ----

    // The pictures shown first already (by uri): each is, once a visit.
    private val revealed = mutableStateMapOf<String, Boolean>()

    /** The card whose picture is showing alone just now, its verse to come. */
    var revealing by mutableStateOf<Long?>(null)
        private set
    private var revealJob: Job? = null

    // Goes up as each picture's time alone ends, for a reading waiting to read its verse.
    private val revealsEnded = MutableStateFlow(0)

    /** Whether [card]'s verse waits for its picture to be shown alone first (see [startReveal]). */
    fun holdsVerse(card: VerseFeedCard): Boolean {
        val picture = card.picture ?: return false
        return showPictures && pictureFirst && shelf.has(picture) && revealed[picture] != true
    }

    /** [card] has come to rest on screen, its picture alone: its verse comes in a few seconds, or at a tap. */
    fun startReveal(card: VerseFeedCard) {
        if (!holdsVerse(card) || revealing == card.uid) return
        // Another card's, swiped past.
        endReveal()
        revealing = card.uid
        revealJob = scope.launch {
            delay(REVEAL_MS)
            showVerse(card)
        }
    }

    /** [card]'s verse now (a tap on its picture, or its time alone up). */
    fun showVerse(card: VerseFeedCard) {
        card.picture?.let { revealed[it] = true }
        if (revealing == card.uid) endReveal()
        revealsEnded.value++
    }

    // The picture showing alone, if any, is done with (shown first, whether or not its verse came).
    private fun endReveal() {
        val uid = revealing ?: return
        (cards.firstOrNull { it.uid == uid } as? VerseFeedCard)?.picture?.let { revealed[it] = true }
        revealing = null
        val job = revealJob
        revealJob = null
        job?.cancel()
        revealsEnded.value++
    }

    // A card showing its picture first: its verse is read once it's there. With the screen off, nothing
    // is shown first; the verse is read straight away.
    private suspend fun awaitReveal(card: VerseFeedCard) {
        if (!holdsVerse(card)) return
        if (!screenShowing) {
            card.picture?.let { revealed[it] = true }
            return
        }
        withTimeoutOrNull(REVEAL_MS + REVEAL_SLACK_MS) { revealsEnded.first { !holdsVerse(card) } }
        // It never came to rest on screen (another card was swiped to): its verse is read all the same.
        if (holdsVerse(card)) showVerse(card)
    }

    // ---- scene pictures ----

    // Decoded pictures for the cards around the one on screen. Hardware bitmaps live in graphics memory,
    // not the app's heap, so a handful of full-screen photos costs the app nothing it needs.
    private val images = LruCache<String, ImageBitmap>(5)

    /** Tests that draw the screen in software (which can't draw hardware bitmaps) switch this. */
    @VisibleForTesting
    internal var imageConfig: Bitmap.Config = Bitmap.Config.HARDWARE

    fun cachedImage(sceneId: String): ImageBitmap? = images.get(sceneId)

    /** A scene's picture: a nature photo, or one of your own (see [sceneFor]). */
    suspend fun loadImage(sceneId: String): ImageBitmap? {
        images.get(sceneId)?.let { return it }
        val bitmap = decode(sceneId, imageConfig, own = { uri -> pictures.image(uri, imageConfig) }) ?: return null
        return bitmap.asImageBitmap().also { images.put(sceneId, it) }
    }

    /**
     * A scene's picture to draw a shared picture over (backlog 53): in the app's own memory, which a
     * picture can be drawn from (the cards' are the screen's), and one of your own as big as a shared one.
     */
    suspend fun sharePhoto(sceneId: String): Bitmap? =
        decode(sceneId, Bitmap.Config.ARGB_8888, own = { uri -> pictures.image(uri, Bitmap.Config.ARGB_8888, SHARE_PICTURE_HEIGHT) })

    private suspend fun decode(sceneId: String, config: Bitmap.Config, own: suspend (String) -> Bitmap?): Bitmap? =
        if (sceneId.startsWith(PICTURE)) {
            own(sceneId.removePrefix(PICTURE).substringAfter('|'))
        } else {
            withContext(Dispatchers.IO) {
                try {
                    context.assets.open("verse_scroll/scenes/$sceneId.webp").use { input ->
                        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                            inPreferredConfig = config
                        })
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

    companion object {
        private const val PREFS_NAME = "verse_scroll"
        // Named for the painted scenes the photos replaced; kept so the setting carries over.
        private const val KEY_SCENES = "painted_scenes"
        private const val KEY_MOTION = "motion"
        private const val KEY_TELUGU = "show_telugu"
        private const val KEY_TELUGU_SIZE = "telugu_size"
        private const val KEY_HINT_SEEN = "hint_seen"
        private const val KEY_COUNT_DATE = "count_date"
        private const val KEY_COUNT = "count"
        private const val KEY_RECENT = "recent"
        private const val KEY_SOURCE = "source"
        private const val KEY_ORDER_SOURCE = "order_source"
        private const val KEY_ORDER_VERSE = "order_verse"
        private const val KEY_PICTURES = "pictures"
        private const val KEY_PICTURE_FIRST = "picture_first"
        private const val KEY_PICTURE_TURN = "picture_turn"
        private const val KEY_SHARE_SHAPE = "share_shape"

        // One of your own pictures, read for a shared picture: at least this tall, for its 1920 pixels.
        private const val SHARE_PICTURE_HEIGHT = 2000

        // A scene id for one of your own pictures: this, then when it was last changed, "|" and its uri.
        private const val PICTURE = "picture:"

        // How a picture not yet measured is dimmed meanwhile: as much as the darkest-needing photos.
        private val UNMEASURED = PictureVeil.Veil(0.55f, 0.65f)

        // A picture for any card ("Any sunrise.jpg") shows on about one card in this many without its own.
        private const val GENERAL_EVERY = 6

        /** How long a card's own picture shows alone before its verse comes in. */
        const val REVEAL_MS = 3_000L

        // How much longer a reading waits for a picture's time alone to end before reading anyway.
        private const val REVEAL_SLACK_MS = 2_000L

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

        // How long a reading waits for the slide to the next card to settle before reading it anyway.
        private const val SETTLE_LIMIT_MS = 2_000L

        private val PILCROW = Regex("¶")
        private val SPACES = Regex("\\s+")

        /** KJV text carries paragraph marks (¶) that read as noise on a card on their own. */
        fun cleanVerseText(text: String): String = text.replace(PILCROW, "").replace(SPACES, " ").trim()
    }
}
