package com.example.mybible.data

import android.content.Context
import com.example.mybible.data.local.AppDatabase
import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.VerseEntity
import com.example.mybible.model.*
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BOOK_CHAPTER_COUNTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Empirical WebView-boost compensation for the default text sizes — see
// the comment above getSavedFontSize()/etc. Capacitor's declared defaults
// are 19 / 16 / 15 (px); these are a first +15%-ish guess, easy to retune
// as one place instead of hunting through the getters.
private const val DEFAULT_FONT_SIZE = 22        // Capacitor: 19px
private const val DEFAULT_TELUGU_FONT_SIZE = 18 // Capacitor: 16px
private const val DEFAULT_GREEK_FONT_SIZE = 17  // Capacitor: 15px

// Capacitor's fixed line-height (1.85) for both English and Telugu text —
// now the *default* for each independently-adjustable slider rather than a
// hardcoded constant, so existing reading views don't shift until the user
// actually moves a slider.
private const val DEFAULT_LINE_HEIGHT_RATIO = 1.85f

// Archaic KJV verb contractions that don't follow any regular suffix
// pattern (see BibleRepository.lookupWebsterStem) — common enough in the
// text to special-case directly against their modern base form(s), tried
// in order. Not exhaustive, just the handful frequent enough to be worth
// it: "saith"/"doth"/"hath" etc. appear throughout narrative KJV prose.
private val IRREGULAR_ARCHAIC_FORMS: Map<String, List<String>> = mapOf(
    "saith" to listOf("say"),
    "sayest" to listOf("say"),
    "hath" to listOf("have"),
    "hast" to listOf("have"),
    "doth" to listOf("do"),
    "doest" to listOf("do"),
    "dost" to listOf("do"),
    "art" to listOf("are", "be"),
    "wilt" to listOf("will"),
    "shalt" to listOf("shall"),
    "wast" to listOf("was", "be"),
    "wert" to listOf("were", "be")
)

/** Outcome of [BibleRepository.getLexiconEntry] — see that function's doc. */
sealed class LexiconLookupResult {
    data class Found(val entry: LexiconEntry) : LexiconLookupResult()
    object NoStrongsNumber : LexiconLookupResult()
    object NotFound : LexiconLookupResult()
    object NetworkError : LexiconLookupResult()
}

class BibleRepository(private val context: Context) {


    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val bibleDao: BibleDao = AppDatabase.getInstance(context).bibleDao()

    /** Runs the one-time KJV download + Telugu asset import; see [BibleDataInitializer]. */
    val dataInitializer = BibleDataInitializer(context, bibleDao)

    // Memory cache for fetched English chapter verses
    private val kjvCache = mutableMapOf<String, List<Verse>>()
    // Memory cache for Telugu chapter verses
    private val teluguCache = mutableMapOf<String, Map<Int, String>>()

    // Preferences and Persistence
    private val prefs = context.getSharedPreferences("my_bible_prefs", Context.MODE_PRIVATE)

    fun getSavedTheme(): ThemeMode {
        val themeStr = prefs.getString("theme_mode", ThemeMode.PAPER.name) ?: ThemeMode.PAPER.name
        return try { ThemeMode.valueOf(themeStr) } catch (e: Exception) { ThemeMode.PAPER }
    }

    fun saveTheme(theme: ThemeMode) {
        prefs.edit().putString("theme_mode", theme.name).apply()
    }

    // English reading font — was previously never persisted at all, so it
    // silently reset to "Serif" on every app restart regardless of what was
    // picked in Settings. Default is "georgia" to match Capacitor's
    // DEFAULT_FONT = 'georgia' (see ui/theme/AppFonts.kt for what "georgia"
    // actually resolves to on Android).
    fun getSavedEnglishFont(): String = prefs.getString("english_font", "georgia") ?: "georgia"

    fun saveEnglishFont(fontId: String) {
        prefs.edit().putString("english_font", fontId).apply()
    }

    // Per-language text sizes — same three independent sliders as the
    // Capacitor app (--reading-size / --telugu-size / --greek-size), same
    // min/max ranges (unchanged, so existing user customization still
    // works the same way), instead of Telugu being derived from the
    // English size and Greek being a fixed, non-adjustable value.
    //
    // DEFAULTS ONLY (this doesn't touch anyone who's already picked a size —
    // prefs.getInt only falls back to these when nothing's saved yet):
    // Capacitor's declared 19/16/15px render visibly larger on Android than
    // the same sp values in Compose, mainly because Android WebView applies
    // its own font-boosting heuristic on top of the declared CSS px (see
    // chat). There's no exact formula for that boost, so this is a first
    // empirical pass (~+15%) to get the *default* look closer — tune the
    // three EMPIRICAL_*_BUMP constants below based on side-by-side feedback
    // rather than re-deriving them.
    fun getSavedFontSize(): Int = prefs.getInt("font_size", DEFAULT_FONT_SIZE).coerceIn(15, 26)
    fun saveFontSize(size: Int) = prefs.edit().putInt("font_size", size.coerceIn(15, 26)).apply()

    fun getSavedTeluguFontSize(): Int = prefs.getInt("telugu_font_size", DEFAULT_TELUGU_FONT_SIZE).coerceIn(12, 24)
    fun saveTeluguFontSize(size: Int) = prefs.edit().putInt("telugu_font_size", size.coerceIn(12, 24)).apply()

    fun getSavedGreekFontSize(): Int = prefs.getInt("greek_font_size", DEFAULT_GREEK_FONT_SIZE).coerceIn(11, 22)
    fun saveGreekFontSize(size: Int) = prefs.edit().putInt("greek_font_size", size.coerceIn(11, 22)).apply()

    // Hebrew gets its own independent size slider too, same range/default as
    // Greek — both are the "interlinear word chip" text, just for the other
    // testament, so there's no reason for them to behave differently.
    fun getSavedHebrewFontSize(): Int = prefs.getInt("hebrew_font_size", DEFAULT_GREEK_FONT_SIZE).coerceIn(11, 22)
    fun saveHebrewFontSize(size: Int) = prefs.edit().putInt("hebrew_font_size", size.coerceIn(11, 22)).apply()

    // Line spacing (line-height multiplier of font size) — independently
    // adjustable for English and Telugu, unlike Capacitor where this was
    // fixed at 1.85 for both. Defaults match that fixed value exactly, so
    // nobody's existing reading view changes until they touch the new
    // sliders themselves.
    fun getSavedEnglishLineHeight(): Float =
        prefs.getFloat("english_line_height", DEFAULT_LINE_HEIGHT_RATIO).coerceIn(1.2f, 2.6f)
    fun saveEnglishLineHeight(ratio: Float) =
        prefs.edit().putFloat("english_line_height", ratio.coerceIn(1.2f, 2.6f)).apply()

    fun getSavedTeluguLineHeight(): Float =
        prefs.getFloat("telugu_line_height", DEFAULT_LINE_HEIGHT_RATIO).coerceIn(1.2f, 2.6f)
    fun saveTeluguLineHeight(ratio: Float) =
        prefs.edit().putFloat("telugu_line_height", ratio.coerceIn(1.2f, 2.6f)).apply()

    // Padding between verse blocks (Capacitor's "--verse-spacing"). Line-height
    // itself is fixed at 1.85, matching Capacitor, and isn't user-adjustable.
    fun getSavedVerseSpacing(): Int = prefs.getInt("verse_spacing", 14).coerceIn(6, 28)
    fun saveVerseSpacing(spacing: Int) = prefs.edit().putInt("verse_spacing", spacing.coerceIn(6, 28)).apply()

    fun getRedLetterEnabled(): Boolean {
        return prefs.getBoolean("red_letter_enabled", true)
    }

