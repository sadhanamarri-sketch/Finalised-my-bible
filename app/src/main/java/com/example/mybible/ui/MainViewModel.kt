package com.example.mybible.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.mybible.HighlightedVerseItem
import com.example.mybible.StudyStats
import com.example.mybible.StudySummary
import com.example.mybible.buildHighlightedVerseItems
import com.example.mybible.data.BibleRepository
import com.example.mybible.data.DriveBackupManager
import com.example.mybible.data.DriveSyncWorker
import com.example.mybible.data.resolveBookName
import com.example.mybible.data.LexiconLookupResult
import com.example.mybible.model.*
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BOOK_CHAPTER_COUNTS
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NavTab {
    READER, STUDIED, NOTES, SEARCH, SETTINGS, HIGHLIGHTS
}

// Mirrors Capacitor's pickingMode (notes) and selectMode (studied) — a
// banner overlay on the Reader that changes what tapping a verse does.
// Only one can be active at a time.
enum class ReaderPickMode {
    NONE, NOTE_PICK, STUDY_PICK
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BibleRepository(application)
    private val appContext: Application = application
    private val driveBackupManager = DriveBackupManager(application)

    // Active Navigation Tab
    private val _activeTab = MutableStateFlow(NavTab.READER)
    val activeTab: StateFlow<NavTab> = _activeTab.asStateFlow()

    // Current Bible Position
    private val _currentBook = MutableStateFlow("Genesis")
    val currentBook: StateFlow<String> = _currentBook.asStateFlow()

    private val _currentChapter = MutableStateFlow(1)
    val currentChapter: StateFlow<Int> = _currentChapter.asStateFlow()

    private val _verses = MutableStateFlow<List<Verse>>(emptyList())
    val verses: StateFlow<List<Verse>> = _verses.asStateFlow()

