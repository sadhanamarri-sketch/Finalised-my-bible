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

    private fun generateMore(count: Int = CARDS_PER_BATCH) {
        if (generating?.isActive == true) return
        generating = scope.launch {
            ensureLoaded()
            repeat(count) {
                val card = nextCard() ?: return@launch
                cards.add(card)
            }
        }
    }

    private suspend fun nextCard(): FeedCard? {
        if (sinceCheckIn >= CHECK_IN_EVERY) {
            sinceCheckIn = 0
            return CheckInFeedCard(nextUid++)
        }
        val pool = discover ?: return null
        // A verse that isn't in the database yet (the Bible text still downloading) is skipped.
        repeat(MAX_SKIPS) {
            val ref = pool.next() ?: return null
            val content = loadContent(ref, 1) ?: return@repeat
            sinceCheckIn++
            waitingForBibleText = false
            return VerseFeedCard(nextUid++, content, pickScene(content))
        }
        if (cards.none { it is VerseFeedCard }) waitingForBibleText = true
        return null
    }

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