    fun setRedLetterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("red_letter_enabled", enabled).apply()
    }

    fun getLastPosition(): Pair<String, Int> {
        val book = prefs.getString("last_book", "Genesis") ?: "Genesis"
        val chapter = prefs.getInt("last_chapter", 1)
        return Pair(book, chapter)
    }

    fun saveLastPosition(book: String, chapter: Int) {
        prefs.edit().putString("last_book", book).putInt("last_chapter", chapter).apply()
        // Widget refresh: MainViewModel.loadChapter() (the only caller of
        // this function) explicitly calls VerseOfDayWidget().updateAll()
        // right after this, so the "Continue reading" widget row updates
        // immediately rather than waiting on its own periodic cycle. Not
        // done here because updateAll() is a suspend call and this
        // function isn't — the caller owns the coroutine scope.
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean("is_first_launch", true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    fun getTotalStudyTimeMs(): Long {
        return prefs.getLong("total_study_time_ms", 0L)
    }

    fun addStudyTimeMs(deltaMs: Long) {
        val current = getTotalStudyTimeMs()
        prefs.edit().putLong("total_study_time_ms", current + deltaMs).apply()
    }

    private val _notesFlow = MutableStateFlow<List<NoteItem>>(loadNotesFromPrefs())
    val allNotes: Flow<List<NoteItem>> = _notesFlow.asStateFlow()

    private val _tagDefinitionsFlow = MutableStateFlow<List<TagDefinition>>(loadTagDefinitionsFromPrefs())
    val allTagDefinitions: Flow<List<TagDefinition>> = _tagDefinitionsFlow.asStateFlow()

    private fun loadTagDefinitionsFromPrefs(): List<TagDefinition> {
        val str = prefs.getString("tag_definitions_json", null)
        if (str != null) {
            try { return json.decodeFromString<List<TagDefinition>>(str) } catch (_: Exception) { }
        }
        // Existing notes are the source of truth for tags created before the
        // dedicated tag-definition store existed. Promote them automatically.
        return _notesFlowSafeForTags().flatMap { it.tags }.distinctBy { it.lowercase(Locale.US) }
            .map { TagDefinition(it) }
    }

    private fun _notesFlowSafeForTags(): List<NoteItem> {
        val str = prefs.getString("saved_notes_json", null) ?: return emptyList()
        return try { json.decodeFromString<List<NoteItem>>(str).map { it.normalized() } } catch (_: Exception) { emptyList() }
    }

    private fun saveTagDefinitionsToPrefs(list: List<TagDefinition>) {
        _tagDefinitionsFlow.value = list
        prefs.edit().putString("tag_definitions_json", json.encodeToString(list)).apply()
    }

    suspend fun addTag(name: String, description: String = "") {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return
        val current = _tagDefinitionsFlow.value
        if (current.any { it.name.equals(cleaned, ignoreCase = true) }) return
        saveTagDefinitionsToPrefs(current + TagDefinition(cleaned, description.trim()))
    }

    // Renames a tag and/or updates its description in one save — used by
    // the Tags screen's edit sheet, which always has both fields open at
    // once. Still propagates a name change into every note's tag list, the
    // same way the old renameTag did; a description-only edit leaves notes
    // untouched since notes never stored the description themselves.
    suspend fun updateTag(oldName: String, newName: String, description: String) {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) return
        val current = _tagDefinitionsFlow.value
        if (current.any { it.name.equals(cleaned, ignoreCase = true) && !it.name.equals(oldName, ignoreCase = true) }) return
        saveTagDefinitionsToPrefs(
            current.map { if (it.name.equals(oldName, true)) it.copy(name = cleaned, description = description.trim()) else it }
        )
        if (!cleaned.equals(oldName, ignoreCase = true)) {
            val updatedNotes = _notesFlow.value.map { note ->
                note.copy(tags = note.tags.map { if (it.equals(oldName, true)) cleaned else it }.distinct())
            }
            saveNotesToPrefs(updatedNotes)
        }
    }

    suspend fun deleteTag(name: String) {
        saveTagDefinitionsToPrefs(_tagDefinitionsFlow.value.filterNot { it.name.equals(name, true) })
        val updatedNotes = _notesFlow.value.map { note -> note.copy(tags = note.tags.filterNot { it.equals(name, true) }) }
        saveNotesToPrefs(updatedNotes)
        recordTombstone(tagTombstoneKey(name))
    }

    // ---- Tombstones (deletion tracking for backup/restore merges) ----
    //
    // A plain additive union merge (see importFromBackup) can't tell "never
    // existed on this device" apart from "existed here and was deleted" —
    // both look like "not present." Without a record of the deletion, a
    // stale backup or an out-of-sync device's copy resurrects the item on
    // the next restore. This is a single flat `key -> deletedAt` map
    // covering every backed-up entity type, rather than five separate
    // stores — the merge logic in importFromBackup only ever needs "is
    // this key deleted, and when." Key builders are the private
    // `*TombstoneKey` functions just below.
    private val _tombstonesFlow = MutableStateFlow(loadTombstonesFromPrefs())

    // Tombstones older than this are pruned on every save. They've long
    // since propagated to any device that was going to sync, and dropping
    // them keeps the backup file from growing forever with deletions no
    // restore will ever need to check again.
    private val TOMBSTONE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000 // 90 days

    private fun loadTombstonesFromPrefs(): Map<String, Long> {
        val str = prefs.getString("tombstones_json", null) ?: return emptyMap()
        return try { json.decodeFromString<Map<String, Long>>(str) } catch (_: Exception) { emptyMap() }
    }

    private fun saveTombstonesToPrefs(map: Map<String, Long>) {
        val cutoff = System.currentTimeMillis() - TOMBSTONE_RETENTION_MS
        val pruned = map.filterValues { it >= cutoff }
        _tombstonesFlow.value = pruned
        prefs.edit().putString("tombstones_json", json.encodeToString(pruned)).apply()
    }

    private fun recordTombstone(key: String) {
        val updated = _tombstonesFlow.value.toMutableMap()
        updated[key] = System.currentTimeMillis()
        saveTombstonesToPrefs(updated)
    }

    private fun noteTombstoneKey(id: Long) = "note:$id"
    private fun highlightTombstoneKey(book: String, chapter: Int, verse: Int) = "highlight:$book:$chapter:$verse"
    private fun completedTombstoneKey(book: String, chapter: Int, verse: Int) = "completed:$book:$chapter:$verse"
    private fun colorTombstoneKey(colorHex: String) = "color:${colorHex.lowercase(Locale.US)}"
    private fun tagTombstoneKey(name: String) = "tag:${name.lowercase(Locale.US)}"

    private val _completedVersesFlow = MutableStateFlow<List<CompletedVerseItem>>(loadCompletedFromPrefs())
    val allCompletedVerses: Flow<List<CompletedVerseItem>> = _completedVersesFlow.asStateFlow()

    private val _highlightsFlow = MutableStateFlow<List<HighlightItem>>(loadHighlightsFromPrefs())
    val allHighlights: Flow<List<HighlightItem>> = _highlightsFlow.asStateFlow()

    private val _highlightColorDefsFlow = MutableStateFlow<List<HighlightColorDef>>(loadHighlightColorDefsFromPrefs())
    val allHighlightColorDefs: Flow<List<HighlightColorDef>> = _highlightColorDefsFlow.asStateFlow()

    private fun loadNotesFromPrefs(): List<NoteItem> {
        val str = prefs.getString("saved_notes_json", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<NoteItem>>(str).map { it.normalized() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun NoteItem.normalized(): NoteItem {
        val normalizedRefs = if (refs.isNotEmpty()) {
            refs
        } else if (book.isNotBlank() && chapter > 0 && verse > 0) {
            listOf(NoteReference(book, chapter, verse, verseText))
        } else {
            emptyList()
        }
        val primary = normalizedRefs.firstOrNull()
        return copy(
            book = primary?.book ?: book,
            chapter = primary?.chapter ?: chapter,
            verse = primary?.verse ?: verse,
            verseText = primary?.verseText ?: verseText,
            refs = normalizedRefs,
            noteDate = noteDate.ifBlank {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(createdAt))
            }
        )
    }

    private fun saveNotesToPrefs(list: List<NoteItem>) {
        _notesFlow.value = list
        prefs.edit().putString("saved_notes_json", json.encodeToString(list)).apply()
    }

    private fun loadCompletedFromPrefs(): List<CompletedVerseItem> {
        val str = prefs.getString("saved_completed_json", null) ?: return emptyList()
        return try { json.decodeFromString(str) } catch (e: Exception) { emptyList() }
    }

    private fun saveCompletedToPrefs(list: List<CompletedVerseItem>) {
        _completedVersesFlow.value = list
        prefs.edit().putString("saved_completed_json", json.encodeToString(list)).apply()
    }

    private fun loadHighlightsFromPrefs(): List<HighlightItem> {
        val str = prefs.getString("saved_highlights_json", null) ?: return emptyList()
        return try { json.decodeFromString(str) } catch (e: Exception) { emptyList() }
    }

    private fun saveHighlightsToPrefs(list: List<HighlightItem>) {
        _highlightsFlow.value = list
        prefs.edit().putString("saved_highlights_json", json.encodeToString(list)).apply()
    }

    // No stored key yet means first launch (or upgrading from the old
    // fixed-swatch picker, which never persisted any defs at all) — seed
    // with the defaults rather than starting empty, same as Capacitor's own
    // `highlightColors || defaultHighlightColors()` fallback.
    private fun loadHighlightColorDefsFromPrefs(): List<HighlightColorDef> {
        val str = prefs.getString("saved_highlight_color_defs_json", null)
            ?: return DEFAULT_HIGHLIGHT_COLOR_DEFS
        return try { json.decodeFromString(str) } catch (e: Exception) { DEFAULT_HIGHLIGHT_COLOR_DEFS }
    }

    private fun saveHighlightColorDefsToPrefs(list: List<HighlightColorDef>) {
        _highlightColorDefsFlow.value = list
        prefs.edit().putString("saved_highlight_color_defs_json", json.encodeToString(list)).apply()
    }

    // Load Telugu translation from assets
    suspend fun getTeluguChapterVerses(bookName: String, chapter: Int): Map<Int, String> = withContext(Dispatchers.IO) {
        val cacheKey = "$bookName|$chapter"
        if (teluguCache.containsKey(cacheKey)) {
            return@withContext teluguCache[cacheKey]!!
        }

        val result = mutableMapOf<Int, String>()
        try {
            val assetPath = "telugu/$bookName.json"
            val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val jsonElement = json.parseToJsonElement(jsonString).jsonObject
            val chapters = jsonElement["chapters"]?.jsonArray ?: return@withContext emptyMap()
            
            val targetChapter = chapters.find { 
                it.jsonObject["chapter"]?.jsonPrimitive?.content == chapter.toString() 
            }?.jsonObject

            if (targetChapter != null) {
                val verses = targetChapter["verses"]?.jsonArray
                verses?.forEach { vElement ->
                    val vObj = vElement.jsonObject
                    val vNum = vObj["verse"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val vText = vObj["text"]?.jsonPrimitive?.content ?: ""
                    if (vNum > 0 && vText.isNotEmpty()) {
                        result[vNum] = vText
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        teluguCache[cacheKey] = result
        result
    }

    // Load KJV verses for a chapter. Room (populated by the one-time import
    // in BibleDataInitializer) is checked first — that's the offline,
    // zero-network path. If a chapter isn't in Room yet (import still
    // running, or failed for lack of connectivity), this falls back to the
    // old per-chapter live fetch below.
    suspend fun getChapterVerses(
        bookName: String,
        chapter: Int,
        includeTelugu: Boolean = true
    ): List<Verse> = withContext(Dispatchers.IO) {
        preloadGreekForChapter(bookName, chapter)
        preloadHebrewForChapter(bookName, chapter)

        val roomRows = bibleDao.getChapter(bookName, chapter)
        if (roomRows.isNotEmpty()) {
            return@withContext roomRows.map { row ->
                Verse(
                    book = row.book,
                    chapter = row.chapter,
                    number = row.number,
                    text = row.text,
                    isRedLetter = row.isRedLetter,
                    teluguText = if (includeTelugu) row.teluguText else null,
                    greekWords = generateGreekInterlinearIfNeeded(bookName, row.number, row.text),
                    hebrewWords = generateHebrewInterlinearIfNeeded(bookName, row.number)
                )
            }
        }

        val cacheKey = "$bookName|$chapter"
        if (kjvCache.containsKey(cacheKey)) {
            val cachedVerses = kjvCache[cacheKey]!!
            if (includeTelugu) {
                val teluguMap = getTeluguChapterVerses(bookName, chapter)
                return@withContext cachedVerses.map { v ->
                    v.copy(teluguText = teluguMap[v.number])
                }
            }
            return@withContext cachedVerses
        }

        // Try local disk file cache first
        val diskFile = File(context.cacheDir, "kjv_${bookName.replace(" ", "_")}_$chapter.json")
        var versesList: List<Verse>? = null

        if (diskFile.exists()) {
            try {
                val cachedContent = diskFile.readText()
                versesList = json.decodeFromString<List<Verse>>(cachedContent)
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        if (versesList == null) {
            // Fetch online from bible-api.com
            try {
                val encodedQuery = URLEncoder.encode("$bookName $chapter", "UTF-8")
                val url = URL("https://bible-api.com/$encodedQuery?translation=kjv")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = json.parseToJsonElement(responseStr).jsonObject
                    val versesArray = jsonObj["verses"]?.jsonArray
                    if (versesArray != null) {
                        val fetched = versesArray.map { vElement ->
                            val vObj = vElement.jsonObject
                            val vNum = vObj["verse"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                            val vText = vObj["text"]?.jsonPrimitive?.content?.trim() ?: ""
                            val isJesus = isJesusWordVerse(bookName, chapter, vNum)
                            Verse(
                                book = bookName,
                                chapter = chapter,
                                number = vNum,
                                text = vText,
                                isRedLetter = isJesus,
                                greekWords = generateGreekInterlinearIfNeeded(bookName, vNum, vText),
                                hebrewWords = generateHebrewInterlinearIfNeeded(bookName, vNum)
                            )
                        }
                        versesList = fetched
                        diskFile.writeText(json.encodeToString(fetched))
                        // Self-heal: write into Room too, so this chapter is
                        // offline from now on even if the full import never
                        // completes (e.g. persistently poor connection).
                        bibleDao.insertVerses(fetched.map { v ->
                            VerseEntity(
                                book = v.book,
                                chapter = v.chapter,
                                number = v.number,
                                text = v.text,
                                isRedLetter = v.isRedLetter
                            )
                        })
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (versesList == null) {
            // No data yet (import still running or network unavailable) —
            // return empty list; ReaderScreen shows loading/empty state.
            versesList = emptyList()
        }

        kjvCache[cacheKey] = versesList

        val teluguMap = if (includeTelugu) getTeluguChapterVerses(bookName, chapter) else emptyMap()
        versesList.map { v ->
            v.copy(teluguText = teluguMap[v.number])
        }
    }

    // Verse count for the picker's verse grid. Reuses getChapterVerses
    // rather than a separate Room COUNT query, so it goes through the exact
    // same Room/cache/network fallback chain as what actually renders —
    // can't disagree with it. Same approach as Capacitor's own
    // getVerseCount, which fully fetches/caches the chapter to learn its
    // length rather than trusting a separate source of truth.
    suspend fun getVerseCount(bookName: String, chapter: Int): Int = withContext(Dispatchers.IO) {
        getChapterVerses(bookName, chapter, includeTelugu = false).size
    }

    // Total verse count across a whole testament's worth of books, for the
    // Studied tab's OT/NT progress bars — a plain Room COUNT unlike
    // getVerseCount above, since this is a coarse denominator (not
    // rendered), not something that needs to agree exactly with the
    // network-fallback chain.
    suspend fun getVerseCountForBooks(books: List<String>): Int = withContext(Dispatchers.IO) {
        bibleDao.countVersesForBooks(books)
    }

    // Single-verse text lookup for list cards (Highlighted Verses, and
    // reused wherever a preview snippet is needed) — doesn't go through the
    // full getChapterVerses fallback chain since a card just needs
    // whatever's already in Room; null falls back to "not available offline"
    // in the UI, same as the cross-reference preview does.
    suspend fun getVerseText(bookName: String, chapter: Int, verse: Int): String? = withContext(Dispatchers.IO) {
        bibleDao.getVerseText(bookName, chapter, verse)
    }

    // Real interlinear Greek (STEPBible TAGNT), imported by GreekImporter.
    // Returns null for OT books (Hebrew, not Greek) and for any verse not
    // yet in Room — no fake/placeholder Greek is generated.
    private suspend fun generateGreekInterlinearIfNeeded(bookName: String, verseNum: Int, @Suppress("UNUSED_PARAMETER") englishText: String): List<GreekWord>? {
        val bookIndex = BIBLE_BOOKS.indexOf(bookName)
        if (bookIndex < 39) return null // Old Testament — no Greek interlinear

        val chapterWords = greekWordsCache.getOrPut(bookName) { mutableMapOf() }
        val words = chapterWords[verseNum]
        return words?.ifEmpty { null } // null = nothing to show; empty list same as null
    }

    // Chapter-scoped Greek word cache, populated by preloadGreekForChapter()
    // right before getChapterVerses builds its verse list — avoids a Room
    // query per verse when a per-chapter query gets everything at once.
    private val greekWordsCache = mutableMapOf<String, MutableMap<Int, List<GreekWord>>>()

    private suspend fun preloadGreekForChapter(bookName: String, chapter: Int) {
        val bookIndex = BIBLE_BOOKS.indexOf(bookName)
        if (bookIndex < 39) return
        val rows = bibleDao.getGreekWordsForChapter(bookName, chapter)
        val byVerse = rows.groupBy { it.verse }
            .mapValues { (_, rowsForVerse) ->
                rowsForVerse.sortedBy { it.orderIndex }.map { row ->
                    GreekWord(row.greek, row.transliteration, row.gloss, row.strongs, row.morphology)
                }
            }
        greekWordsCache[bookName] = byVerse.toMutableMap()
    }

    // Real interlinear Hebrew (STEPBible TAHOT), imported by HebrewImporter.
    // Mirrors generateGreekInterlinearIfNeeded exactly, just the other
    // testament's book range and cache. Returns null for NT books and for
    // any verse not yet in Room — no fake/placeholder Hebrew is generated.
    private suspend fun generateHebrewInterlinearIfNeeded(bookName: String, verseNum: Int): List<HebrewWord>? {
        val bookIndex = BIBLE_BOOKS.indexOf(bookName)
        if (bookIndex !in 0..38) return null // New Testament — no Hebrew interlinear

        val chapterWords = hebrewWordsCache.getOrPut(bookName) { mutableMapOf() }
        val words = chapterWords[verseNum]
        return words?.ifEmpty { null }
    }

    // Chapter-scoped Hebrew word cache, populated by preloadHebrewForChapter()
    // right before getChapterVerses builds its verse list — same reasoning
    // as greekWordsCache above.
    private val hebrewWordsCache = mutableMapOf<String, MutableMap<Int, List<HebrewWord>>>()

    private suspend fun preloadHebrewForChapter(bookName: String, chapter: Int) {
        val bookIndex = BIBLE_BOOKS.indexOf(bookName)
        if (bookIndex !in 0..38) return
        val rows = bibleDao.getHebrewWordsForChapter(bookName, chapter)
        val byVerse = rows.groupBy { it.verse }
            .mapValues { (_, rowsForVerse) ->
                rowsForVerse.sortedBy { it.orderIndex }.map { row ->
                    HebrewWord(row.hebrew, row.transliteration, row.gloss, row.strongs, row.morphology)
                }
            }
        hebrewWordsCache[bookName] = byVerse.toMutableMap()
    }

    // Jesus words red-letter detection
    private fun isJesusWordVerse(bookName: String, chapter: Int, verse: Int): Boolean {
        return when (bookName) {
            "Matthew" -> chapter in 3..28
            "Mark" -> chapter in 1..16
            "Luke" -> chapter in 2..24
            "John" -> chapter in 1..21
            "Revelation" -> chapter in 1..3 || chapter in 19..22
            else -> false
        }
    }

    // Persistence mutation actions
    suspend fun toggleCompletedVerse(book: String, chapter: Int, verse: Int) {
        val current = _completedVersesFlow.value.toMutableList()
        val existing = current.find { it.book == book && it.chapter == chapter && it.verse == verse }
        if (existing != null) {
            current.remove(existing)
            recordTombstone(completedTombstoneKey(book, chapter, verse))
        } else {
            current.add(CompletedVerseItem(book, chapter, verse, System.currentTimeMillis()))
        }
        saveCompletedToPrefs(current)
    }

    suspend fun removeCompletedVerse(book: String, chapter: Int, verse: Int) {
        val current = _completedVersesFlow.value.toMutableList()
        current.removeAll { it.book == book && it.chapter == chapter && it.verse == verse }
        saveCompletedToPrefs(current)
        recordTombstone(completedTombstoneKey(book, chapter, verse))
    }

    suspend fun setHighlight(book: String, chapter: Int, verse: Int, colorHex: String) {
        val current = _highlightsFlow.value.toMutableList()
        current.removeAll { it.book == book && it.chapter == chapter && it.verse == verse }
        current.add(HighlightItem(book, chapter, verse, colorHex))
        saveHighlightsToPrefs(current)
    }

    suspend fun removeHighlight(book: String, chapter: Int, verse: Int) {
        val current = _highlightsFlow.value.toMutableList()
        current.removeAll { it.book == book && it.chapter == chapter && it.verse == verse }
        saveHighlightsToPrefs(current)
        recordTombstone(highlightTombstoneKey(book, chapter, verse))
    }

    suspend fun addHighlightColor(label: String, colorHex: String) {
        val current = _highlightColorDefsFlow.value.toMutableList()
        current.add(HighlightColorDef(label, colorHex))
        saveHighlightColorDefsToPrefs(current)
    }

    suspend fun renameHighlightColor(colorHex: String, newLabel: String) {
        val current = _highlightColorDefsFlow.value.map {
            if (it.colorHex == colorHex) it.copy(label = newLabel) else it
        }
        saveHighlightColorDefsToPrefs(current)
    }

    // Disabling hides a color from the picker without touching any verse
    // already highlighted with it. Guards against disabling the last
    // remaining enabled color, since that would leave no way to highlight
    // anything new — mirrors the same "keep at least one" guard Capacitor
    // applies before letting a color be turned off.
    suspend fun setHighlightColorEnabled(colorHex: String, enabled: Boolean) {
        val current = _highlightColorDefsFlow.value
        if (!enabled && current.count { it.enabled } <= 1 &&
            current.firstOrNull { it.colorHex == colorHex }?.enabled == true) {
            return
        }
        val updated = current.map {
            if (it.colorHex == colorHex) it.copy(enabled = enabled) else it
        }
        saveHighlightColorDefsToPrefs(updated)
    }

    // Since a color's identity IS its hex (see HighlightColors.kt), picking
    // a new hex for an existing def has to carry every verse already
    // highlighted with the old hex along with it — otherwise they'd
    // silently fall out of that group. Capacitor doesn't need this step
    // (it keys highlights by a separate colorId that never changes), but
    // this achieves the same end result.
    suspend fun recolorHighlightColor(oldHex: String, newHex: String) {
        val updatedDefs = _highlightColorDefsFlow.value.map {
            if (it.colorHex == oldHex) it.copy(colorHex = newHex) else it
        }
        saveHighlightColorDefsToPrefs(updatedDefs)

        // Bumps updatedAt too, not just colorHex — this is a real local
        // mutation of every affected highlight, and leaving updatedAt
        // stale would let an older incoming backup's copy (still on the
        // old hex) win a future merge purely on timestamp, undoing the
        // recolor.
        val now = System.currentTimeMillis()
        val updatedHighlights = _highlightsFlow.value.map {
            if (it.colorHex == oldHex) it.copy(colorHex = newHex, updatedAt = now) else it
        }
        saveHighlightsToPrefs(updatedHighlights)
    }

    // Matches Capacitor's confirm-then-delete: deleting a color un-highlights
    // every verse that was using it, not just removes the def from the list.
    suspend fun deleteHighlightColor(colorHex: String) {
        val updatedDefs = _highlightColorDefsFlow.value.filter { it.colorHex != colorHex }
        saveHighlightColorDefsToPrefs(updatedDefs)

        // Every verse using this color gets un-highlighted too (see the
        // comment above), so each of those needs its own tombstone as well
        // as the color def itself — otherwise a restore could re-add the
        // individual highlight entries even though the color they pointed
        // to is (correctly) gone, leaving orphaned, unlabeled highlights.
        val removedHighlights = _highlightsFlow.value.filter { it.colorHex == colorHex }
        val updatedHighlights = _highlightsFlow.value.filter { it.colorHex != colorHex }
        saveHighlightsToPrefs(updatedHighlights)

        recordTombstone(colorTombstoneKey(colorHex))
        removedHighlights.forEach { recordTombstone(highlightTombstoneKey(it.book, it.chapter, it.verse)) }
    }

    suspend fun saveNote(
        id: Long,
        title: String,
        text: String,
        noteDate: String,
        refs: List<NoteReference>,
        tags: List<String>
    ) {
        val current = _notesFlow.value.toMutableList()
        val now = System.currentTimeMillis()
        val noteId = if (id > 0) id else System.currentTimeMillis()
        val normalizedRefs = refs.distinctBy { Triple(it.book, it.chapter, it.verse) }
        val primary = normalizedRefs.firstOrNull()
        val existingIndex = current.indexOfFirst { it.id == noteId }

        val newNote = NoteItem(
            id = noteId,
            title = title.trim(),
            text = text,
            noteDate = noteDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) },
            refs = normalizedRefs,
            // Keep the old fields synchronized for old callers/backups and
            // for compatibility with data written by previous builds.
            book = primary?.book ?: "",
            chapter = primary?.chapter ?: 0,
            verse = primary?.verse ?: 0,
            verseText = primary?.verseText ?: "",
            tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            createdAt = if (existingIndex >= 0) current[existingIndex].createdAt else now,
            updatedAt = now
        )
        if (existingIndex >= 0) {
            current[existingIndex] = newNote
        } else {
            current.add(0, newNote)
        }
        saveNotesToPrefs(current)
        val known = _tagDefinitionsFlow.value.toMutableList()
        tags.map { it.trim() }.filter { it.isNotBlank() }.forEach { tag ->
            if (known.none { it.name.equals(tag, true) }) known.add(TagDefinition(tag))
        }
        saveTagDefinitionsToPrefs(known)
    }

    // Compatibility overload for existing UI/callers while the richer note
    // editor is rolled out.
    suspend fun saveNote(id: Long, book: String, chapter: Int, verse: Int, verseText: String, text: String, tags: List<String>) {
        saveNote(
            id = id,
            title = "",
            text = text,
            noteDate = "",
            refs = listOf(NoteReference(book, chapter, verse, verseText)),
            tags = tags
        )
    }

    suspend fun deleteNote(id: Long) {
        val current = _notesFlow.value.toMutableList()
        current.removeAll { it.id == id }
        saveNotesToPrefs(current)
        recordTombstone(noteTombstoneKey(id))
    }

    suspend fun resolveNoteReference(ref: NoteReference): NoteReference {
        val text = getVerseText(ref.book, ref.chapter, ref.verse) ?: ""
        return ref.copy(verseText = if (ref.verseText.isBlank()) text else ref.verseText)
    }


    // Real full-Bible search across whatever's currently in Room (all 66
    // books once the import has run, growing incrementally before that).
    // Falls back to the old handful-of-books live search only if Room is
    // still completely empty (e.g. first launch, offline, import not yet run).
    //
    // Also recognizes a typed reference ("john 3", "john 3:16", "1 john 3:16")
    // and short-circuits straight to that chapter/verse instead of doing a
    // text search — this is what powers the reference examples shown in the
    // search box's placeholder text.
    suspend fun searchBible(query: String, caseSensitive: Boolean = false): List<Verse> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        parseReference(q)?.let { ref ->
            val chapterVerses = getChapterVerses(ref.book, ref.chapter, includeTelugu = true)
            return@withContext if (ref.verse != null) {
                chapterVerses.filter { it.number == ref.verse }
            } else {
                chapterVerses
            }
        }

        // Room's LIKE is only reliably case-insensitive for ASCII, so it's
        // used purely as a coarse (case-insensitive) candidate filter here;
        // the actual case-sensitive check, when requested, happens in
        // Kotlin below where Char.equals(ignoreCase) is unambiguous for
        // both English and Telugu text.
        val roomCandidates = bibleDao.search(q)
        val bibleImported = roomCandidates.isNotEmpty() || bibleDao.countAllVerses() > 0
        if (bibleImported) {
            val matched = if (caseSensitive) {
                roomCandidates.filter { row ->
                    row.text.contains(q) || (row.teluguText?.contains(q) == true)
                }
            } else {
                roomCandidates
            }
            return@withContext matched.map { row ->
                Verse(
                    book = row.book,
                    chapter = row.chapter,
                    number = row.number,
                    text = row.text,
                    isRedLetter = row.isRedLetter,
                    teluguText = row.teluguText
                )
            }
        }

        val qLower = q.lowercase()
        val results = mutableListOf<Verse>()
        val searchBooks = listOf("Genesis", "Psalms", "Proverbs", "Matthew", "John", "Romans")
        for (b in searchBooks) {
            for (c in 1..2) {
                val verses = getChapterVerses(b, c, includeTelugu = true)
                for (v in verses) {
                    val matches = if (caseSensitive) {
                        v.text.contains(q) || (v.teluguText?.contains(q) == true)
                    } else {
                        v.text.lowercase().contains(qLower) || (v.teluguText?.lowercase()?.contains(qLower) == true)
                    }
                    if (matches) results.add(v)
                }
            }
        }
        results
    }

    private data class ParsedReference(val book: String, val chapter: Int, val verse: Int?)

    // Matches a trailing " <chapter>" or " <chapter>:<verse>" and treats
    // everything before it as the book name, e.g. "1 john 3:16" ->
    // book="1 john", chapter=3, verse=16.
    private val referencePattern = Regex("""^\s*(.+?)\s+(\d{1,3})(?::(\d{1,3}))?\s*$""")

    private fun parseReference(query: String): ParsedReference? {
        val match = referencePattern.find(query) ?: return null
        val bookPart = match.groupValues[1]
        val chapter = match.groupValues[2].toIntOrNull() ?: return null
        val verse = match.groupValues[3].toIntOrNull()
        val book = matchBookName(bookPart) ?: return null
        val maxChapters = BOOK_CHAPTER_COUNTS[book] ?: return null
        if (chapter < 1 || chapter > maxChapters) return null
        return ParsedReference(book, chapter, verse)
    }

    // Case-insensitive book-name matching that tolerates missing/extra
    // spaces around a leading number ("1john" / "1 john" / "1  john" all
    // resolve to "1 John"), plus an unambiguous prefix match ("gen" ->
    // "Genesis") so partial typing still resolves as long as it's unique.
    private fun matchBookName(input: String): String? {
        val norm = input.trim().lowercase().replace(Regex("\\s+"), " ")
        if (norm.isEmpty()) return null
        val normCollapsed = norm.replace(" ", "")

        BIBLE_BOOKS.firstOrNull { it.lowercase() == norm }?.let { return it }
        BIBLE_BOOKS.firstOrNull { it.lowercase().replace(" ", "") == normCollapsed }?.let { return it }

        val prefixMatches = BIBLE_BOOKS.filter { it.lowercase().replace(" ", "").startsWith(normCollapsed) }
        if (prefixMatches.size == 1) return prefixMatches.first()

        return null
    }

    // English dictionary lookup — Noah Webster's 1828 dictionary, bundled
    // as a Room table by WebsterImporter (see BibleDataInitializer). Fully
    // offline, period-correct for KJV archaic vocabulary; no live fallback
    // to api.dictionaryapi.dev. Session memory cache avoids repeat queries.
    private val dictCache = mutableMapOf<String, EnglishDictionaryEntry?>()

    suspend fun lookupEnglishWord(rawWord: String): EnglishDictionaryEntry? = withContext(Dispatchers.IO) {
        val word = rawWord.lowercase().replace(Regex("[^a-z]"), "")
        if (word.isEmpty()) return@withContext null
        if (dictCache.containsKey(word)) return@withContext dictCache[word]

        val exact = bibleDao.getWebsterEntry(word)
        val result = if (exact != null) {
            parseWebsterDefinition(word, exact.definition)
        } else {
            lookupWebsterStem(word)
        }
        dictCache[word] = result
        result
    }

    // Webster's 1828 lists base headwords only ("tribulation"), not every
    // inflected form the KJV text actually uses ("tribulations", "loveth",
    // "walkest", ...) — normal for a period dictionary, not missing data.
    // When the exact word isn't a headword, try a short list of plausible
    // base forms — most specific/likely-correct suffix first — and use
    // whichever one actually resolves against the same offline dataset,
    // instead of reporting "no definition" for a word one suffix-strip
    // away from a real entry.
    private suspend fun lookupWebsterStem(word: String): EnglishDictionaryEntry? {
        // A handful of archaic KJV verb forms are genuinely irregular
        // (contractions, not suffixed forms), so no suffix-strip rule below
        // finds them: "saith" isn't "say" + a stripped suffix, it's just a
        // different spelling. Common enough in the text to special-case
        // directly rather than leave unresolved.
        IRREGULAR_ARCHAIC_FORMS[word]?.let { candidates ->
            for (base in candidates) {
                val entity = bibleDao.getWebsterEntry(base)
                if (entity != null) {
                    return parseWebsterDefinition(word, entity.definition)?.copy(resolvedFrom = base)
                }
            }
        }

        val candidates = mutableListOf<String>()
        when {
            word.endsWith("ies") && word.length > 4 ->
                candidates += word.dropLast(3) + "y" // prophecies -> prophecy
            word.endsWith("ves") && word.length > 4 -> {
                candidates += word.dropLast(3) + "f"  // wolves -> wolf
                candidates += word.dropLast(3) + "fe" // lives -> life
            }
            word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses") ->
                candidates += word.dropLast(2) // churches -> church
        }
        if (word.endsWith("s") && !word.endsWith("ss") && word.length > 2) {
            candidates += word.dropLast(1) // sins -> sin, disciples -> disciple
        }
        if (word.endsWith("eth") && word.length > 4) {
            candidates += word.dropLast(3) // walketh -> walk
            candidates += word.dropLast(2) // loveth -> love, believeth -> believe (base already ends in "e")
        }
        if (word.endsWith("est") && word.length > 4) {
            candidates += word.dropLast(3) // walkest -> walk
            candidates += word.dropLast(2) // believest -> believe (base already ends in "e")
        }
        if (word.endsWith("ed") && word.length > 3) {
            candidates += word.dropLast(2)
        }
        if (word.endsWith("ing") && word.length > 4) {
            candidates += word.dropLast(3)
        }

        for (candidate in candidates.distinct()) {
            val entity = bibleDao.getWebsterEntry(candidate)
            if (entity != null) {
                return parseWebsterDefinition(word, entity.definition)?.copy(resolvedFrom = candidate)
            }
        }
        return null
    }

    // Splits WebsterEntity.definition ("<pos>\n1. sense one\n2. sense two…",
    // see the format note on WebsterEntity) back into the same
    // EnglishDictionaryEntry/DictionaryMeaning shape the sheet already
    // knows how to render — a single "meaning" group since the 1828 CSV
    // gives one part of speech per headword.
    private fun parseWebsterDefinition(word: String, raw: String): EnglishDictionaryEntry? {
        val lines = raw.split("\n")
        if (lines.isEmpty()) return null
        val pos = lines.first()
        val defs = lines.drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirst(Regex("^\\d+\\.\\s*"), "") }
        if (defs.isEmpty()) return null
        return EnglishDictionaryEntry(
            word = word,
            phonetic = null,
            meanings = listOf(DictionaryMeaning(partOfSpeech = pos, definitions = defs))
        )
    }

    // ---- Greek lexicon lookup (TBESG — tap a Greek word for its full Strong's entry) ----
    // Mirrors the Capacitor app's loadStrongsGreekLexicon()/openGreekWordSheet():
    // TBESG is downloaded and imported into Room lazily, the first time a
    // Greek word is tapped (not eagerly at startup alongside KJV/Greek/xrefs,
    // since it's a bonus layer most sessions never touch). importOnce below
    // is the single-flight equivalent of Capacitor's cached
    // strongsGreekLexiconPromise, so concurrent taps during the first-ever
    // download don't kick off duplicate downloads.
    private val lexiconImportMutex = Mutex()
    private var lexiconImported = false

    /** True once TBESG is available in Room (already was, or just finished downloading). */
    private suspend fun ensureLexiconImported(): Boolean {
        if (lexiconImported) return true
        return lexiconImportMutex.withLock {
            if (lexiconImported) return@withLock true
            if (bibleDao.countLexiconEntries() > 100) {
                lexiconImported = true
                return@withLock true
            }
            val imported = TbesgImporter.importInto(bibleDao)
            if (imported > 0) lexiconImported = true
            // imported <= 0 (no connection, or the file was unreachable):
            // lexiconImported stays false, so the next tap retries — same
            // as Capacitor's promise rejecting and a later call trying again.
            lexiconImported
        }
    }

    // Strong's numbers as tagged on a word (dStrong) can carry a trailing
    // disambiguation letter (e.g. "G3588A"). Unlike the old behavior, that
    // letter is looked up exactly first (see LexiconEntity's class doc for
    // why — two entirely unrelated words can share a bare number, e.g.
    // G0001G "Α" vs G0001H "ἔα"), falling back to the bare number only if
    // no row has that exact disambiguated key.
    private fun normalizeLexiconKey(dStrong: String?): String? {
        if (dStrong.isNullOrBlank()) return null
        return dStrong.trim().uppercase().takeIf { Regex("^G\\d+").containsMatchIn(it) }
    }

    private fun bareLexiconKey(fullKey: String): String? =
        Regex("^G\\d+").find(fullKey)?.value

    /**
     * Looks up a Greek word's full lexicon entry by its Strong's number.
     * Three distinct "nothing to show" outcomes, matching the three
     * distinct messages Capacitor's openGreekWordSheet() shows:
     * no Strong's number tagged at all, a number with no TBESG entry, and
     * a first-time download that failed (no connection).
     */
    suspend fun getLexiconEntry(dStrong: String?): LexiconLookupResult = withContext(Dispatchers.IO) {
        val fullKey = normalizeLexiconKey(dStrong) ?: return@withContext LexiconLookupResult.NoStrongsNumber
        val bareKey = bareLexiconKey(fullKey) ?: return@withContext LexiconLookupResult.NoStrongsNumber
        if (!ensureLexiconImported()) return@withContext LexiconLookupResult.NetworkError
        val entity = bibleDao.getLexiconEntryByDisambiguated(fullKey)
            ?: bibleDao.getLexiconEntryByBareStrongs(bareKey)
        if (entity == null || (entity.definition.isBlank() && entity.gloss.isBlank())) {
            LexiconLookupResult.NotFound
        } else {
            LexiconLookupResult.Found(LexiconEntry(entity.lemma, entity.transliteration, entity.morphology, entity.gloss, entity.definition))
        }
    }

    // ---- Hebrew lexicon lookup (Strong's H-numbers, TBESH) ----
    // Same on-demand download as Greek's TBESG — first tap on a Hebrew word
    // triggers ensureHebrewLexiconImported() below, which downloads TBESH
    // once and caches it in the same lexicon_entries table Greek uses (safe:
    // "G..." and "H..." keys never collide).
    // Hebrew lexicon (TBESH) import — same on-demand, single-flight pattern
    // as ensureLexiconImported() above, just its own flag/mutex/counter so
    // Hebrew's TBESH download is tracked independently of Greek's TBESG
    // (they're two different STEPBible files; one having already downloaded
    // says nothing about the other).
    private val hebrewLexiconImportMutex = Mutex()
    private var hebrewLexiconImported = false

    private suspend fun ensureHebrewLexiconImported(): Boolean {
        if (hebrewLexiconImported) return true
        return hebrewLexiconImportMutex.withLock {
            if (hebrewLexiconImported) return@withLock true
            if (bibleDao.countHebrewLexiconEntries() > 100) {
                hebrewLexiconImported = true
                return@withLock true
            }
            val imported = TbeshImporter.importInto(bibleDao)
            if (imported > 0) hebrewLexiconImported = true
            hebrewLexiconImported
        }
    }

    private fun normalizeHebrewLexiconKey(dStrong: String?): String? {
        if (dStrong.isNullOrBlank()) return null
        return dStrong.trim().uppercase().takeIf { Regex("^H\\d+").containsMatchIn(it) }
    }

    private fun bareHebrewLexiconKey(fullKey: String): String? =
        Regex("^H\\d+").find(fullKey)?.value

    suspend fun getHebrewLexiconEntry(dStrong: String?): LexiconLookupResult = withContext(Dispatchers.IO) {
        val fullKey = normalizeHebrewLexiconKey(dStrong) ?: return@withContext LexiconLookupResult.NoStrongsNumber
        val bareKey = bareHebrewLexiconKey(fullKey) ?: return@withContext LexiconLookupResult.NoStrongsNumber
        if (!ensureHebrewLexiconImported()) return@withContext LexiconLookupResult.NetworkError
        val entity = bibleDao.getLexiconEntryByDisambiguated(fullKey)
            ?: bibleDao.getLexiconEntryByBareStrongs(bareKey)
        if (entity == null || (entity.definition.isBlank() && entity.gloss.isBlank())) {
            LexiconLookupResult.NotFound
        } else {
            LexiconLookupResult.Found(LexiconEntry(entity.lemma, entity.transliteration, entity.morphology, entity.gloss, entity.definition))
        }
    }

    // Real cross references (Treasury of Scripture Knowledge), imported by
    // CrossReferenceImporter. Falls back to the old hardcoded handful only
    // if Room has no cross-reference data at all yet (import still running
    // or failed for lack of connectivity).
    suspend fun getCrossReferences(book: String, chapter: Int, verse: Int): List<CrossReferenceItem> = withContext(Dispatchers.IO) {
        val rows = bibleDao.getCrossReferences(book, chapter, verse)
        if (rows.isNotEmpty()) {
            return@withContext rows.take(20).map { row ->
                val preview = bibleDao.getVerseText(row.toBook, row.toChapter, row.toVerse) ?: ""
                CrossReferenceItem(
                    targetBook = row.toBook,
                    targetChapter = row.toChapter,
                    targetVerse = row.toVerse,
                    previewText = preview
                )
            }
        }
        fallbackCrossReferences(book, chapter, verse)
    }

    // Chapter-scoped: which verse numbers have at least one cross-reference,
    // loaded once per chapter so VerseCard can mark them with a dagger
    // without a per-verse round trip. Empty set (rather than the hardcoded
    // fallback book) if the real dataset hasn't imported yet — the fallback
    // stays reserved for the tap-through sheet itself, not the marker.
    suspend fun getCrossReferenceVerseNumbers(book: String, chapter: Int): Set<Int> = withContext(Dispatchers.IO) {
        bibleDao.getCrossReferenceVerseNumbers(book, chapter).toSet()
    }

    private fun fallbackCrossReferences(book: String, chapter: Int, verse: Int): List<CrossReferenceItem> {
        val key = "$book $chapter:$verse"
        val knownMap: Map<String, List<CrossReferenceItem>> = mapOf(
            "Genesis 1:1" to listOf(
                CrossReferenceItem("John", 1, 1, "In the beginning was the Word, and the Word was with God, and the Word was God."),
                CrossReferenceItem("Psalms", 33, 6, "By the word of the LORD were the heavens made; and all the host of them by the breath of his mouth."),
                CrossReferenceItem("Hebrews", 11, 3, "Through faith we understand that the worlds were framed by the word of God..."),
                CrossReferenceItem("Revelation", 4, 11, "Thou art worthy, O Lord, to receive glory and honour and power: for thou hast created all things...")
            ),
            "John 3:16" to listOf(
                CrossReferenceItem("Romans", 5, 8, "But God commendeth his love toward us, in that, while we were yet sinners, Christ died for us."),
                CrossReferenceItem("1 John", 4, 9, "In this was manifested the love of God toward us, because that God sent his only begotten Son into the world..."),
                CrossReferenceItem("Ephesians", 2, 4, "But God, who is rich in mercy, for his great love wherewith he loved us...")
            ),
            "Psalms 23:1" to listOf(
                CrossReferenceItem("Isaiah", 40, 11, "He shall feed his flock like a shepherd: he shall gather the lambs with his arm..."),
                CrossReferenceItem("Ezekiel", 34, 12, "As a shepherd seeketh out his flock in the day that he is among his sheep that are scattered..."),
                CrossReferenceItem("John", 10, 11, "I am the good shepherd: the good shepherd giveth his life for the sheep.")
            ),
            "Matthew 5:3" to listOf(
                CrossReferenceItem("Isaiah", 66, 2, "To this man will I look, even to him that is poor and of a contrite spirit, and trembleth at my word."),
                CrossReferenceItem("James", 2, 5, "Hath not God chosen the poor of this world rich in faith, and heirs of the kingdom..."),
                CrossReferenceItem("Proverbs", 16, 19, "Better it is to be of an humble spirit with the lowly, than to divide the spoil with the proud.")
            )
        )

        return knownMap[key] ?: listOf(
            CrossReferenceItem(
                targetBook = if (book == "John") "Genesis" else "John",
                targetChapter = 1,
                targetVerse = 1,
                previewText = "In the beginning was the Word, and the Word was with God, and the Word was God."
            ),
            CrossReferenceItem(
                targetBook = "Psalms",
                targetChapter = 119,
                targetVerse = 105,
                previewText = "Thy word is a lamp unto my feet, and a light unto my path."
            ),
            CrossReferenceItem(
                targetBook = "Hebrews",
                targetChapter = 4,
                targetVerse = 12,
                previewText = "For the word of God is quick, and powerful, and sharper than any twoedged sword..."
            ),
            CrossReferenceItem(
                targetBook = "Romans",
                targetChapter = 8,
                targetVerse = 28,
                previewText = "And we know that all things work together for good to them that love God..."
            )
        )
    }

    // ---- Backup / Restore (local file + Google Drive appdata) ----

    private val backupTimestampFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

    /** Snapshots everything backup-worthy (notes, tags, completed verses,
     * highlights) into the shared [BackupData] envelope. Used for both the
     * local SAF export and the Google Drive appdata upload, so the two
     * paths can never drift out of sync with each other. */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val data = BackupData(
            exportedAt = backupTimestampFormat.format(Date()),
            notes = _notesFlow.value,
            // `tags` (name-only) is kept for backups read by older builds;
            // `tagDefs` is what this build itself reads back, since it's
            // the only one that carries `description`.
            tags = _tagDefinitionsFlow.value.map { it.name },
            tagDefs = _tagDefinitionsFlow.value,
            completed = _completedVersesFlow.value,
            highlights = _highlightsFlow.value,
            highlightColorDefs = _highlightColorDefsFlow.value,
            tombstones = SyncTombstones(_tombstonesFlow.value)
        )
        json.encodeToString(data)
    }

    /** Result of merging an imported [BackupData] into what's already on
     * this device — surfaced in Settings so the user knows the restore
     * actually did something rather than silently no-op'ing. */
    data class ImportResult(
        val notesAdded: Int,
        val notesUpdated: Int,
        val tagsAdded: Int,
        val completedAdded: Int,
        val highlightsAdded: Int,
        val colorsAdded: Int,
        val colorsRelabeled: Int
    )

    /** Restores from a backup produced by [exportBackupJson] (this device,
     * another device, or a prior Google Drive backup). Adds to or freshens
     * what's on this device, but is no longer purely additive — a key that
     * this device has a *tombstone* for (see the Tombstones section above)
     * is treated as deleted and will not be resurrected by the incoming
     * copy, except where noted below:
     *  - tombstones: merged first, union by key, keeping the newer
     *    `deletedAt` on a same-key collision (both sides agree it's
     *    deleted; the timestamp only matters for pruning later).
     *  - notes: unioned by id; on a matching id, whichever copy has the
     *    newer `updatedAt` wins. A tombstoned id is skipped *unless* the
     *    incoming note's `updatedAt` is newer than the tombstone's
     *    `deletedAt` — that means the note was edited (or re-created)
     *    after the delete, which reads as an intentional undelete rather
     *    than stale data resurrecting.
     *  - tags: unioned by name (case-insensitive), preferring the incoming
     *    `tagDefs` (name + description) and falling back to the legacy
     *    `tags` (name-only) for backups from older builds. A tombstoned
     *    name is skipped. On a name collision where only the description
     *    differs, the incoming description fills in a blank local one
     *    (never overwrites a local description that's already set, since
     *    unlike a color relabel there's no "this was a deliberate rename"
     *    signal here).
     *  - completed verses: unioned by (book, chapter, verse). No per-item
     *    timestamp strong enough to out-rank a tombstone beyond
     *    `completedAt` itself (which restore would just overwrite with an
     *    older value anyway), so a tombstoned key is always skipped while
     *    its tombstone is live — it only ages out via the retention
     *    window. On a non-tombstoned collision the earliest `completedAt`
     *    of the two is kept since that's the true first time it was read.
     *  - highlights: unioned by (book, chapter, verse); now that
     *    HighlightItem carries `updatedAt`, a tombstoned key is skipped
     *    the same way notes are — unless the incoming `updatedAt` postdates
     *    the deletion, which reads as an intentional re-highlight after
     *    deleting. On a non-tombstoned collision, whichever copy is newer
     *    wins (not "the backup always wins" as before), so restoring an
     *    old backup can't clobber a recolor you made locally since then.
     *  - highlight color defs: unioned by colorHex (a color's identity —
     *    see model/HighlightColors.kt); a tombstoned hex is skipped. On a
     *    matching, non-tombstoned hex the backup's label wins, so a custom
     *    rename made elsewhere and restored here actually takes effect.
     */
    suspend fun importFromBackup(jsonText: String): ImportResult = withContext(Dispatchers.IO) {
        val data = json.decodeFromString<BackupData>(jsonText)

        // Tombstones are merged first so every entity merge below can
        // check against the up-to-date combined set in one pass.
        val mergedTombstones = _tombstonesFlow.value.toMutableMap()
        for ((key, deletedAt) in data.tombstones.entries) {
            val existingAt = mergedTombstones[key]
            if (existingAt == null || deletedAt > existingAt) {
                mergedTombstones[key] = deletedAt
            }
        }
        saveTombstonesToPrefs(mergedTombstones)
        val tombstones = _tombstonesFlow.value

        // Notes: union by id, newest updatedAt wins on collision. A
        // tombstoned id is skipped unless the incoming edit postdates the
        // deletion (see doc comment above).
        val existingNotes = _notesFlow.value
        val existingNoteIds = existingNotes.associateBy { it.id }
        var notesAdded = 0
        var notesUpdated = 0
        val mergedNotes = existingNotes.toMutableList()
        for (incoming in data.notes) {
            val deletedAt = tombstones[noteTombstoneKey(incoming.id)]
            if (deletedAt != null && incoming.updatedAt <= deletedAt) continue
            val existing = existingNoteIds[incoming.id]
            if (existing == null) {
                mergedNotes.add(incoming.normalized())
                notesAdded++
            } else if (incoming.updatedAt > existing.updatedAt) {
                val idx = mergedNotes.indexOfFirst { it.id == incoming.id }
                if (idx >= 0) mergedNotes[idx] = incoming.normalized()
                notesUpdated++
            }
        }
        saveNotesToPrefs(mergedNotes.sortedByDescending { it.createdAt })

        // Tags: union by name, case-insensitive; new names only. Prefers
        // tagDefs (carries description) over the legacy name-only `tags`
        // list; if a backup somehow has both, tagDefs' names win so a name
        // isn't processed twice.
        val existingTags = _tagDefinitionsFlow.value
        var tagsAdded = 0
        val mergedTags = existingTags.toMutableList()
        val incomingTagDefs = if (data.tagDefs.isNotEmpty()) {
            data.tagDefs
        } else {
            data.tags.map { TagDefinition(it) }
        }
        for (incoming in incomingTagDefs) {
            val cleaned = incoming.name.trim()
            if (cleaned.isBlank()) continue
            if (tombstones.containsKey(tagTombstoneKey(cleaned))) continue
            val idx = mergedTags.indexOfFirst { it.name.equals(cleaned, ignoreCase = true) }
            if (idx < 0) {
                mergedTags.add(TagDefinition(cleaned, incoming.description.trim()))
                tagsAdded++
            } else if (mergedTags[idx].description.isBlank() && incoming.description.isNotBlank()) {
                mergedTags[idx] = mergedTags[idx].copy(description = incoming.description.trim())
            }
        }
        saveTagDefinitionsToPrefs(mergedTags)

        // Completed verses: union by (book, chapter, verse); keep the
        // earlier completedAt on collision. A tombstoned key is skipped —
        // CompletedVerseItem has no "was this re-completed after the
        // delete" signal beyond completedAt itself, which restore would
        // just overwrite with an older value anyway, so there's nothing
        // reliable to compare against the tombstone.
        val existingCompleted = _completedVersesFlow.value
        var completedAdded = 0
        val mergedCompleted = existingCompleted.toMutableList()
        for (incoming in data.completed) {
            if (tombstones.containsKey(completedTombstoneKey(incoming.book, incoming.chapter, incoming.verse))) continue
            val idx = mergedCompleted.indexOfFirst {
                it.book == incoming.book && it.chapter == incoming.chapter && it.verse == incoming.verse
            }
            if (idx < 0) {
                mergedCompleted.add(incoming)
                completedAdded++
            } else if (incoming.completedAt < mergedCompleted[idx].completedAt) {
                mergedCompleted[idx] = incoming
            }
        }
        saveCompletedToPrefs(mergedCompleted)

        // Highlights: union by (book, chapter, verse). Now that
        // HighlightItem carries updatedAt, a tombstoned key is skipped
        // unless the incoming updatedAt postdates the deletion (same rule
        // notes use), and a non-tombstoned collision is resolved by
        // whichever copy is newer — not "the backup always wins" as
        // before — so restoring an old backup can't clobber a recolor
        // made locally since.
        val existingHighlights = _highlightsFlow.value
        var highlightsAdded = 0
        val mergedHighlights = existingHighlights.toMutableList()
        for (incoming in data.highlights) {
            val deletedAt = tombstones[highlightTombstoneKey(incoming.book, incoming.chapter, incoming.verse)]
            if (deletedAt != null && incoming.updatedAt <= deletedAt) continue
            val idx = mergedHighlights.indexOfFirst {
                it.book == incoming.book && it.chapter == incoming.chapter && it.verse == incoming.verse
            }
            if (idx < 0) {
                mergedHighlights.add(incoming)
                highlightsAdded++
            } else if (incoming.updatedAt >= mergedHighlights[idx].updatedAt) {
                mergedHighlights[idx] = incoming
            }
        }
        saveHighlightsToPrefs(mergedHighlights)

        // Highlight color defs: unioned by colorHex (a color's identity,
        // not a separate id — see model/HighlightColors.kt). A tombstoned
        // hex is skipped. A matching, non-tombstoned hex with a different
        // label means the same color was renamed on another device; the
        // backup's label wins since restoring is a deliberate action, same
        // tiebreak rule highlights themselves use.
        val existingColorDefs = _highlightColorDefsFlow.value
        var colorsAdded = 0
        var colorsRelabeled = 0
        val mergedColorDefs = existingColorDefs.toMutableList()
        for (incoming in data.highlightColorDefs) {
            if (tombstones.containsKey(colorTombstoneKey(incoming.colorHex))) continue
            val idx = mergedColorDefs.indexOfFirst { it.colorHex.equals(incoming.colorHex, ignoreCase = true) }
            if (idx < 0) {
                mergedColorDefs.add(incoming)
                colorsAdded++
            } else if (!mergedColorDefs[idx].label.equals(incoming.label, ignoreCase = true)) {
                mergedColorDefs[idx] = incoming
                colorsRelabeled++
            }
        }
        saveHighlightColorDefsToPrefs(mergedColorDefs)

        ImportResult(
            notesAdded = notesAdded,
            notesUpdated = notesUpdated,
            tagsAdded = tagsAdded,
            completedAdded = completedAdded,
            highlightsAdded = highlightsAdded,
            colorsAdded = colorsAdded,
            colorsRelabeled = colorsRelabeled
        )
    }

    // Last successful Drive backup/restore timestamps, surfaced in Settings
    // so "Backup to Drive" doesn't feel like it vanished into a void.
    fun getLastDriveBackupAt(): Long = prefs.getLong("last_drive_backup_at", 0L)
    fun setLastDriveBackupAt(millis: Long) = prefs.edit().putLong("last_drive_backup_at", millis).apply()

    fun getLastDriveRestoreAt(): Long = prefs.getLong("last_drive_restore_at", 0L)
    fun setLastDriveRestoreAt(millis: Long) = prefs.edit().putLong("last_drive_restore_at", millis).apply()

    // Whether the daily background Drive sync (DriveSyncWorker) should be
    // scheduled. Off by default — the user opts in from Settings, since
    // this uploads data on a schedule even when they're not in the app.
    fun getAutoBackupEnabled(): Boolean = prefs.getBoolean("auto_backup_enabled", false)
    fun setAutoBackupEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
}