    // Which verse numbers in the currently-loaded chapter have at least one
    // cross-reference — loaded once per chapter alongside the verses
    // themselves, so VerseCard can render the dagger marker without a
    // per-verse query. Empty while a chapter is loading/import hasn't
    // reached this chapter's data yet, same as verses defaulting empty.
    private val _verseNumbersWithXrefs = MutableStateFlow<Set<Int>>(emptySet())
    val verseNumbersWithXrefs: StateFlow<Set<Int>> = _verseNumbersWithXrefs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Settings / Preferences
    private val _themeMode = MutableStateFlow(repository.getSavedTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _redLetterEnabled = MutableStateFlow(repository.getRedLetterEnabled())
    val redLetterEnabled: StateFlow<Boolean> = _redLetterEnabled.asStateFlow()

    private val _showTeluguInline = MutableStateFlow(true)
    val showTeluguInline: StateFlow<Boolean> = _showTeluguInline.asStateFlow()

    private val _showInterlinear = MutableStateFlow(false)
    val showInterlinear: StateFlow<Boolean> = _showInterlinear.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(repository.getSavedFontSize())
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    // Independent Telugu and Greek text sizes — previously Telugu was just
    // (English size - 2) and Greek was hardcoded at 14sp, neither
    // adjustable on their own like the Capacitor app's three separate
    // sliders (--reading-size / --telugu-size / --greek-size).
    private val _teluguFontSizeSp = MutableStateFlow(repository.getSavedTeluguFontSize())
    val teluguFontSizeSp: StateFlow<Int> = _teluguFontSizeSp.asStateFlow()

    private val _greekFontSizeSp = MutableStateFlow(repository.getSavedGreekFontSize())
    val greekFontSizeSp: StateFlow<Int> = _greekFontSizeSp.asStateFlow()

    private val _hebrewFontSizeSp = MutableStateFlow(repository.getSavedHebrewFontSize())
    val hebrewFontSizeSp: StateFlow<Int> = _hebrewFontSizeSp.asStateFlow()

    // Capacitor's line-height is fixed (1.85), never user-adjustable — what
    // IS adjustable there is the padding between verse blocks
    // ("--verse-spacing", 6-28px, default 14, step 2). This used to be
    // mislabeled/mis-implemented as an adjustable line-height instead.
    private val _verseSpacingDp = MutableStateFlow(repository.getSavedVerseSpacing())
    val verseSpacingDp: StateFlow<Int> = _verseSpacingDp.asStateFlow()

    // Line spacing (line-height as a multiplier of font size) — a new
    // control not present in Capacitor, added on request. Independently
    // adjustable for English and Telugu since the two scripts can want
    // different breathing room. Defaults to Capacitor's fixed 1.85 ratio.
    private val _englishLineHeightMultiplier = MutableStateFlow(repository.getSavedEnglishLineHeight())
    val englishLineHeightMultiplier: StateFlow<Float> = _englishLineHeightMultiplier.asStateFlow()

    private val _teluguLineHeightMultiplier = MutableStateFlow(repository.getSavedTeluguLineHeight())
    val teluguLineHeightMultiplier: StateFlow<Float> = _teluguLineHeightMultiplier.asStateFlow()

    private val _englishFontFamilyName = MutableStateFlow(repository.getSavedEnglishFont())
    val englishFontFamilyName: StateFlow<String> = _englishFontFamilyName.asStateFlow()

    // Blur Mode state
    private val _isBlurModeEnabled = MutableStateFlow(false)
    val isBlurModeEnabled: StateFlow<Boolean> = _isBlurModeEnabled.asStateFlow()

    // Cross Reference Navigation & Focused Verse
    private val _xrefHistory = MutableStateFlow<List<Position>>(emptyList())
    val xrefHistory: StateFlow<List<Position>> = _xrefHistory.asStateFlow()

    private val _focusedVerseNumber = MutableStateFlow<Int?>(null)
    val focusedVerseNumber: StateFlow<Int?> = _focusedVerseNumber.asStateFlow()

    // Whether landing on focusedVerseNumber should also trigger the
    // temporary "blur the rest of the chapter" effect, vs just scrolling to
    // that verse and leaving the chapter reading normally. True for
    // cross-reference jumps and other "spotlight this exact verse" entry
    // points; false for browse-style entry points (see jumpToVerse).
    private val _focusedVerseBlurEnabled = MutableStateFlow(true)
    val focusedVerseBlurEnabled: StateFlow<Boolean> = _focusedVerseBlurEnabled.asStateFlow()

    // English Dictionary Lookup state
    private val _selectedEnglishWord = MutableStateFlow<String?>(null)
    val selectedEnglishWord: StateFlow<String?> = _selectedEnglishWord.asStateFlow()

    private val _dictionaryEntry = MutableStateFlow<EnglishDictionaryEntry?>(null)
    val dictionaryEntry: StateFlow<EnglishDictionaryEntry?> = _dictionaryEntry.asStateFlow()

    private val _isLoadingDictionary = MutableStateFlow(false)
    val isLoadingDictionary: StateFlow<Boolean> = _isLoadingDictionary.asStateFlow()

    // Room DB Observables
    // completedVerses is a StateFlow (not a plain Flow) so pick-mode logic
    // below can read .value synchronously when a verse is tapped, the same
    // way Capacitor's toggleSelectVerse() reads the in-memory `completed`
    // array directly rather than awaiting a fresh emission.
    val completedVerses: StateFlow<List<CompletedVerseItem>> =
        repository.allCompletedVerses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notes = repository.allNotes
    val tagDefinitions = repository.allTagDefinitions
    val highlights = repository.allHighlights
    val highlightColorDefs = repository.allHighlightColorDefs

    // Feeds HighlightedVersesScreen (the "Highlighted Verses" browser tab):
    // joins the two flows above against each verse's text. Recomputes
    // whenever either the highlight set or the color labels change; verse
    // text lookups are cheap single-row Room queries, not worth caching
    // separately given highlight counts are small relative to the full text.
    val highlightedVerseItems: StateFlow<List<HighlightedVerseItem>> =
        combine(repository.allHighlights, repository.allHighlightColorDefs) { highlightList, colorDefs ->
            buildHighlightedVerseItems(highlightList, colorDefs) { book, chapter, verse ->
                repository.getVerseText(book, chapter, verse)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Used by HighlightedVersesScreen when the user taps a result: jump the
    // Reader to that verse and switch back to it, same as tapping a Studied
    // or Search result does.
    fun openHighlightedVerse(item: HighlightedVerseItem) {
        jumpToVerse(item.book, item.chapter, item.verse)
        // Same reasoning as navigateToCrossReference — you tapped this to
        // read the verse, blur mode would hide the very thing you came for.
        _isBlurModeEnabled.value = false
        selectTab(NavTab.READER)
    }

    // Interactive UI overlays & actions
    private val _selectedVerse = MutableStateFlow<Verse?>(null)
    val selectedVerse: StateFlow<Verse?> = _selectedVerse.asStateFlow()

    private val _selectedGreekWord = MutableStateFlow<GreekWord?>(null)
    val selectedGreekWord: StateFlow<GreekWord?> = _selectedGreekWord.asStateFlow()

    private val _selectedHebrewWord = MutableStateFlow<HebrewWord?>(null)
    val selectedHebrewWord: StateFlow<HebrewWord?> = _selectedHebrewWord.asStateFlow()

    // Greek Lexicon (TBESG) Lookup state — the sheet's inline gloss comes
    // straight off selectedGreekWord above; this is the fuller entry fetched
    // lazily once the sheet opens, mirroring Capacitor's openGreekWordSheet().
    private val _lexiconResult = MutableStateFlow<LexiconLookupResult?>(null)
    val lexiconResult: StateFlow<LexiconLookupResult?> = _lexiconResult.asStateFlow()

    private val _isLoadingLexicon = MutableStateFlow(false)
    val isLoadingLexicon: StateFlow<Boolean> = _isLoadingLexicon.asStateFlow()

    private var lexiconLookupJob: Job? = null

    // Hebrew lexicon lookup state — separate StateFlows from the Greek ones
    // above (not shared) so opening a Hebrew word sheet can't flash stale
    // Greek lexicon content, and vice versa, if one sheet opens right after
    // the other closes.
    private val _hebrewLexiconResult = MutableStateFlow<LexiconLookupResult?>(null)
    val hebrewLexiconResult: StateFlow<LexiconLookupResult?> = _hebrewLexiconResult.asStateFlow()

    private val _isLoadingHebrewLexicon = MutableStateFlow(false)
    val isLoadingHebrewLexicon: StateFlow<Boolean> = _isLoadingHebrewLexicon.asStateFlow()

    private var hebrewLexiconLookupJob: Job? = null

    private val _selectedCrossReferences = MutableStateFlow<List<CrossReferenceItem>?>(null)
    val selectedCrossReferences: StateFlow<List<CrossReferenceItem>?> = _selectedCrossReferences.asStateFlow()

    // The verse whose cross-references are currently open — the source verse
    // for the "return to" breadcrumb in navigateToCrossReference(). Tracked
    // separately from _selectedVerse since opening xrefs via the dagger
    // marker never selects the verse.
    private val _crossReferenceSourceVerse = MutableStateFlow<Verse?>(null)

    // Verse-mention preview sheet — opened by tapping a linkified reference
    // inside note text (see data/VerseMentionLinkifier.kt). Matches
    // Capacitor's openVerseTextSheet/closeVerseTextSheet: shows the
    // reference + verse text without leaving the note; "Open in Reader" is
    // an explicit separate action.
    data class VerseMentionPreview(val book: String, val chapter: Int, val verse: Int, val text: String?)

    private val _verseMentionPreview = MutableStateFlow<VerseMentionPreview?>(null)
    val verseMentionPreview: StateFlow<VerseMentionPreview?> = _verseMentionPreview.asStateFlow()

    fun openVerseMentionPreview(book: String, chapter: Int, verse: Int) {
        _verseMentionPreview.value = VerseMentionPreview(book, chapter, verse, null)
        viewModelScope.launch {
            val text = repository.getVerseText(book, chapter, verse)
            // bail if the sheet moved on to a different reference while
            // this was loading, or was dismissed — same guard as
            // Capacitor's guard against a stale ref completing after the
            // sheet has already moved on or been dismissed.
            val current = _verseMentionPreview.value
            if (current != null && current.book == book && current.chapter == chapter && current.verse == verse) {
                _verseMentionPreview.value = current.copy(text = text ?: "This verse's text isn't available offline right now.")
            }
        }
    }

    fun closeVerseMentionPreview() {
        _verseMentionPreview.value = null
    }

    private val _showBookPicker = MutableStateFlow(false)
    val showBookPicker: StateFlow<Boolean> = _showBookPicker.asStateFlow()

    private val _showNoteEditor = MutableStateFlow(false)
    val showNoteEditor: StateFlow<Boolean> = _showNoteEditor.asStateFlow()

    private val _noteToEdit = MutableStateFlow<NoteItem?>(null)
    val noteToEdit: StateFlow<NoteItem?> = _noteToEdit.asStateFlow()

    private val _noteToRead = MutableStateFlow<NoteItem?>(null)
    val noteToRead: StateFlow<NoteItem?> = _noteToRead.asStateFlow()

    private val _showOnboarding = MutableStateFlow(repository.isFirstLaunch())
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Verse>>(emptyList())
    val searchResults: StateFlow<List<Verse>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchCaseSensitive = MutableStateFlow(false)
    val searchCaseSensitive: StateFlow<Boolean> = _searchCaseSensitive.asStateFlow()

    // Scroll position of the search results list, saved/restored across tab
    // switches since SearchScreen is fully disposed (not just hidden) when
    // the user navigates away — see MainActivity's `when (activeTab)`.
    private val _searchScrollIndex = MutableStateFlow(0)
    val searchScrollIndex: StateFlow<Int> = _searchScrollIndex.asStateFlow()

    private val _searchScrollOffset = MutableStateFlow(0)
    val searchScrollOffset: StateFlow<Int> = _searchScrollOffset.asStateFlow()

    fun saveSearchScrollPosition(index: Int, offset: Int) {
        _searchScrollIndex.value = index
        _searchScrollOffset.value = offset
    }

    // Whether a "return to search results" banner should be showing in the
    // Reader — set when a search result is tapped, cleared when the user
    // either returns to Search via the banner or explicitly dismisses it.
    private val _searchReturnAvailable = MutableStateFlow(false)
    val searchReturnAvailable: StateFlow<Boolean> = _searchReturnAvailable.asStateFlow()

    // Timer state
    private val _totalStudyTimeMs = MutableStateFlow(repository.getTotalStudyTimeMs())
    val totalStudyTimeMs: StateFlow<Long> = _totalStudyTimeMs.asStateFlow()

    private var timerJob: Job? = null

    // Real consecutive-day streak (StudyStats.kt) rather than a flat
    // "0 or 1" placeholder. completedAt is stored as epoch millis; StudyStats
    // works in yyyy-MM-dd device-local day strings, so convert here at the
    // one call site rather than changing the stored model.
    private val studyDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val studySummary: StateFlow<StudySummary> =
        repository.allCompletedVerses
            .map { list -> StudyStats.summary(list.map { studyDateFormat.format(Date(it.completedAt)) }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudySummary(0, 0, 0, 0))

    // Bible data import progress (KJV download + Telugu asset load) — see BibleDataInitializer.
    val importProgress = repository.dataInitializer.progress
    val importError = repository.dataInitializer.errorMessage

    // Total verse counts for the Studied tab's OT/NT progress bars.
    // Computed once the Bible data import finishes (see init below) rather
    // than hardcoded, so it's correct even if versification data changes.
    // Default to 0 (progress bars just render empty) until then.
    private val _otTotalVerses = MutableStateFlow(0)
    val otTotalVerses: StateFlow<Int> = _otTotalVerses.asStateFlow()

    private val _ntTotalVerses = MutableStateFlow(0)
    val ntTotalVerses: StateFlow<Int> = _ntTotalVerses.asStateFlow()

    init {
        val (savedBook, savedChap) = repository.getLastPosition()
        _currentBook.value = savedBook
        _currentChapter.value = savedChap
        loadCurrentChapter()

        // Study time should only accrue while the app is actually in the
        // foreground — previously it started once here and never stopped,
        // so it counted process-alive time (including backgrounded) rather
        // than time spent reading. ProcessLifecycleOwner gives us one
        // app-wide foreground/background signal without wiring per-Activity
        // lifecycle callbacks through the ViewModel.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = startStudyTimer()
            override fun onStop(owner: LifecycleOwner) = stopStudyTimer()
        })

        viewModelScope.launch {
            repository.dataInitializer.ensureImported()
            // Once the import finishes, refresh whatever chapter is on screen
            // so it picks up Room data (Telugu text, etc.) instead of
            // whatever the live-fetch fallback returned while it was running.
            loadCurrentChapter()

            val otBooks = BIBLE_BOOKS.take(BIBLE_BOOKS.indexOf("Matthew"))
            val ntBooks = BIBLE_BOOKS.drop(BIBLE_BOOKS.indexOf("Matthew"))
            _otTotalVerses.value = repository.getVerseCountForBooks(otBooks)
            _ntTotalVerses.value = repository.getVerseCountForBooks(ntBooks)
        }
    }

    fun retryBibleDataImport() {
        viewModelScope.launch {
            repository.dataInitializer.retry()
            loadCurrentChapter()
        }
    }

    private fun startStudyTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                repository.addStudyTimeMs(5000)
                _totalStudyTimeMs.value = repository.getTotalStudyTimeMs()
            }
        }
    }

    private fun stopStudyTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun selectTab(tab: NavTab) {
        _activeTab.value = tab
    }

    fun loadChapter(book: String, chapter: Int) {
        _currentBook.value = book
        _currentChapter.value = chapter
        repository.saveLastPosition(book, chapter)
        loadCurrentChapter()
        refreshContinueReadingWidget()
    }

    // The home screen widget's "Continue reading" row reads last_book/
    // last_chapter out of SharedPreferences (see saveLastPosition above)
    // each time Android calls its provideGlance(), but nothing was ever
    // asking Android to call that *now* — the manifest's updatePeriodMillis
    // (see verse_of_day_widget_info.xml) is a 24-hour periodic refresh, so
    // without this the widget only picked up a new chapter on its own
    // schedule, sometimes closer to a day later than "next time you switch
    // chapters." updateAll() asks Glance to re-run provideGlance()
    // immediately; fire-and-forget on viewModelScope since it's a suspend
    // call and loadChapter isn't.
    private fun refreshContinueReadingWidget() {
        viewModelScope.launch {
            try {
                com.example.mybible.widget.VerseOfDayWidget().updateAll(appContext)
                com.example.mybible.widget.ContinueReadingWidget().updateAll(appContext)
                Log.d(
                    "ContinueReadingWidget",
                    "updateAll() succeeded for ${_currentBook.value} ${_currentChapter.value}"
                )
            } catch (e: Exception) {
                // Was previously a fully silent catch — a real failure here
                // (e.g. a Glance composition error, or no widget currently
                // placed on any home screen) looked identical to "nothing
                // went wrong" from Logcat, which made the "still shows
                // previous chapter after swipe" bug unreportable. Logging
                // it doesn't change behavior (a refresh failure still must
                // never affect reading/navigation) but makes a genuine
                // failure visible instead of indistinguishable from a
                // stale-read/backgrounding race.
                Log.w("ContinueReadingWidget", "updateAll() failed", e)
            }
        }
    }

    // Used by the picker's verse grid: jumping straight to a verse (rather
    // than a cross-reference jump) is fresh navigation, so it clears any
    // prior xref-jump breadcrumb the same way Capacitor's verse-grid button
    // handlers call clearXrefHistory() right before loadChapter().
    //
    // focusVerse controls the temporary "spotlight this verse, blur the
    // rest" effect (see focusedVerseNumber / ReaderScreen's xrefFocusActive).
    // That's the right effect for "jump straight to verse X" entry points
    // (the main Book/Chapter/Verse picker, cross-reference jumps, tapping a
    // search/highlight/studied result) where the point is to land the user
    // on one specific verse. It's wrong for browse-style entry points like
    // the Studied tab's chapter verse grid, where the user is just opening
    // a chapter to review it — pass focusVerse = false there so the whole
    // chapter reads normally instead of blurring around one cell.
    fun jumpToVerse(book: String, chapter: Int, verse: Int, focusVerse: Boolean = true) {
        clearXrefHistory()
        _focusedVerseNumber.value = verse
        _focusedVerseBlurEnabled.value = focusVerse
        loadChapter(book, chapter)
    }

    // Verse count for the picker's verse grid — see
    // BibleRepository.getVerseCount for why this isn't a plain Room count.
    suspend fun getVerseCount(book: String, chapter: Int): Int = repository.getVerseCount(book, chapter)

    suspend fun getVerseText(book: String, chapter: Int, verse: Int): String? =
        repository.getVerseText(book, chapter, verse)

    private fun loadCurrentChapter() {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedVerse.value = null
            _selectedCrossReferences.value = null
            _crossReferenceSourceVerse.value = null
            
            val bookName = _currentBook.value
            val chap = _currentChapter.value
            val result = repository.getChapterVerses(bookName, chap, includeTelugu = _showTeluguInline.value)
            _verses.value = result
            _verseNumbersWithXrefs.value = repository.getCrossReferenceVerseNumbers(bookName, chap)
            _isLoading.value = false
        }
    }

    // Swiping chapters is plain forward/backward reading, not a "jump to a
    // specific verse" action — so any leftover spotlight/blur target from
    // an earlier cross-reference jump, search result, or highlighted-verse
    // tap needs to be cleared here too (same reasoning as the pick-mode
    // entry points above). Without this, swiping could silently inherit a
    // stale focusedVerseNumber: the new chapter would land scrolled to that
    // old verse instead of the top, with it spotlighted and the rest blurred.
    fun nextChapter() {
        clearVerseFocus()
        val totalChapters = BOOK_CHAPTER_COUNTS[_currentBook.value] ?: 1
        if (_currentChapter.value < totalChapters) {
            loadChapter(_currentBook.value, _currentChapter.value + 1)
        } else {
            val bookIndex = BIBLE_BOOKS.indexOf(_currentBook.value)
            if (bookIndex != -1 && bookIndex < BIBLE_BOOKS.lastIndex) {
                val nextBook = BIBLE_BOOKS[bookIndex + 1]
                loadChapter(nextBook, 1)
            }
        }
    }

    fun prevChapter() {
        clearVerseFocus()
        if (_currentChapter.value > 1) {
            loadChapter(_currentBook.value, _currentChapter.value - 1)
        } else {
            val bookIndex = BIBLE_BOOKS.indexOf(_currentBook.value)
            if (bookIndex > 0) {
                val prevBook = BIBLE_BOOKS[bookIndex - 1]
                val prevChapterCount = BOOK_CHAPTER_COUNTS[prevBook] ?: 1
                loadChapter(prevBook, prevChapterCount)
            }
        }
    }

    fun setTheme(theme: ThemeMode) {
        _themeMode.value = theme
        repository.saveTheme(theme)
    }

    fun setRedLetterEnabled(enabled: Boolean) {
        _redLetterEnabled.value = enabled
        repository.setRedLetterEnabled(enabled)
    }

    fun toggleTeluguInline() {
        _showTeluguInline.value = !_showTeluguInline.value
        loadCurrentChapter()
    }

    fun toggleInterlinear() {
        _showInterlinear.value = !_showInterlinear.value
    }

    fun toggleBlurMode() {
        _isBlurModeEnabled.value = !_isBlurModeEnabled.value
    }

    // Forces blur mode off — for deliberate "go read this specific verse"
    // navigation that doesn't already go through one of the wrapper
    // functions above (openSearchResult, openHighlightedVerse, the note-
    // picking/study-picking starters, navigateToCrossReference): the
    // Book/Chapter picker, tapping a reference inside a saved note or a
    // linkified verse mention, and browsing a studied verse/group. Same
    // reasoning throughout — you navigated here to read something specific,
    // blur mode would hide the very thing you came for.
    fun disableBlurModeForNavigation() {
        _isBlurModeEnabled.value = false
    }

    // Reading reminders. Actually turning notifications ON also needs the
    // POST_NOTIFICATIONS runtime permission on Android 13+, which requires
    // an Activity — that's requested by MainActivity, which calls this only
    // once permission is granted (or immediately, on older Android where no
    // such permission exists).
    private val _remindersEnabled = MutableStateFlow(
        com.example.mybible.reminders.ReminderScheduler.isEnabled(appContext)
    )
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    fun setRemindersEnabled(enabled: Boolean) {
        com.example.mybible.reminders.ReminderScheduler.setEnabled(appContext, enabled)
        _remindersEnabled.value = enabled
    }

    fun adjustFontSize(deltaSp: Int) {
        _fontSizeSp.value = (_fontSizeSp.value + deltaSp).coerceIn(15, 26)
        repository.saveFontSize(_fontSizeSp.value)
    }

    fun adjustTeluguFontSize(deltaSp: Int) {
        _teluguFontSizeSp.value = (_teluguFontSizeSp.value + deltaSp).coerceIn(12, 24)
        repository.saveTeluguFontSize(_teluguFontSizeSp.value)
    }

    fun adjustHebrewFontSize(deltaSp: Int) {
        _hebrewFontSizeSp.value = (_hebrewFontSizeSp.value + deltaSp).coerceIn(11, 22)
        repository.saveHebrewFontSize(_hebrewFontSizeSp.value)
    }

    fun adjustGreekFontSize(deltaSp: Int) {
        _greekFontSizeSp.value = (_greekFontSizeSp.value + deltaSp).coerceIn(11, 22)
        repository.saveGreekFontSize(_greekFontSizeSp.value)
    }

    fun adjustVerseSpacing(deltaDp: Int) {
        _verseSpacingDp.value = (_verseSpacingDp.value + deltaDp).coerceIn(6, 28)
        repository.saveVerseSpacing(_verseSpacingDp.value)
    }

    fun adjustEnglishLineHeight(delta: Float) {
        _englishLineHeightMultiplier.value = ((_englishLineHeightMultiplier.value + delta)
            .coerceIn(1.2f, 2.6f) * 100f).let { kotlin.math.round(it) / 100f }
        repository.saveEnglishLineHeight(_englishLineHeightMultiplier.value)
    }

    fun adjustTeluguLineHeight(delta: Float) {
        _teluguLineHeightMultiplier.value = ((_teluguLineHeightMultiplier.value + delta)
            .coerceIn(1.2f, 2.6f) * 100f).let { kotlin.math.round(it) / 100f }
        repository.saveTeluguLineHeight(_teluguLineHeightMultiplier.value)
    }

    fun setEnglishFontFamilyName(fontName: String) {
        _englishFontFamilyName.value = fontName
        repository.saveEnglishFont(fontName)
    }

    fun navigateToCrossReference(targetBook: String, targetChapter: Int, targetVerse: Int) {
        val sourceVerse = _crossReferenceSourceVerse.value
        val currentPos = Position(
            book = sourceVerse?.book ?: _currentBook.value,
            chapter = sourceVerse?.chapter ?: _currentChapter.value,
            verse = sourceVerse?.number ?: _selectedVerse.value?.number ?: 1
        )
        _xrefHistory.value = _xrefHistory.value + currentPos
        _focusedVerseNumber.value = targetVerse
        _focusedVerseBlurEnabled.value = true
        // Blur mode (privacy blur, the pill toggle) would defeat the whole
        // point of following a cross-reference — you followed it to read
        // the target verse, not stare at an obscured one.
        _isBlurModeEnabled.value = false
        dismissCrossReferences()
        loadChapter(targetBook, targetChapter)
    }

    fun goBackCrossReference() {
        val history = _xrefHistory.value
        if (history.isNotEmpty()) {
            val last = history.last()
            _xrefHistory.value = history.dropLast(1)
            _focusedVerseNumber.value = last.verse
            _focusedVerseBlurEnabled.value = true
            _isBlurModeEnabled.value = false
            loadChapter(last.book, last.chapter)
        }
    }

    fun clearXrefHistory() {
        _xrefHistory.value = emptyList()
        _focusedVerseNumber.value = null
    }

    // Clears just the "spotlight this verse, blur the rest" target, without
    // touching xref breadcrumb history. Called when entering any pick mode
    // (Study or Note), since pick mode is a distinct interaction that
    // should always show the chapter normally — never inheriting a stale
    // focus/blur left over from an earlier cross-reference jump, search
    // result, or highlighted-verse tap that happened to land on the same
    // chapter.
    private fun clearVerseFocus() {
        _focusedVerseNumber.value = null
    }

    fun openEnglishWordLookup(word: String) {
        val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")
        if (cleanWord.isEmpty()) return
        _selectedEnglishWord.value = cleanWord
        viewModelScope.launch {
            _isLoadingDictionary.value = true
            val entry = repository.lookupEnglishWord(cleanWord)
            _dictionaryEntry.value = entry
            _isLoadingDictionary.value = false
        }
    }

    fun dismissEnglishWordSheet() {
        _selectedEnglishWord.value = null
        _dictionaryEntry.value = null
        _isLoadingDictionary.value = false
    }

    fun setSelectedVerse(verse: Verse?) {
        _selectedVerse.value = verse
    }

    fun selectGreekWord(greekWord: GreekWord?) {
        _selectedGreekWord.value = greekWord
        lexiconLookupJob?.cancel()
        _lexiconResult.value = null
        _isLoadingLexicon.value = false
        if (greekWord == null) return

        _isLoadingLexicon.value = true
        lexiconLookupJob = viewModelScope.launch {
            val result = repository.getLexiconEntry(greekWord.strongs)
            _lexiconResult.value = result
            _isLoadingLexicon.value = false
        }
    }

    fun selectHebrewWord(hebrewWord: HebrewWord?) {
        _selectedHebrewWord.value = hebrewWord
        hebrewLexiconLookupJob?.cancel()
        _hebrewLexiconResult.value = null
        _isLoadingHebrewLexicon.value = false
        if (hebrewWord == null) return

        _isLoadingHebrewLexicon.value = true
        hebrewLexiconLookupJob = viewModelScope.launch {
            val result = repository.getHebrewLexiconEntry(hebrewWord.strongs)
            _hebrewLexiconResult.value = result
            _isLoadingHebrewLexicon.value = false
        }
    }

    fun openCrossReferences(verse: Verse) {
        // Remember the verse the xrefs were opened *from*, independent of
        // _selectedVerse — the dagger marker (onCrossReferenceMarkerClick)
        // opens xrefs without ever selecting the verse, so relying on
        // _selectedVerse here caused navigateToCrossReference() to fall back
        // to verse 1 for the "return to" breadcrumb.
        _crossReferenceSourceVerse.value = verse
        viewModelScope.launch {
            val xrefs = repository.getCrossReferences(verse.book, verse.chapter, verse.number)
            _selectedCrossReferences.value = xrefs
        }
    }

    fun dismissCrossReferences() {
        _selectedCrossReferences.value = null
        _crossReferenceSourceVerse.value = null
    }

    fun toggleCompletedVerse(book: String, chapter: Int, verse: Int) {
        viewModelScope.launch {
            repository.toggleCompletedVerse(book, chapter, verse)
        }
    }

    // Used by the Studied screen's multi-select mode (long-press to enter,
    // tap to select, then remove) — removes rather than toggles, since a
    // bulk action should never re-add an already-removed verse.
    fun removeCompletedVerses(items: List<CompletedVerseItem>) {
        viewModelScope.launch {
            items.forEach { repository.removeCompletedVerse(it.book, it.chapter, it.verse) }
        }
    }

    fun setHighlightColor(book: String, chapter: Int, verse: Int, colorHex: String) {
        viewModelScope.launch {
            if (colorHex.isEmpty()) {
                repository.removeHighlight(book, chapter, verse)
            } else {
                repository.setHighlight(book, chapter, verse, colorHex)
            }
            _selectedVerse.value = null
        }
    }

    // ---- Highlight color management (labeled colors, "option b" preset
    // palette instead of a free-form picker — see model/HighlightColors.kt) ----

    private val _showManageHighlightColors = MutableStateFlow(false)
    val showManageHighlightColors: StateFlow<Boolean> = _showManageHighlightColors.asStateFlow()

    fun setShowManageHighlightColors(show: Boolean) {
        _showManageHighlightColors.value = show
    }

    fun addHighlightColor(label: String, colorHex: String) {
        viewModelScope.launch { repository.addHighlightColor(label, colorHex) }
    }

    fun renameHighlightColor(colorHex: String, newLabel: String) {
        viewModelScope.launch { repository.renameHighlightColor(colorHex, newLabel) }
    }

    fun setHighlightColorEnabled(colorHex: String, enabled: Boolean) {
        viewModelScope.launch { repository.setHighlightColorEnabled(colorHex, enabled) }
    }

    fun recolorHighlightColor(oldHex: String, newHex: String) {
        viewModelScope.launch { repository.recolorHighlightColor(oldHex, newHex) }
    }

    fun deleteHighlightColor(colorHex: String) {
        viewModelScope.launch { repository.deleteHighlightColor(colorHex) }
    }

    fun openNoteReader(note: NoteItem) {
        _noteToRead.value = note
    }

    fun closeNoteReader() {
        _noteToRead.value = null
    }

    // Opens the editor directly for a single known verse (from the verse
    // action toolbar's "Add note" / "Add another note") or an existing note
    // (edit). Mirrors Capacitor's startNewNote(b,c,v) / openNoteEditor(note).
    // There is deliberately no "no-args" fallback that guesses a verse from
    // current reader position — that was the source of the Genesis 1:1
    // default bug. Starting a note with no specific verse in mind now always
    // goes through startNotePicking() below, matching Capacitor's
    // startNewNoteFromScratch().
    fun openNoteEditor(note: NoteItem? = null, defaultVerse: Verse? = null) {
        if (note != null) {
            _noteToEdit.value = note
        } else if (defaultVerse != null) {
            _noteToEdit.value = NoteItem(
                id = 0,
                book = defaultVerse.book,
                chapter = defaultVerse.chapter,
                verse = defaultVerse.number,
                verseText = defaultVerse.text,
                text = "",
                refs = listOf(NoteReference(defaultVerse.book, defaultVerse.chapter, defaultVerse.number, defaultVerse.text)),
                tags = emptyList()
            )
        }
        _showNoteEditor.value = true
    }

    fun closeNoteEditor() {
        _showNoteEditor.value = false
        _noteToEdit.value = null
    }

    // ---- Reader "picking mode" — the banner overlay that lets the user tap
    // verses directly in the Reader instead of guessing one. Two independent
    // uses, mirroring Capacitor's pickingMode (notes) and selectMode
    // (studied), which share the same banner-over-Reader mechanic but never
    // run at the same time. ----

    private val _readerPickMode = MutableStateFlow(ReaderPickMode.NONE)
    val readerPickMode: StateFlow<ReaderPickMode> = _readerPickMode.asStateFlow()

    private val _pickBannerMessage = MutableStateFlow("")
    val pickBannerMessage: StateFlow<String> = _pickBannerMessage.asStateFlow()

    // Verses tapped so far in the current note-picking session. Held
    // separately from _noteToEdit so Cancel can discard them without
    // touching a draft that's being resumed (see pickModeResumesEditor).
    private val _pickedNoteRefs = MutableStateFlow<List<NoteReference>>(emptyList())
    val pickedNoteRefs: StateFlow<List<NoteReference>> = _pickedNoteRefs.asStateFlow()

    // true when picking was entered from "+ Add another verse" inside an
    // already-open draft (editor hides, Reader shows, editor reopens with
    // the same title/text/tags plus the new ref) rather than from the Notes
    // tab's "+" (brand-new blank draft). Mirrors the two different
    // pickingMode entry points in Capacitor: startNewNoteFromScratch() vs
    // the neAddRef click handler.
    private var pickModeResumesEditor = false
    private var pickBannerResetJob: Job? = null

    // Session-only bookkeeping for STUDY_PICK: verses toggled ON during the
    // *current* pick-mode session. Needed because completedVerses is a
    // global DB-backed list — a verse can already be "completed" going into
    // this session (e.g. tapped previously, or long-pressed to start), so
    // checking wasCompleted alone can't tell whether THIS session still has
    // other verses selected. The banner should auto-close (matching
    // Capacitor's single-tap quick-toggle via long-press) only once this
    // session's own selection count returns to zero, not just because any
    // one previously-selected verse got unmarked while others remain.
    private val studyPickSessionVerses = mutableSetOf<Triple<String, Int, Int>>()

    private fun setPickBanner(message: String, revertTo: String) {
        _pickBannerMessage.value = message
        pickBannerResetJob?.cancel()
        pickBannerResetJob = viewModelScope.launch {
            delay(1400)
            _pickBannerMessage.value = revertTo
        }
    }

    // Notes tab "+" — brand-new note, nothing picked yet, editor not shown
    // until Done. Matches startNewNoteFromScratch().
    fun startNotePicking() {
        pickBannerResetJob?.cancel()
        pickModeResumesEditor = false
        _pickedNoteRefs.value = emptyList()
        _showNoteEditor.value = false
        _noteToEdit.value = null
        _pickBannerMessage.value = "Tap verses to add — or tap Done to write without one"
        _readerPickMode.value = ReaderPickMode.NOTE_PICK
        // Picking requires reading verse text to know what you're tapping —
        // blur mode would make that impossible.
        _isBlurModeEnabled.value = false
        clearVerseFocus()
        selectTab(NavTab.READER)
    }

    // "+ Add another verse" inside an in-progress draft. `draft` is the
    // editor's current unsaved state (title/text/date/tags/refs as typed so
    // far) — held onto so it can be restored once picking finishes. Matches
    // the neAddRef click handler, which hides the editor without clearing
    // draftNote.
    fun addAnotherVerseToDraft(draft: NoteItem) {
        pickBannerResetJob?.cancel()
        pickModeResumesEditor = true
        _pickedNoteRefs.value = emptyList()
        _noteToEdit.value = draft
        _showNoteEditor.value = false
        _pickBannerMessage.value = "Adding to note \u2014 tap a verse"
        _readerPickMode.value = ReaderPickMode.NOTE_PICK
        _isBlurModeEnabled.value = false
        clearVerseFocus()
        selectTab(NavTab.READER)
    }

    // Studied tab "+ Select" — mark verses studied by tapping them in the
    // Reader. Matches enterSelectMode().
    fun startStudyPicking() {
        studyPickSessionVerses.clear()
        _pickBannerMessage.value = "Tap verses to mark studied \u2014 tap again to unmark"
        _readerPickMode.value = ReaderPickMode.STUDY_PICK
        _isBlurModeEnabled.value = false
        clearVerseFocus()
        selectTab(NavTab.READER)
    }

    // Long-press on a verse in the Reader while readerPickMode == NONE.
    // Matches Capacitor's pointerdown-timer handler, which fires
    // enterSelectMode() + toggleSelectVerse() together as a single unit —
    // the long-pressed verse is toggled immediately, not left for a
    // follow-up tap. Mirrors onPickModeVerseTap's STUDY_PICK branch
    // (including the auto-close-on-unmark case) rather than calling it,
    // since at this point _readerPickMode hasn't been set to STUDY_PICK yet.
    fun startStudyPickingFromLongPress(verse: Verse) {
        _readerPickMode.value = ReaderPickMode.STUDY_PICK
        _pickBannerMessage.value = "Tap verses to mark studied \u2014 tap again to unmark"
        _isBlurModeEnabled.value = false
        studyPickSessionVerses.clear()
        clearVerseFocus()
        val key = Triple(verse.book, verse.chapter, verse.number)
        val wasCompleted = completedVerses.value.any {
            it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
        }
        toggleCompletedVerse(verse.book, verse.chapter, verse.number)
        if (wasCompleted) {
            // Long-pressed an already-completed verse — nothing left
            // selected this session, so close immediately like a
            // single-verse quick-toggle.
            _readerPickMode.value = ReaderPickMode.NONE
        } else {
            studyPickSessionVerses.add(key)
        }
    }

    // Called by the Reader when a verse is tapped while readerPickMode != NONE.
    fun onPickModeVerseTap(verse: Verse) {
        when (_readerPickMode.value) {
            ReaderPickMode.NOTE_PICK -> {
                val alreadyOnDraft = pickModeResumesEditor &&
                    (_noteToEdit.value?.refs?.any { it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number } == true)
                val alreadyPicked = _pickedNoteRefs.value.any {
                    it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                }
                val label = "${verse.book} ${verse.chapter}:${verse.number}"
                val revertTo = if (pickModeResumesEditor) "Adding to note \u2014 tap a verse"
                    else "Tap verses to add \u2014 or tap Done to write without one"
                when {
                    alreadyPicked -> {
                        // Tapped again this session — unselect it.
                        _pickedNoteRefs.value = _pickedNoteRefs.value.filterNot {
                            it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                        }
                        setPickBanner("Removed $label", revertTo)
                    }
                    alreadyOnDraft -> {
                        // Already part of the draft from before this picking
                        // session (e.g. resumed via "+ Add another verse") —
                        // tapping it again removes it from the draft too.
                        _noteToEdit.value = _noteToEdit.value?.let { draft ->
                            draft.copy(refs = draft.refs.filterNot {
                                it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                            })
                        }
                        setPickBanner("Removed $label", revertTo)
                    }
                    else -> {
                        _pickedNoteRefs.value = _pickedNoteRefs.value +
                            NoteReference(verse.book, verse.chapter, verse.number, verse.text)
                        setPickBanner("Added $label", revertTo)
                    }
                }
            }
            ReaderPickMode.STUDY_PICK -> {
                val key = Triple(verse.book, verse.chapter, verse.number)
                val wasCompleted = completedVerses.value.any {
                    it.book == verse.book && it.chapter == verse.chapter && it.verse == verse.number
                }
                toggleCompletedVerse(verse.book, verse.chapter, verse.number)
                if (wasCompleted) {
                    // Unmarked this verse — remove it from this session's
                    // selection, and only auto-close once none of the
                    // verses picked during this session remain selected
                    // (not just because this one tap happened to unmark).
                    studyPickSessionVerses.remove(key)
                    if (studyPickSessionVerses.isEmpty()) {
                        _readerPickMode.value = ReaderPickMode.NONE
                    }
                } else {
                    studyPickSessionVerses.add(key)
                }
            }
            ReaderPickMode.NONE -> Unit
        }
    }

    // "Done" — for note-picking, commits picked refs and (re)opens the
    // editor. For study-picking, just closes the banner.
    fun finishPicking() {
        pickBannerResetJob?.cancel()
        val mode = _readerPickMode.value
        _readerPickMode.value = ReaderPickMode.NONE
        studyPickSessionVerses.clear()
        if (mode != ReaderPickMode.NOTE_PICK) return

        val picked = _pickedNoteRefs.value
        if (pickModeResumesEditor) {
            val current = _noteToEdit.value
            if (current != null) {
                val merged = current.refs + picked.filter { p ->
                    current.refs.none { it.book == p.book && it.chapter == p.chapter && it.verse == p.verse }
                }
                _noteToEdit.value = current.copy(refs = merged)
            }
        } else {
            _noteToEdit.value = NoteItem(
                id = 0,
                text = "",
                title = "",
                noteDate = todayDateString(),
                refs = picked,
                tags = emptyList()
            )
        }
        _showNoteEditor.value = true
        pickModeResumesEditor = false
        _pickedNoteRefs.value = emptyList()
    }

    // "Cancel"/back — for note-picking started from an in-progress draft,
    // discard whatever was picked this session and restore the editor
    // exactly as it was. For a brand-new draft, or study-picking, just close
    // the banner with nothing committed.
    fun cancelPicking() {
        pickBannerResetJob?.cancel()
        _readerPickMode.value = ReaderPickMode.NONE
        _pickedNoteRefs.value = emptyList()
        studyPickSessionVerses.clear()
        if (pickModeResumesEditor) {
            _showNoteEditor.value = true
        }
        pickModeResumesEditor = false
    }

    private fun todayDateString(): String = studyDateFormat.format(Date())

    fun saveNote(title: String, text: String, noteDate: String, refs: List<NoteReference>, tags: List<String>) {
        val note = _noteToEdit.value ?: return
        viewModelScope.launch {
            val resolvedRefs = refs.map { ref -> repository.resolveNoteReference(ref) }
            repository.saveNote(
                id = note.id,
                title = title,
                text = text,
                noteDate = noteDate,
                refs = resolvedRefs,
                tags = tags
            )
            closeNoteEditor()
            _selectedVerse.value = null
        }
    }

    fun saveNote(text: String, tags: List<String>) {
        val note = _noteToEdit.value ?: return
        saveNote(note.title, text, note.noteDate, note.refs.ifEmpty {
            listOf(NoteReference(note.book, note.chapter, note.verse, note.verseText))
        }, tags)
    }

    fun parseNoteReference(input: String): NoteReference? {
        val match = Regex("^\\s*(.+?)\\s+(\\d+)\\s*:\\s*(\\d+)\\s*$").matchEntire(input) ?: return null
        val book = resolveBookName(match.groupValues[1]) ?: return null
        val chapter = match.groupValues[2].toIntOrNull() ?: return null
        val verse = match.groupValues[3].toIntOrNull() ?: return null
        return NoteReference(book, chapter, verse, "")
    }

    suspend fun resolveNoteReferenceText(ref: NoteReference): NoteReference {
        val text = repository.getVerseText(ref.book, ref.chapter, ref.verse) ?: ""
        return ref.copy(verseText = text)
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun addTag(name: String, description: String = "") { viewModelScope.launch { repository.addTag(name, description) } }
    fun updateTag(oldName: String, newName: String, description: String) {
        viewModelScope.launch { repository.updateTag(oldName, newName, description) }
    }
    fun deleteTag(name: String) { viewModelScope.launch { repository.deleteTag(name) } }

    // ── Tags screen ──────────────────────────────────────────────────────
    // Full page pushed over Notes (opened from its top-bar tag icon),
    // rendered the same "overlay above the active tab" way as the note
    // editor/reader rather than as its own NavTab — back returns to Notes,
    // not Reader.
    private val _showTagsScreen = MutableStateFlow(false)
    val showTagsScreen: StateFlow<Boolean> = _showTagsScreen.asStateFlow()

    fun openTagsScreen() { _showTagsScreen.value = true }
    fun closeTagsScreen() { _showTagsScreen.value = false }

    // Set when a tag row on the Tags screen is tapped; Notes reads this
    // once (via LaunchedEffect) to preset its tag filter, then clears it —
    // a one-shot handoff rather than persistent shared filter state.
    private val _pendingNoteTagFilter = MutableStateFlow<String?>(null)
    val pendingNoteTagFilter: StateFlow<String?> = _pendingNoteTagFilter.asStateFlow()

    fun openNotesFilteredByTag(tagName: String) {
        _pendingNoteTagFilter.value = tagName
        _showTagsScreen.value = false
    }

    fun clearPendingNoteTagFilter() { _pendingNoteTagFilter.value = null }

    fun setShowBookPicker(show: Boolean) {
        _showBookPicker.value = show
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
        repository.setFirstLaunchCompleted()
    }

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        runSearch()
    }

    fun setSearchCaseSensitive(enabled: Boolean) {
        _searchCaseSensitive.value = enabled
        runSearch()
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        val results = repository.searchBible(query, caseSensitive = _searchCaseSensitive.value)
        _searchResults.value = results
        _isSearching.value = false
    }

    private fun runSearch() {
        searchJob?.cancel()
        val query = _searchQuery.value
        if (query.trim().length < 2) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            // Small debounce so fast typing doesn't fire a query per
            // keystroke now that results are uncapped.
            delay(250)
            performSearch(query)
        }
    }

    // Recent search terms, most-recent-first, deduped case-insensitively,
    // capped so the suggestion list stays short. In-memory only (ViewModel-
    // scoped) — survives tab switches and screen rotation but not a process
    // death/app restart.
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private fun addToSearchHistory(query: String) {
        val updated = _searchHistory.value.toMutableList()
        updated.removeAll { it.equals(query, ignoreCase = true) }
        updated.add(0, query)
        _searchHistory.value = updated.take(10)
    }

    fun removeSearchHistoryItem(query: String) {
        _searchHistory.value = _searchHistory.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }

    // Called when the user presses the keyboard's search/return action —
    // just saves the term to history for later suggestions. Deliberately
    // does NOT touch the field text or results; pressing Enter should feel
    // like "confirm what I typed", not "clear what I typed".
    fun commitSearchToHistory() {
        val query = _searchQuery.value.trim()
        if (query.length >= 2) addToSearchHistory(query)
    }

    // "X" button on the field — an explicit, immediate wipe of both the
    // text and any results.
    fun clearSearchInput() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    // Ends the whole search session: field, results, everything reset.
    // Called when the user explicitly leaves Search (its own back button)
    // or dismisses the "Return to search results" banner — both mean "I'm
    // done with this search", so the next time they open Search it should
    // start from a blank box rather than old text with the cursor stuck at
    // position 0. NOT called when merely hopping to a result and back via
    // the banner itself — that's mid-session and should keep the query and
    // results intact for continued browsing.
    fun endSearchSession() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    // Tapping a recent-search suggestion re-runs that exact search and
    // bumps it back to the front of history. Field keeps the term (same
    // "Enter shouldn't clear it" reasoning as commitSearchToHistory).
    fun searchFromHistory(query: String) {
        searchJob?.cancel()
        _searchQuery.value = query
        addToSearchHistory(query)
        viewModelScope.launch { performSearch(query) }
    }

    // Used by the search results list: navigating to a result is a fresh
    // reader position, so reuse jumpToVerse (clears xref history), then flag
    // the "return to search" banner so the user can hop straight back to
    // their results — same scroll position, same query — or dismiss it to
    // just keep reading.
    fun openSearchResult(verse: Verse) {
        _searchReturnAvailable.value = true
        _isBlurModeEnabled.value = false
        jumpToVerse(verse.book, verse.chapter, verse.number)
        selectTab(NavTab.READER)
    }

    // When true, SearchScreen's next composition should NOT auto-focus the
    // search field / pop the keyboard. Set when hopping back via "Return to
    // search results" — the user wants to browse the existing results list,
    // not be dropped straight into retyping the query. Consumed (reset)
    // immediately by SearchScreen once read, so a normal Search-tab entry
    // (e.g. tapping the tab icon fresh) still auto-focuses as before.
    private val _suppressNextSearchAutofocus = MutableStateFlow(false)
    val suppressNextSearchAutofocus: StateFlow<Boolean> = _suppressNextSearchAutofocus.asStateFlow()

    fun consumeSuppressSearchAutofocus() {
        _suppressNextSearchAutofocus.value = false
    }

    fun returnToSearchResults() {
        _searchReturnAvailable.value = false
        _suppressNextSearchAutofocus.value = true
        selectTab(NavTab.SEARCH)
    }

    // "Cancel" on the banner — the user is choosing NOT to resume that
    // search, so end the session (see endSearchSession) rather than just
    // hiding the banner and leaving stale text sitting in Search for next
    // time.
    fun dismissSearchReturnBanner() {
        _searchReturnAvailable.value = false
        endSearchSession()
    }

    // ---- Backup / Restore (local file + Google Drive) ----

    private val _driveAccount = MutableStateFlow(driveBackupManager.getLastSignedInAccount())
    val driveAccount: StateFlow<GoogleSignInAccount?> = _driveAccount.asStateFlow()

    private val _isDriveSyncing = MutableStateFlow(false)
    val isDriveSyncing: StateFlow<Boolean> = _isDriveSyncing.asStateFlow()

    private val _lastDriveBackupAt = MutableStateFlow(repository.getLastDriveBackupAt())
    val lastDriveBackupAt: StateFlow<Long> = _lastDriveBackupAt.asStateFlow()

    private val _lastDriveRestoreAt = MutableStateFlow(repository.getLastDriveRestoreAt())
    val lastDriveRestoreAt: StateFlow<Long> = _lastDriveRestoreAt.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(repository.getAutoBackupEnabled())
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    init {
        // Re-affirm the schedule on every process start (not just when the
        // toggle changes) — WorkManager persists requests across restarts,
        // but this guards against the request ever getting silently
        // dropped (e.g. app data cleared while the pref survives).
        if (_autoBackupEnabled.value) {
            DriveSyncWorker.schedule(appContext)
        }
    }

    /** Turns the daily background Drive sync on/off. No-ops (and turns
     * itself back off) if the user isn't signed in to Drive, since there's
     * nothing for the worker to sync to. */
    fun setAutoBackupEnabled(enabled: Boolean) {
        val actuallyEnabled = enabled && _driveAccount.value != null
        repository.setAutoBackupEnabled(actuallyEnabled)
        _autoBackupEnabled.value = actuallyEnabled
        if (actuallyEnabled) {
            DriveSyncWorker.schedule(appContext)
        } else {
            DriveSyncWorker.cancel(appContext)
        }
    }

    // One-shot status messages for Settings to show as a snackbar/toast —
    // cleared by the UI right after it reads them via consumeBackupStatus().
    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    fun consumeBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    /** Everything the Drive sign-in flow needs to hand the launcher; the
     * Activity calls this to get the [Intent] to launch. */
    fun getDriveSignInIntent(): Intent = driveBackupManager.getSignInIntent()

    fun handleDriveSignInResult(data: Intent?) {
        when (val result = driveBackupManager.signInResultFromIntent(data)) {
            is DriveBackupManager.SignInResult.Success -> {
                _driveAccount.value = result.account
                _backupStatusMessage.value = "Signed in as ${result.account.email ?: "Google account"}"
            }
            is DriveBackupManager.SignInResult.Failure -> {
                _driveAccount.value = null
                // Surfacing the raw status code/message here (rather than a
                // flat "cancelled") is deliberate — a status 10
                // (DEVELOPER_ERROR) means the OAuth client in Google Cloud
                // Console doesn't match this app's package name + signing
                // certificate SHA-1, which looks identical to a user
                // cancelling unless this is visible.
                _backupStatusMessage.value = "Google sign-in failed: ${result.message} (code ${result.statusCode})"
            }
        }
    }

    fun signOutOfDrive() {
        driveBackupManager.signOut {
            _driveAccount.value = null
            if (_autoBackupEnabled.value) {
                setAutoBackupEnabled(false)
            }
            _backupStatusMessage.value = "Signed out of Google Drive"
        }
    }

    /** Exports the full backup and returns it for a SAF `CreateDocument`
     * launcher to write; Settings/MainActivity owns the actual file I/O
     * since the ViewModel shouldn't hold a `Uri`. */
    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    /** Restores from a backup JSON string (read from a SAF `OpenDocument`
     * result by the caller). */
    fun importFromBackupJson(jsonText: String) {
        viewModelScope.launch {
            try {
                val result = repository.importFromBackup(jsonText)
                _backupStatusMessage.value = "Restored: ${result.notesAdded} new notes, " +
                    "${result.notesUpdated} updated, ${result.highlightsAdded} new highlights, " +
                    "${result.completedAdded} new completed verses, ${result.tagsAdded} new tags, " +
                    "${result.colorsAdded} new colors"
            } catch (e: Exception) {
                _backupStatusMessage.value = "Couldn't read that backup file"
            }
        }
    }

    /** Backs up to Google Drive's appdata folder. If Drive needs the user
     * to re-consent to the appdata scope, [onNeedsConsent] receives the
     * intent to launch and the caller should retry after it returns.
     * Requires [driveAccount] to be signed in already.
     *
     * Downloads and merges the existing remote copy into this device
     * *before* uploading, rather than blindly overwriting it. Previously
     * this just re-uploaded whatever was on the current device — so if
     * device B had notes device A never restored, A backing up would
     * silently drop B's notes from the cloud copy, since nothing ever
     * merged them in first. Restore already merges (see
     * [BibleRepository.importFromBackup]); this makes backup use the exact
     * same merge on the way up, so the two devices actually converge
     * instead of whichever backs up last winning outright. */
    fun backupToDrive(onNeedsConsent: (Intent) -> Unit) {
        val account = _driveAccount.value ?: run {
            _backupStatusMessage.value = "Sign in to Google Drive first"
            return
        }
        viewModelScope.launch {
            _isDriveSyncing.value = true
            when (val downloadResult = driveBackupManager.downloadBackup(account)) {
                is DriveBackupManager.RestoreResult.Success -> {
                    try {
                        repository.importFromBackup(downloadResult.json)
                    } catch (e: Exception) {
                        // Remote copy is unreadable (corrupt/foreign
                        // format) — proceed with a local-only backup
                        // rather than blocking the whole operation on it.
                    }
                }
                is DriveBackupManager.RestoreResult.NeedsConsent -> {
                    onNeedsConsent(downloadResult.intent)
                    _isDriveSyncing.value = false
                    return@launch
                }
                is DriveBackupManager.RestoreResult.NotFound -> {
                    // Nothing remote yet — this is the first backup, no merge needed.
                }
                is DriveBackupManager.RestoreResult.Failure -> {
                    // Couldn't reach Drive to check the remote copy at all.
                    // Still proceed and upload local state rather than
                    // blocking the backup entirely on a transient network
                    // failure at just this one step.
                }
            }

            val json = repository.exportBackupJson()
            when (val result = driveBackupManager.uploadBackup(account, json)) {
                is DriveBackupManager.BackupResult.Success -> {
                    val now = System.currentTimeMillis()
                    repository.setLastDriveBackupAt(now)
                    _lastDriveBackupAt.value = now
                    _backupStatusMessage.value = "Backed up to Google Drive"
                }
                is DriveBackupManager.BackupResult.NeedsConsent -> onNeedsConsent(result.intent)
                is DriveBackupManager.BackupResult.Failure -> _backupStatusMessage.value =
                    "Drive backup failed: ${result.message}"
            }
            _isDriveSyncing.value = false
        }
    }

    /** Restores from Google Drive's appdata folder, merging into whatever
     * notes/highlights/etc. are already on this device (see
     * [BibleRepository.importFromBackup] for the merge rules). */
    fun restoreFromDrive(onNeedsConsent: (Intent) -> Unit) {
        val account = _driveAccount.value ?: run {
            _backupStatusMessage.value = "Sign in to Google Drive first"
            return
        }
        viewModelScope.launch {
            _isDriveSyncing.value = true
            when (val result = driveBackupManager.downloadBackup(account)) {
                is DriveBackupManager.RestoreResult.Success -> {
                    try {
                        val importResult = repository.importFromBackup(result.json)
                        val now = System.currentTimeMillis()
                        repository.setLastDriveRestoreAt(now)
                        _lastDriveRestoreAt.value = now
                        _backupStatusMessage.value = "Restored from Drive: ${importResult.notesAdded} new notes, " +
                            "${importResult.notesUpdated} updated, ${importResult.highlightsAdded} new highlights, " +
                            "${importResult.colorsAdded} new colors"
                    } catch (e: Exception) {
                        _backupStatusMessage.value = "Couldn't read the Drive backup"
                    }
                }
                is DriveBackupManager.RestoreResult.NotFound ->
                    _backupStatusMessage.value = "No backup found on this Google account yet"
                is DriveBackupManager.RestoreResult.NeedsConsent -> onNeedsConsent(result.intent)
                is DriveBackupManager.RestoreResult.Failure -> _backupStatusMessage.value =
                    "Drive restore failed: ${result.message}"
            }
            _isDriveSyncing.value = false
        }
    }
}
