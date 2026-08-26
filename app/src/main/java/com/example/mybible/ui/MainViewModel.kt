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
import com.example.mybible.data.BibleDataImportWorker
import com.example.mybible.data.BibleRepository
import com.example.mybible.data.DriveBackupManager
import com.example.mybible.data.DriveSyncWorker
import com.example.mybible.reminders.ReminderFrequency
import com.example.mybible.reminders.ReminderScheduler
import com.example.mybible.reminders.ReminderTheme
import com.example.mybible.data.resolveBookName
import com.example.mybible.data.LexiconLookupResult
import com.example.mybible.model.*
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BOOK_CHAPTER_COUNTS
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
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
    READER, STUDIED, NOTES, SEARCH, SETTINGS, HIGHLIGHTS, CROSS_REFERENCES, GREEK_WORD, HEBREW_WORD
}

// Mirrors Capacitor's pickingMode (notes) and selectMode (studied) — a
// banner overlay on the Reader that changes what tapping a verse does.
// Only one can be active at a time.
enum class ReaderPickMode {
    NONE, NOTE_PICK, STUDY_PICK
}

// Guided app tour state — see MainViewModel.tourMode's doc. CHOOSING is
// the upfront "curated or everything" prompt; CURATED/FULL are the tour
// itself actually running.
enum class TourMode { NONE, CHOOSING, CURATED, FULL }

// In-memory-only "last scroll position in Reader" marker — see
// MainViewModel.saveReaderAnchor's doc. Deliberately not @Serializable/
// persisted: it only needs to survive a live tab switch, not an app
// restart (repository.saveLastPosition already covers that, at
// book/chapter granularity).
data class ReaderScrollAnchor(val book: String, val chapter: Int, val verse: Int)

// How long to wait after the last scroll update before writing the exact
// reading position to disk (see MainViewModel.reportLiveTopVerse) — long
// enough that active scrolling doesn't turn into a write per frame, short
// enough that the on-disk position is never far behind if the process dies
// without a clean onStop.
private const val POSITION_SAVE_DEBOUNCE_MS = 1500L

// How long the user needs to have been continuously reading the current
// chapter, while a "return to X" detour banner is still active, before that
// position counts as "settled" enough to persist — see
// MainViewModel.isPositionSettled's doc. Long enough that briefly glancing
// at a search/cross-reference/etc. result (a few seconds, maybe a scroll or
// two) never corrupts the saved position with a chapter the user didn't
// mean to stay in; short enough that genuinely reading on past a detour for
// several minutes doesn't get silently discarded just because the banner
// was never explicitly dismissed.
private const val DETOUR_SETTLE_MS = 300_000L

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

    // Focused Verse
    private val _focusedVerseNumber = MutableStateFlow<Int?>(null)
    val focusedVerseNumber: StateFlow<Int?> = _focusedVerseNumber.asStateFlow()

    // Whether landing on focusedVerseNumber should also trigger the
    // temporary "blur the rest of the chapter" effect, vs just scrolling to
    // that verse and leaving the chapter reading normally. True for
    // cross-reference jumps and other "spotlight this exact verse" entry
    // points; false for browse-style entry points (see jumpToVerse).
    private val _focusedVerseBlurEnabled = MutableStateFlow(true)
    val focusedVerseBlurEnabled: StateFlow<Boolean> = _focusedVerseBlurEnabled.asStateFlow()

    // Whether landing on focusedVerseNumber should pin it flush to the exact
    // top of the viewport (offset 0) instead of the default centered "jump
    // to this verse" landing. True only for the Telugu/interlinear inline-
    // toggle case (toggleTeluguInline/toggleInterlinear below) — its whole
    // point is to keep whatever verse was already at the top of the screen
    // there, not to spotlight a new one, so centering it instead reads as a
    // scroll jump (the verse that was pinned at the top drifts toward the
    // middle of the screen, and drifts again on the next toggle since the
    // next anchor capture reads the now-wrong top verse). Every real "jump
    // to verse X" entry point (jumpToVerse, navigateToCrossReference, the
    // lexicon-page return below) explicitly resets this to false.
    private val _focusedVersePinToTop = MutableStateFlow(false)
    val focusedVersePinToTop: StateFlow<Boolean> = _focusedVersePinToTop.asStateFlow()

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
    val savedWords = repository.allSavedWords

    // NotesScreen is fully disposed (not just hidden) when the user
    // switches tabs, same as Search/CrossReferenceScreen — without this,
    // the list silently reset to the top every time you left the Notes
    // tab and came back, even though nothing else about it had changed.
    private val _notesScrollIndex = MutableStateFlow(0)
    val notesScrollIndex: StateFlow<Int> = _notesScrollIndex.asStateFlow()

    private val _notesScrollOffset = MutableStateFlow(0)
    val notesScrollOffset: StateFlow<Int> = _notesScrollOffset.asStateFlow()

    // Same reasoning, for SettingsScreen — a plain verticalScroll(ScrollState)
    // page rather than a LazyColumn, so it's a single pixel offset instead
    // of an index/offset pair.
    private val _settingsScrollPosition = MutableStateFlow(0)
    val settingsScrollPosition: StateFlow<Int> = _settingsScrollPosition.asStateFlow()

    fun saveSettingsScrollPosition(value: Int) {
        _settingsScrollPosition.value = value
    }

    fun saveNotesScrollPosition(index: Int, offset: Int) {
        _notesScrollIndex.value = index
        _notesScrollOffset.value = offset
    }

    // Feeds HighlightedVersesScreen (the "Highlighted Verses" browser tab):
    // joins the two flows above against each verse's text. Recomputes
    // whenever either the highlight set or the color labels change; verse
    // text lookups are cheap single-row Room queries, not worth caching
    // separately given highlight counts are small relative to the full text.
    val highlightedVerseItems: StateFlow<List<HighlightedVerseItem>> =
        combine(repository.allHighlights, repository.allHighlightColorDefs, repository.allNotes) { highlightList, colorDefs, notes ->
            buildHighlightedVerseItems(highlightList, colorDefs, notes = notes) { book, chapter, verse ->
                repository.getVerseText(book, chapter, verse)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Whether a "return to highlighted verses" banner should be showing in
    // the Reader — set when a highlighted-verse result is tapped, cleared
    // when the user either returns via the banner or dismisses it. The
    // banner's own Return button still goes to that list (same as
    // returnToSearchResults for search) — but fully exiting Highlights back
    // to Reader (its own close button, or system back from that tab) now
    // restores wherever Reader actually was before, via
    // _highlightsSourceVerse below, same as search/cross-reference/lexicon.
    private val _highlightsReturnAvailable = MutableStateFlow(false)
    val highlightsReturnAvailable: StateFlow<Boolean> = _highlightsReturnAvailable.asStateFlow()

    // Mirrors _searchSourceVerse: Reader's actual position before this
    // detour started, captured once (openHighlightedVerse guards on
    // already-set) and restored by backToHighlightsSourceVerse when the
    // Highlights tab is exited entirely.
    private val _highlightsSourceVerse = MutableStateFlow<ReaderScrollAnchor?>(null)

    // Same pattern, for StudiedScreen's two jump-to-Reader entry points
    // ("Recently Studied" card, verse grid selection) — neither of those
    // used to set any return flag at all, so system back after either one
    // fell through with nothing to catch it and exited/backgrounded the
    // app instead of doing anything sensible.
    private val _studiedReturnAvailable = MutableStateFlow(false)
    val studiedReturnAvailable: StateFlow<Boolean> = _studiedReturnAvailable.asStateFlow()

    // Mirrors _highlightsSourceVerse above, for Studied.
    private val _studiedSourceVerse = MutableStateFlow<ReaderScrollAnchor?>(null)

    private fun captureReaderSourceVerseIfNeeded(existing: MutableStateFlow<ReaderScrollAnchor?>) {
        if (existing.value != null) return
        val anchor = _readerAnchor.value
        val sourceVerseNumber = anchor?.takeIf {
            it.book == _currentBook.value && it.chapter == _currentChapter.value
        }?.verse ?: 1
        existing.value = ReaderScrollAnchor(_currentBook.value, _currentChapter.value, sourceVerseNumber)
    }

    fun markStudiedNavigation() {
        captureReaderSourceVerseIfNeeded(_studiedSourceVerse)
        _studiedReturnAvailable.value = true
    }

    fun returnToStudied() {
        _studiedReturnAvailable.value = false
        selectTab(NavTab.STUDIED)
    }

    fun dismissStudiedReturnBanner() {
        _studiedReturnAvailable.value = false
    }

    // System back / Studied's own back arrow when fully exiting the tab
    // (not the banner's Return button, which still goes to Studied — see
    // returnToStudied) — restores wherever Reader actually was before this
    // detour, same as backToSearchSourceVerse/backToHighlightsSourceVerse.
    fun backToStudiedSourceVerse() {
        val source = _studiedSourceVerse.value
        _studiedSourceVerse.value = null
        _studiedReturnAvailable.value = false
        if (source != null) {
            jumpToVerse(source.book, source.chapter, source.verse)
            _isBlurModeEnabled.value = false
        }
    }

    // StudiedScreen's own book/chapter drill-down + book-list scroll
    // position — same reasoning as notesScrollIndex/Offset: the screen is
    // fully disposed on a tab switch, so without this, leaving mid-browse
    // and coming back always reset to the top-level dashboard instead of
    // wherever you'd drilled into.
    private val _studiedSelectedBook = MutableStateFlow<String?>(null)
    val studiedSelectedBook: StateFlow<String?> = _studiedSelectedBook.asStateFlow()

    private val _studiedSelectedChapter = MutableStateFlow<Int?>(null)
    val studiedSelectedChapter: StateFlow<Int?> = _studiedSelectedChapter.asStateFlow()

    private val _studiedBookListScrollIndex = MutableStateFlow(0)
    val studiedBookListScrollIndex: StateFlow<Int> = _studiedBookListScrollIndex.asStateFlow()

    private val _studiedBookListScrollOffset = MutableStateFlow(0)
    val studiedBookListScrollOffset: StateFlow<Int> = _studiedBookListScrollOffset.asStateFlow()

    fun saveStudiedScreenState(book: String?, chapter: Int?, scrollIndex: Int, scrollOffset: Int) {
        _studiedSelectedBook.value = book
        _studiedSelectedChapter.value = chapter
        _studiedBookListScrollIndex.value = scrollIndex
        _studiedBookListScrollOffset.value = scrollOffset
    }

    // Used by HighlightedVersesScreen when the user taps a result: jump the
    // Reader to that verse and switch back to it, same as tapping a Studied
    // or Search result does.
    fun openHighlightedVerse(item: HighlightedVerseItem) {
        captureReaderSourceVerseIfNeeded(_highlightsSourceVerse)
        _highlightsReturnAvailable.value = true
        jumpToVerse(item.book, item.chapter, item.verse)
        // Same reasoning as navigateToCrossReference — you tapped this to
        // read the verse, blur mode would hide the very thing you came for.
        _isBlurModeEnabled.value = false
        selectTab(NavTab.READER)
    }

    // System back / HighlightedVersesScreen's own close button when fully
    // exiting the tab (not the banner's Return button, which still goes to
    // Highlights — see returnToHighlightedVerses) — restores wherever
    // Reader actually was before this detour, same as
    // backToSearchSourceVerse/backToStudiedSourceVerse.
    fun backToHighlightsSourceVerse() {
        val source = _highlightsSourceVerse.value
        _highlightsSourceVerse.value = null
        _highlightsReturnAvailable.value = false
        if (source != null) {
            jumpToVerse(source.book, source.chapter, source.verse)
            _isBlurModeEnabled.value = false
        }
    }

    fun returnToHighlightedVerses() {
        _highlightsReturnAvailable.value = false
        selectTab(NavTab.HIGHLIGHTS)
    }

    fun dismissHighlightsReturnBanner() {
        _highlightsReturnAvailable.value = false
    }

    // Interactive UI overlays & actions
    private val _selectedVerse = MutableStateFlow<Verse?>(null)
    val selectedVerse: StateFlow<Verse?> = _selectedVerse.asStateFlow()

    private val _selectedGreekWord = MutableStateFlow<GreekWord?>(null)
    val selectedGreekWord: StateFlow<GreekWord?> = _selectedGreekWord.asStateFlow()

    private val _selectedHebrewWord = MutableStateFlow<HebrewWord?>(null)
    val selectedHebrewWord: StateFlow<HebrewWord?> = _selectedHebrewWord.asStateFlow()

    // Greek Lexicon (TBESG) Lookup state — GreekWordScreen's inline gloss
    // comes straight off selectedGreekWord above; this is the fuller entry
    // fetched lazily once the page opens, mirroring Capacitor's
    // openGreekWordSheet() (the page here, its bottom sheet there).
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

    // Scroll position within GreekWordScreen/HebrewWordScreen's definition
    // text, saved/restored across the trip to Reader and back the same way
    // Search/CrossReferenceScreen do (see _searchScrollIndex etc. below) —
    // a single Int since the page is one continuously-scrolling Column, not
    // a LazyColumn of discrete items.
    private val _greekWordScrollPosition = MutableStateFlow(0)
    val greekWordScrollPosition: StateFlow<Int> = _greekWordScrollPosition.asStateFlow()

    private val _hebrewWordScrollPosition = MutableStateFlow(0)
    val hebrewWordScrollPosition: StateFlow<Int> = _hebrewWordScrollPosition.asStateFlow()

    fun saveGreekWordScrollPosition(position: Int) {
        _greekWordScrollPosition.value = position
    }

    fun saveHebrewWordScrollPosition(position: Int) {
        _hebrewWordScrollPosition.value = position
    }

    // Cross References page (see CrossReferenceScreen) — same
    // persisted-list-and-scroll-position pattern as Search (see
    // _searchResults / _searchScrollIndex below): the list and source verse
    // survive navigating to the Reader to follow a reference, so "return to
    // cross references" lands back on the same list instead of a fresh
    // lookup.
    private val _crossReferenceList = MutableStateFlow<List<CrossReferenceItem>?>(null)
    val crossReferenceList: StateFlow<List<CrossReferenceItem>?> = _crossReferenceList.asStateFlow()

    // The verse the currently-open cross-reference list was opened *from*.
    // Tracked separately from _selectedVerse since opening xrefs via the
    // dagger marker never selects the verse. Shown as the "base verse" at
    // the top of CrossReferenceScreen.
    private val _crossReferenceSourceVerse = MutableStateFlow<Verse?>(null)
    val crossReferenceSourceVerse: StateFlow<Verse?> = _crossReferenceSourceVerse.asStateFlow()

    private val _crossReferenceScrollIndex = MutableStateFlow(0)
    val crossReferenceScrollIndex: StateFlow<Int> = _crossReferenceScrollIndex.asStateFlow()

    private val _crossReferenceScrollOffset = MutableStateFlow(0)
    val crossReferenceScrollOffset: StateFlow<Int> = _crossReferenceScrollOffset.asStateFlow()

    fun saveCrossReferenceScrollPosition(index: Int, offset: Int) {
        _crossReferenceScrollIndex.value = index
        _crossReferenceScrollOffset.value = offset
    }

    // Whether a "return to cross references" banner should be showing in
    // the Reader — set when a cross-reference result is tapped, cleared
    // when the user either returns via the banner or dismisses it. Same
    // pattern as _searchReturnAvailable below.
    private val _crossReferenceReturnAvailable = MutableStateFlow(false)
    val crossReferenceReturnAvailable: StateFlow<Boolean> = _crossReferenceReturnAvailable.asStateFlow()

    // "targetBook:targetChapter:targetVerse" key of the last reference
    // tapped — same idea and lifecycle as _searchLastTappedKey above, for
    // CrossReferenceScreen's list instead of Search's.
    private val _crossReferenceLastTappedKey = MutableStateFlow<String?>(null)
    val crossReferenceLastTappedKey: StateFlow<String?> = _crossReferenceLastTappedKey.asStateFlow()

    fun clearCrossReferenceLastTapped() {
        _crossReferenceLastTappedKey.value = null
    }

    // Verse-mention preview sheet — opened by tapping a linkified reference
    // inside note text (see data/VerseMentionLinkifier.kt) or a clickable
    // Scripture citation inside a Greek/Hebrew lexicon definition (see
    // ui/components/LexiconDefinitionView.kt). Matches Capacitor's
    // openVerseTextSheet/closeVerseTextSheet: shows the reference + verse
    // text without leaving the note/lexicon page; "Open in Reader" is an
    // explicit separate action.
    //
    // lexiconOriginTab is non-null only for the lexicon-reference case — it
    // records which of NavTab.GREEK_WORD/HEBREW_WORD to return to (see
    // _lexiconReturnTab below) once "Open in Reader" is tapped.
    //
    // noteReturnItem is non-null only when the mention was tapped inside
    // NoteReaderScreen (see _noteReturnItem below) — the note to reopen
    // once "Open in Reader" is tapped. Null for the note-editor origin too
    // (a mention tapped mid-edit): reopening the editor there would reseed
    // its local draft state from the saved note, discarding any unsaved
    // edits, so that case keeps the old behavior (closes the editor, no
    // return banner) until that's handled properly.
    data class VerseMentionPreview(
        val book: String,
        val chapter: Int,
        val verse: Int,
        val text: String?,
        val lexiconOriginTab: NavTab? = null,
        val noteReturnItem: NoteItem? = null
    )

    private val _verseMentionPreview = MutableStateFlow<VerseMentionPreview?>(null)
    val verseMentionPreview: StateFlow<VerseMentionPreview?> = _verseMentionPreview.asStateFlow()

    fun openVerseMentionPreview(
        book: String,
        chapter: Int,
        verse: Int,
        lexiconOriginTab: NavTab? = null,
        noteReturnItem: NoteItem? = null
    ) {
        _verseMentionPreview.value = VerseMentionPreview(book, chapter, verse, null, lexiconOriginTab, noteReturnItem)
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

    // Which lexicon tab (if any) a "return to lexicon" banner in the
    // Reader should jump back to — set when a reference tapped inside a
    // Greek/Hebrew lexicon definition is opened in the Reader. Mirrors
    // _crossReferenceReturnAvailable's pattern, but needs to remember
    // *which* of the two lexicon tabs to return to (GREEK_WORD vs
    // HEBREW_WORD), unlike cross-references which only ever return to one
    // place.
    private val _lexiconReturnTab = MutableStateFlow<NavTab?>(null)
    val lexiconReturnTab: StateFlow<NavTab?> = _lexiconReturnTab.asStateFlow()

    fun setLexiconReturnTab(tab: NavTab) {
        _lexiconReturnTab.value = tab
    }

    fun returnToLexicon() {
        val tab = _lexiconReturnTab.value ?: return
        _lexiconReturnTab.value = null
        selectTab(tab)
    }

    fun dismissLexiconReturnBanner() {
        _lexiconReturnTab.value = null
    }

    // Which note (if any) a "return to note" banner in the Reader should
    // reopen — set when a verse mention tapped inside NoteReaderScreen is
    // opened in the Reader. Same pattern as _lexiconReturnTab, but unlike
    // cross-references/lexicon there's no "verse reading started from" to
    // distinguish a system-back destination from a button destination —
    // you were reading a note, not a Bible verse, so both the banner's
    // Return button and system back do the same thing here: reopen that
    // note (see MainActivity's back-handling comment).
    private val _noteReturnItem = MutableStateFlow<NoteItem?>(null)
    val noteReturnItem: StateFlow<NoteItem?> = _noteReturnItem.asStateFlow()

    // Where Reader actually was before the user ever went to the Notes tab
    // — separate from _noteReturnItem above, which only tracks "which note
    // to reopen" one step at a time. A verse-mention jump can nest several
    // notes deep (note A's mention -> Reader -> back to note A -> back to
    // notes list), and each of those steps disposes/remounts Reader — which
    // would clobber a shared anchor like readerAnchor — so this is captured
    // once (guarded, see captureReaderSourceVerseIfNeeded) the first time a
    // note-mention jump happens, and only consumed when the Notes tab is
    // exited entirely (see backToNotesSourceVerse), not by the individual
    // note-mention "return to note" steps in between.
    private val _notesSourceVerse = MutableStateFlow<ReaderScrollAnchor?>(null)

    fun setNoteReturnItem(note: NoteItem) {
        captureReaderSourceVerseIfNeeded(_notesSourceVerse)
        _noteReturnItem.value = note
    }

    // Called when the Notes tab is exited entirely — its own back arrow, or
    // system back from the Notes list (not from within a note or its
    // "return to note" banner, which keep working as before). Restores
    // wherever Reader actually was before the user ever opened Notes.
    fun backToNotesSourceVerse() {
        val source = _notesSourceVerse.value
        _notesSourceVerse.value = null
        if (source != null) {
            jumpToVerse(source.book, source.chapter, source.verse)
            _isBlurModeEnabled.value = false
        }
    }

    fun returnToNote() {
        val note = _noteReturnItem.value ?: return
        _noteReturnItem.value = null
        val originTab = _noteReaderOriginTab.value
        openNoteReader(note, originTab)
        selectTab(originTab)
    }

    fun dismissNoteReturnBanner() {
        _noteReturnItem.value = null
    }

    private val _showBookPicker = MutableStateFlow(false)
    val showBookPicker: StateFlow<Boolean> = _showBookPicker.asStateFlow()

    private val _showNoteEditor = MutableStateFlow(false)
    val showNoteEditor: StateFlow<Boolean> = _showNoteEditor.asStateFlow()

    private val _noteToEdit = MutableStateFlow<NoteItem?>(null)
    val noteToEdit: StateFlow<NoteItem?> = _noteToEdit.asStateFlow()

    private val _noteToRead = MutableStateFlow<NoteItem?>(null)
    val noteToRead: StateFlow<NoteItem?> = _noteToRead.asStateFlow()

    // Guided app tour — replaces the old static 4-slide AlertDialog with a
    // real walkthrough that switches through the actual tabs (see
    // ui/components/GuidedTourComponents.kt for the step content and
    // overlay UI). CHOOSING is the upfront "curated or everything" prompt;
    // starts there automatically on first launch, same trigger the old
    // showOnboarding used.
    private val _tourMode = MutableStateFlow(if (repository.isFirstLaunch()) TourMode.CHOOSING else TourMode.NONE)
    val tourMode: StateFlow<TourMode> = _tourMode.asStateFlow()

    private val _tourStepIndex = MutableStateFlow(0)
    val tourStepIndex: StateFlow<Int> = _tourStepIndex.asStateFlow()

    // Shown once, right after a CURATED tour ends, noting the full
    // walkthrough is still available later — not shown after FULL, and not
    // shown at all if the user skips out of the tour entirely (skipping
    // isn't "I want the short version," it's "not now").
    private val _tourJustFinishedCurated = MutableStateFlow(false)
    val tourJustFinishedCurated: StateFlow<Boolean> = _tourJustFinishedCurated.asStateFlow()

    /** Opens the upfront curated/everything choice — used by Settings'
     *  "Show app tour" button to relaunch the tour on demand. */
    fun startTour() {
        _tourMode.value = TourMode.CHOOSING
        _tourStepIndex.value = 0
    }

    fun chooseTourVariant(mode: TourMode) {
        _tourMode.value = mode
        _tourStepIndex.value = 0
    }

    fun setTourStepIndex(index: Int) {
        _tourStepIndex.value = index
    }

    /** Ends the tour, whatever mode it was in — used for both "finished
     *  the last step" and "tapped Skip"/dismissed the choice prompt. */
    fun finishTour() {
        val wasCurated = _tourMode.value == TourMode.CURATED
        _tourMode.value = TourMode.NONE
        repository.setFirstLaunchCompleted()
        if (wasCurated) _tourJustFinishedCurated.value = true
    }

    fun dismissTourCuratedEndNote() {
        _tourJustFinishedCurated.value = false
    }

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

    // Where the user was actually reading before this search detour started
    // — Reader's own position, kept separate from wherever a search result
    // jump has since taken it. Mirrors _crossReferenceSourceVerse's role for
    // cross-references: the banner's own Return button (returnToSearchResults)
    // goes to the search results list, but system back means "take me back
    // to where I was," same as it does for cross-references/lexicon — see
    // backToSearchSourceVerse. Captured once per search "session" (only if
    // not already set) so tapping a second result after already returning
    // to the list once doesn't overwrite the *original* reading position
    // with the first result's; cleared in endSearchSession, same lifetime
    // as the rest of the search-detour state.
    private val _searchSourceVerse = MutableStateFlow<ReaderScrollAnchor?>(null)

    // "book:chapter:verse" key of the last search result tapped — lets
    // SearchScreen mark that card with an accent bar on return, so the
    // user can spot which result they already visited without having to
    // remember it themselves. Cleared once the user scrolls the list
    // (SearchScreen's own scroll-watching effect) or ends the session.
    private val _searchLastTappedKey = MutableStateFlow<String?>(null)
    val searchLastTappedKey: StateFlow<String?> = _searchLastTappedKey.asStateFlow()

    fun clearSearchLastTapped() {
        _searchLastTappedKey.value = null
    }

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

    // True once the cold-start restore below has fully landed: chapter
    // loaded (verses populated) and, if there was one, the saved verse
    // applied to focusedVerseNumber. MainActivity holds the whole app's UI
    // back (a blank frame, not any tab's content) until this flips —
    // rather than mounting Reader immediately and racing several layers of
    // seed/hide/reveal machinery to hide the gap, there's simply nothing
    // mounted yet to show a wrong position in. Reader mounting only once
    // everything is already resolved makes a cold start look, to
    // ReaderScreen, exactly like any other in-app jump to an
    // already-loaded chapter — which already works correctly.
    private val _initialRestoreComplete = MutableStateFlow(false)
    val initialRestoreComplete: StateFlow<Boolean> = _initialRestoreComplete.asStateFlow()

    init {
        val (savedBook, savedChap) = repository.getLastPosition()
        _currentBook.value = savedBook
        _currentChapter.value = savedChap
        // Resume at the exact verse the app was showing when it last went
        // to background (see saveLastReadPosition/the ProcessLifecycleOwner
        // observer below), not just the top of the saved chapter. Unblurred/
        // pinned-to-top — same as toggleTeluguInline's anchor restore — since
        // this is a silent position restore, not a "look at this verse" jump.
        //
        // Deliberately set *after* loadCurrentChapterSuspend() finishes,
        // not before — ReaderScreen's scroll-to-focus effect and its
        // "did the user scroll away" watcher both key off focusedVerseNumber
        // and verses together; setting focusedVerseNumber here before
        // ReaderScreen has even composed once meant the watcher could start
        // observing (with nothing landed yet, since verses was still empty)
        // before the scroll effect ever got a chance to run, misreading the
        // very first unscrolled frame as "the user scrolled away" and
        // clearing the target before it was used — this only ever showed up
        // on a genuine cold start (e.g. via the widget), since a normal
        // in-app jump always has verses already loaded by the time it sets
        // focusedVerseNumber on an already-mounted Reader. Sequencing it
        // after the chapter's verses are actually in _verses makes this
        // cold-start restore look like any other in-app jump to Reader,
        // instead of a special pre-mount case.
        val restoredVerse = repository.getLastReadVerse()
        viewModelScope.launch {
            loadCurrentChapterSuspend()
            restoredVerse?.let { verse ->
                _focusedVerseNumber.value = verse
                _focusedVerseBlurEnabled.value = false
                _focusedVersePinToTop.value = true
            }
            _initialRestoreComplete.value = true
        }

        // Study time should only accrue while the app is actually in the
        // foreground — previously it started once here and never stopped,
        // so it counted process-alive time (including backgrounded) rather
        // than time spent reading. ProcessLifecycleOwner gives us one
        // app-wide foreground/background signal without wiring per-Activity
        // lifecycle callbacks through the ViewModel.
        // Deliberately NOT where the exact reading position gets persisted
        // (see persistCurrentReadingPosition below and MainActivity's own
        // onStop override) — ProcessLifecycleOwner debounces this callback
        // by roughly 700ms to avoid false "backgrounded" signals from brief
        // activity transitions, which is exactly right for study-time
        // tracking but means backgrounding the app and reopening it (e.g.
        // via the widget) within that window would race the save and could
        // still find yesterday's — or no — saved verse.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = startStudyTimer()
            override fun onStop(owner: LifecycleOwner) = stopStudyTimer()
        })

        // Runs as WorkManager foreground work (see BibleDataImportWorker)
        // instead of a plain coroutine here, so the one-time download
        // actually survives the app being backgrounded. enqueue() is safe
        // to call on every launch (KEEP policy — a finished or in-progress
        // run is left alone); awaitCompletion() below just waits for
        // whatever that run is to reach a terminal state, then does the
        // same on-screen refresh the old direct ensureImported() call did.
        BibleDataImportWorker.enqueue(appContext)
        viewModelScope.launch {
            BibleDataImportWorker.awaitCompletion(appContext)
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
        BibleDataImportWorker.enqueueRetry(appContext)
        viewModelScope.launch {
            BibleDataImportWorker.awaitCompletion(appContext)
            loadCurrentChapter()
        }
    }

    // Settings' "Re-check Greek/Hebrew data" — see
    // BibleDataImportWorker.enqueueGreekHebrewReimport's doc for why this
    // is separate from retryBibleDataImport above (that one only helps a
    // step that never finished; this handles one that finished against a
    // now-stale upstream snapshot). Reloads the current chapter afterward
    // so any newly-imported words for the verse on screen show up
    // immediately, same as retryBibleDataImport does.
    fun reimportGreekAndHebrewData() {
        BibleDataImportWorker.enqueueGreekHebrewReimport(appContext)
        viewModelScope.launch {
            BibleDataImportWorker.awaitCompletion(appContext)
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

    // In-memory-only "where was I reading" anchor — separate from
    // repository.saveLastPosition (book/chapter, persisted to disk for
    // cold app launch): this is verse-level and only needs to survive
    // ReaderScreen being disposed and remounted within the same live app
    // session, e.g. visiting Highlighted Verses/Notes/Search/etc. and
    // pressing back without having tapped anything there. ReaderScreen
    // saves it (see its own DisposableEffect) with the top-visible verse
    // right before it's torn down, and consults it once on a fresh mount
    // when there's no explicit focusedVerseNumber jump target — otherwise
    // that mount had nothing to go on but "scroll to the top of the
    // chapter," which is the exact behavior this fixes.
    private val _readerAnchor = MutableStateFlow<ReaderScrollAnchor?>(null)
    val readerAnchor: StateFlow<ReaderScrollAnchor?> = _readerAnchor.asStateFlow()

    fun saveReaderAnchor(book: String, chapter: Int, verse: Int?) {
        _readerAnchor.value = if (verse != null) ReaderScrollAnchor(book, chapter, verse) else null
    }

    // Consumed (cleared) once used so a coincidental later revisit to the
    // same chapter within the same session doesn't keep re-applying a
    // stale anchor from a much earlier visit.
    fun consumeReaderAnchor() {
        _readerAnchor.value = null
    }

    // Continuously-updated "what verse is at the top of the Reader viewport
    // right now" — read once, synchronously, when the app actually
    // backgrounds (see persistCurrentReadingPosition/MainActivity's onStop)
    // to persist an exact resume position to disk (saveLastReadPosition).
    // Deliberately separate from readerAnchor above: that one is only
    // snapshotted at tab-switch-away time and gets consumed/cleared on use,
    // which is exactly wrong here — backgrounding the app while still on
    // the Reader tab never triggers a tab switch, so readerAnchor would
    // often be stale or null at the moment it's needed. A plain field (not
    // a StateFlow) is enough since nothing needs to observe this reactively.
    private var liveTopVerse: Int? = null

    // When the current book/chapter was last (re)entered — reset every time
    // loadChapter runs, regardless of detour state. Used only to measure
    // "how long has the user been sitting in this chapter" for
    // isPositionSettled below; irrelevant outside a detour, since saves
    // aren't gated on it then.
    private var chapterEnteredAtMillis: Long = System.currentTimeMillis()

    // Debounced disk-only save fired on every scroll update, independent of
    // any single lifecycle callback getting a chance to run — a safety net
    // alongside the onStop-triggered durable save (persistCurrentReadingPosition
    // below), for cases where onStop itself either races a fast process kill
    // or doesn't fire at all (some OEM "swipe away" implementations skip
    // Activity lifecycle callbacks entirely). Cancel-and-relaunch means only
    // the last scroll position in any given window actually hits disk.
    private var positionSaveJob: Job? = null

    fun reportLiveTopVerse(book: String, chapter: Int, verse: Int?) {
        if (book == _currentBook.value && chapter == _currentChapter.value) {
            liveTopVerse = verse
            positionSaveJob?.cancel()
            positionSaveJob = viewModelScope.launch {
                delay(POSITION_SAVE_DEBOUNCE_MS)
                // Gated the same way persistCurrentReadingPosition is (see
                // isPositionSettled's doc) — otherwise this debounced save,
                // which has no reason of its own to know about detours,
                // would silently overwrite the saved position with a
                // search/cross-reference/etc. jump the user only glanced at.
                if (isPositionSettled()) savePositionToDisk(durable = false)
            }
        }
    }

    // True while a "return to X" banner is showing (cross-reference/search/
    // lexicon/note/highlights/studied) — the user has jumped away from
    // where they were reading but hasn't decided yet whether to stay here
    // or go back. See loadChapter/the ProcessLifecycleOwner onStop
    // observer's docs for why this gates what counts as the "genuine"
    // reading position for resume/widget purposes: a search/cross-
    // reference/etc. detour a few taps deep shouldn't silently become
    // "where you were reading" just because you looked at it, only once
    // you've actually settled there (dismissed the banner via its own
    // close button rather than tapping Return) or backed all the way out.
    private fun isDetourActive(): Boolean =
        _crossReferenceReturnAvailable.value ||
            _searchReturnAvailable.value ||
            _lexiconReturnTab.value != null ||
            _noteReturnItem.value != null ||
            _highlightsReturnAvailable.value ||
            _studiedReturnAvailable.value

    // What actually gates every position save (durable onStop save,
    // debounced scroll save, and per-chapter-navigation save alike) — not
    // just isDetourActive() by itself. A "return to X" banner is
    // deliberately sticky now (see the scroll-away watcher in
    // ReaderScreen — it no longer auto-clears on scroll, so browsing
    // multiple search/cross-reference/etc. results doesn't kill the
    // banner), which means isDetourActive() alone could stay true for as
    // long as the user keeps reading, silently blocking their real
    // position from ever being saved even after they've clearly settled
    // into this chapter. DETOUR_SETTLE_MS is the escape hatch: once the
    // user has been sitting in the current chapter that long, treat it as
    // genuinely settled and allow saves through regardless of whether the
    // banner itself has been dismissed.
    private fun isPositionSettled(): Boolean =
        !isDetourActive() || (System.currentTimeMillis() - chapterEnteredAtMillis >= DETOUR_SETTLE_MS)

    // Persists an exact resume position, not just book/chapter
    // (saveLastPosition already covers that on every chapter navigation) —
    // liveTopVerse is kept live by ReaderScreen's own scroll-reporting
    // effect (see reportLiveTopVerse) the whole time the app is in the
    // foreground, so whatever it holds here is genuinely "where the user
    // was looking" the moment this is called, not stale.
    //
    // Called from MainActivity's own Activity-level onStop, not the
    // ProcessLifecycleOwner observer above — that one is debounced by
    // ~700ms (correct for study-time tracking, wrong here), so backgrounding
    // the app and immediately reopening it via the widget could otherwise
    // race the save and land back on whatever was saved before (or nothing,
    // on a first run), overwriting the exact verse with the top of the
    // chapter. Activity.onStop fires synchronously the moment this Activity
    // loses the foreground, closing that window.
    //
    // Skipped while a detour banner is showing and the user hasn't been
    // sitting in this chapter long enough yet to count as settled (see
    // isPositionSettled's doc) — backgrounding mid-detour, before the user
    // has decided to stay or go back, must leave whatever was already saved
    // untouched rather than silently overwriting it with a position the
    // user hasn't settled on. Once settled, goes through even with the
    // banner still technically up.
    fun persistCurrentReadingPosition() {
        if (isPositionSettled()) {
            // durable=true: this is the one guaranteed moment before Android
            // may kill the process, so it blocks briefly on a synchronous
            // write rather than risking apply()'s queued write losing the
            // race (see saveLastReadPosition's own doc). The debounced path
            // in reportLiveTopVerse above deliberately stays non-durable —
            // this is the only call site that needs the stronger guarantee.
            positionSaveJob?.cancel()
            savePositionToDisk(durable = true)
            refreshContinueReadingWidget()
        }
    }

    private fun savePositionToDisk(durable: Boolean) {
        repository.saveLastReadPosition(_currentBook.value, _currentChapter.value, liveTopVerse, durable = durable)
    }

    fun loadChapter(book: String, chapter: Int) {
        _currentBook.value = book
        _currentChapter.value = chapter
        // Restarts the "how long has the user been sitting here" clock
        // isPositionSettled uses — a fresh chapter (detour or not) always
        // starts at 0 elapsed, same as before this existed.
        chapterEnteredAtMillis = System.currentTimeMillis()
        // Only persist/refresh the widget for a genuine reading position —
        // swiping chapters or picking one from the Book/Chapter picker
        // (neither sets a detour flag) still does immediately, same as
        // before; a search/cross-reference/etc. jump (which does) skips
        // this until the user settles there — see isPositionSettled's doc.
        if (isPositionSettled()) {
            repository.saveLastPosition(book, chapter)
            refreshContinueReadingWidget()
        }
        loadCurrentChapter()
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
    //
    // Dispatchers.Default (not the viewModelScope default of Main.immediate):
    // loadChapter also kicks off loadCurrentChapter()'s heavier verse/xref
    // load on the same scope, and backgrounding the app right after
    // switching chapters piles on window-teardown work of its own — all of
    // that queues ahead of this on the Main thread, so the widget lagged
    // behind exactly when the user jumped straight to the home screen to
    // check it. updateAll() itself doesn't touch any UI, so it doesn't need
    // Main at all; running it on Default lets it fire as soon as the prefs
    // write lands instead of waiting for Main to clear out.
    private fun refreshContinueReadingWidget() {
        viewModelScope.launch(Dispatchers.Default) {
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
        _focusedVerseNumber.value = verse
        _focusedVerseBlurEnabled.value = focusVerse
        _focusedVersePinToTop.value = false
        loadChapter(book, chapter)
    }

    // Verse count for the picker's verse grid — see
    // BibleRepository.getVerseCount for why this isn't a plain Room count.
    suspend fun getVerseCount(book: String, chapter: Int): Int = repository.getVerseCount(book, chapter)

    suspend fun getVerseText(book: String, chapter: Int, verse: Int): String? =
        repository.getVerseText(book, chapter, verse)

    private fun loadCurrentChapter() {
        viewModelScope.launch { loadCurrentChapterSuspend() }
    }

    // Suspend variant of loadCurrentChapter(), for callers that need to
    // sequence something to run only after this chapter's verses have
    // actually landed in _verses — see init{}'s use of it below. Every
    // other call site still just fires loadCurrentChapter() and moves on.
    private suspend fun loadCurrentChapterSuspend() {
        _isLoading.value = true
        _selectedVerse.value = null

        val bookName = _currentBook.value
        val chap = _currentChapter.value
        val result = repository.getChapterVerses(bookName, chap, includeTelugu = _showTeluguInline.value)
        _verses.value = result
        _verseNumbersWithXrefs.value = repository.getCrossReferenceVerseNumbers(bookName, chap)
        _isLoading.value = false
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

    // anchorVerse: the verse currently at/near the top of the reader's
    // viewport, if known. loadCurrentChapter() below re-fetches _verses
    // with a new list identity (same chapter, just with/without Telugu
    // text), and ReaderScreen's scroll effect is keyed on that list — with
    // no focusedVerseNumber to land on, it falls back to scrolling to the
    // very top of the chapter, which reads as "toggling Telugu jumped me
    // back to verse 1." Setting it here (unblurred — this is a position
    // restore, not a real "jump to this verse") makes that effect land back
    // where the user already was instead. pinToTop=true is the other half
    // of that: without it, the effect *centers* the anchor verse instead of
    // leaving it flush with the top of the screen, which reads as its own
    // kind of scroll jump (the verse that was pinned at the top drifts
    // toward the middle) and compounds on every subsequent toggle, since
    // each one re-anchors off the now-wrong top verse.
    fun toggleTeluguInline(anchorVerse: Int? = null) {
        _showTeluguInline.value = !_showTeluguInline.value
        if (anchorVerse != null) {
            _focusedVerseNumber.value = anchorVerse
            _focusedVerseBlurEnabled.value = false
            _focusedVersePinToTop.value = true
        }
        loadCurrentChapter()
    }

    // Same anchor-preservation need as toggleTeluguInline above, and the
    // same fix — but no loadCurrentChapter() call: showInterlinear only
    // gates whether VerseComponents renders the Greek/Hebrew interlinear
    // words that are already present on every loaded Verse, so there's no
    // new data to re-fetch, just a height change per verse to restore
    // position across (setting focusedVerseNumber still re-triggers
    // ReaderScreen's scroll-restore effect on its own).
    fun toggleInterlinear(anchorVerse: Int? = null) {
        _showInterlinear.value = !_showInterlinear.value
        if (anchorVerse != null) {
            _focusedVerseNumber.value = anchorVerse
            _focusedVerseBlurEnabled.value = false
            _focusedVersePinToTop.value = true
        }
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
    private val _remindersEnabled = MutableStateFlow(ReminderScheduler.isEnabled(appContext))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    fun setRemindersEnabled(enabled: Boolean) {
        ReminderScheduler.setEnabled(appContext, enabled)
        _remindersEnabled.value = enabled
    }

    // Frequency, active-hours window, and which message themes rotate —
    // all previously hardcoded (every 3 hours, 6am-9pm, all 6 themes).
    // ReminderScheduler/ReminderMessages own the actual persistence and
    // alarm-rescheduling; these StateFlows just mirror that for Settings'
    // UI, same pattern as remindersEnabled above.
    private val _reminderFrequency = MutableStateFlow(ReminderScheduler.getFrequency(appContext))
    val reminderFrequency: StateFlow<ReminderFrequency> = _reminderFrequency.asStateFlow()

    fun setReminderFrequency(frequency: ReminderFrequency) {
        ReminderScheduler.setFrequency(appContext, frequency)
        _reminderFrequency.value = frequency
    }

    // Minutes-since-midnight, null until the user has actually picked a
    // value — no silent 6am-9pm default (see ReminderScheduler.getStartMinutes's
    // doc). End is re-synced after every start change since setStartMinutes
    // may have cleared a now-too-close end time server-side.
    private val _reminderStartMinutes = MutableStateFlow(ReminderScheduler.getStartMinutes(appContext))
    val reminderStartMinutes: StateFlow<Int?> = _reminderStartMinutes.asStateFlow()

    private val _reminderEndMinutes = MutableStateFlow(ReminderScheduler.getEndMinutes(appContext))
    val reminderEndMinutes: StateFlow<Int?> = _reminderEndMinutes.asStateFlow()

    fun setReminderStartMinutes(startMinutes: Int) {
        ReminderScheduler.setStartMinutes(appContext, startMinutes)
        _reminderStartMinutes.value = startMinutes
        _reminderEndMinutes.value = ReminderScheduler.getEndMinutes(appContext)
    }

    fun setReminderEndMinutes(endMinutes: Int) {
        ReminderScheduler.setEndMinutes(appContext, endMinutes)
        _reminderEndMinutes.value = endMinutes
    }

    private val _reminderEnabledThemes = MutableStateFlow(ReminderScheduler.getEnabledThemes(appContext))
    val reminderEnabledThemes: StateFlow<Set<ReminderTheme>> = _reminderEnabledThemes.asStateFlow()

    fun setReminderThemeEnabled(theme: ReminderTheme, enabled: Boolean) {
        ReminderScheduler.setThemeEnabled(appContext, theme, enabled)
        _reminderEnabledThemes.value = ReminderScheduler.getEnabledThemes(appContext)
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

    // Tapping a reference in CrossReferenceScreen: same pattern as
    // openSearchResult — this is a fresh reader position, so it flags the
    // "return to cross references" banner (rather than dismissing/clearing
    // the list, which now stays intact for that return trip) and jumps.
    fun navigateToCrossReference(targetBook: String, targetChapter: Int, targetVerse: Int) {
        _crossReferenceReturnAvailable.value = true
        _crossReferenceLastTappedKey.value = "$targetBook:$targetChapter:$targetVerse"
        _focusedVerseNumber.value = targetVerse
        _focusedVerseBlurEnabled.value = true
        _focusedVersePinToTop.value = false
        // Blur mode (privacy blur, the pill toggle) would defeat the whole
        // point of following a cross-reference — you followed it to read
        // the target verse, not stare at an obscured one.
        _isBlurModeEnabled.value = false
        selectTab(NavTab.READER)
        loadChapter(targetBook, targetChapter)
    }

    // Clears just the "spotlight this verse, blur the rest" target. Called
    // when entering any pick mode (Study or Note), since pick mode is a
    // distinct interaction that should always show the chapter normally —
    // never inheriting a stale
    // focus/blur left over from an earlier cross-reference jump, search
    // result, or highlighted-verse tap that happened to land on the same
    // chapter. Also called by ReaderScreen once the user scrolls away from
    // wherever a jump landed (see its "watches for the user actually
    // moving away" effect) — without that, focusedVerseNumber stayed set
    // indefinitely, and since ReaderScreen's isFocused gives it priority
    // over isBlurModeEnabled, manually turning on Blur Mode afterward got
    // stuck spotlighting that stale jump target instead of tracking scroll
    // position, no matter how far you scrolled.
    fun clearVerseFocus() {
        _focusedVerseNumber.value = null
        _focusedVersePinToTop.value = false
    }

    // Clears every "return to X" detour flag (cross-reference/search/
    // lexicon/note/highlights/studied) plus any leftover verse focus. Two
    // callers: entry points that arrive at Reader from *outside* the app
    // (the home screen widget), and ReaderScreen's own "scrolled away from
    // the landed verse" watcher — the latter is what makes this the
    // *general* answer to "the user has settled here," not just a widget-
    // specific cleanup: without it, a detour flag set by jumping in from
    // Highlighted Verses/Studied/Search/Cross-Ref/Lexicon/Notes stayed
    // stuck true for the rest of the session unless the user happened to
    // tap that banner's own Return/dismiss button, silently blocking every
    // isDetourActive()-gated position save (including the widget's exact-
    // verse resume) from then on.
    //
    // Without this: these flags are plain in-memory state, so backgrounding
    // the app mid-detour (e.g. having followed a cross-reference, then
    // pressing Home instead of formally closing the banner) leaves them
    // set indefinitely — the process is still alive, nothing clears them.
    // Reopening later via a widget tap (which jumps straight to a chapter,
    // or just re-selects the Reader tab, neither of which touched these
    // flags before) then hit a surprising bug: the very first system back
    // press was silently hijacked by that stale detour — e.g. a leftover
    // crossReferenceReturnAvailable spotlight-jumping back to whatever
    // verse the old cross-reference session started from, which could
    // easily be a different verse in the very same chapter the widget had
    // just opened, reading as "back just replayed the same chapter" —
    // before a second back press finally exited normally.
    // clearFocus = false is used only by the widget's "Continue Reading"
    // entry point: init already seeded focusedVerseNumber/pinToTop this
    // same cold start from the persisted exact resume verse (see
    // saveLastReadPosition), and clearing it right back out here would
    // silently drop that seed, landing on the top of the chapter instead
    // of the saved verse — the exact bug this parameter exists to avoid.
    fun clearStaleReaderDetours(clearFocus: Boolean = true) {
        _crossReferenceReturnAvailable.value = false
        _searchReturnAvailable.value = false
        _lexiconReturnTab.value = null
        _noteReturnItem.value = null
        _highlightsReturnAvailable.value = false
        _studiedReturnAvailable.value = false
        // Also clear the source-verse-to-restore-on-exit tracking for
        // search/highlights/studied/notes — otherwise a stale one left over
        // from before backgrounding (see this function's own doc) would
        // never get overwritten (openSearchResult/openHighlightedVerse/
        // markStudiedNavigation/setNoteReturnItem only capture a *new* one
        // when the existing slot is empty), silently restoring the wrong
        // chapter the next time that tab's detour is fully exited.
        _searchSourceVerse.value = null
        _highlightsSourceVerse.value = null
        _studiedSourceVerse.value = null
        _notesSourceVerse.value = null
        if (clearFocus) clearVerseFocus()
    }

    // baseVerse mirrors selectGreekWord/selectHebrewWord's own parameter —
    // the verse this lookup was opened from, recorded into the same
    // _lexiconBaseVerse used to save a source verse alongside a saved word
    // (see toggleSaveCurrentEnglishWord) and to jump back to it (see
    // openReaderForSavedWord). Null for callers with no verse context.
    fun openEnglishWordLookup(word: String, baseVerse: Verse? = null) {
        val cleanWord = word.lowercase().replace(Regex("[^a-z]"), "")
        if (cleanWord.isEmpty()) return
        _selectedEnglishWord.value = cleanWord
        if (baseVerse != null) _lexiconBaseVerse.value = baseVerse
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

    // Bookmark toggle for the Save button on the English dictionary sheet
    // — see BibleRepository.toggleSavedWord's doc. Concatenates every
    // part-of-speech's definitions into one plain-text block since
    // SavedWordItem only has a single definition field (English lookups
    // never had the Greek/Hebrew lexicon's single "definition" string to
    // begin with — meanings is the closest equivalent).
    fun toggleSaveCurrentEnglishWord() {
        val word = _selectedEnglishWord.value ?: return
        val entry = _dictionaryEntry.value
        val definition = entry?.meanings.orEmpty().joinToString("\n\n") { meaning ->
            val body = meaning.definitions.joinToString("\n") { "• $it" }
            if (meaning.partOfSpeech.isNotBlank()) "${meaning.partOfSpeech}\n$body" else body
        }
        val baseVerse = _lexiconBaseVerse.value
        viewModelScope.launch {
            repository.toggleSavedWord(
                SavedWordItem(
                    language = SavedWordLanguage.ENGLISH,
                    word = word,
                    definition = definition,
                    sourceBook = baseVerse?.book.orEmpty(),
                    sourceChapter = baseVerse?.chapter ?: 0,
                    sourceVerse = baseVerse?.number ?: 0
                )
            )
        }
    }

    fun setSelectedVerse(verse: Verse?) {
        _selectedVerse.value = verse
    }

    // The verse a Greek/Hebrew word lookup was opened from — set by
    // selectGreekWord/selectHebrewWord whenever a word is tapped, read back
    // by closeGreekWordPage/closeHebrewWordPage (including via system back —
    // see MainActivity) to land back on that exact verse instead of the top
    // of the chapter. Reader's own LazyListState is fully disposed while
    // GreekWordScreen/HebrewWordScreen is showing (AnimatedContent tears
    // down the inactive tab's composable), so without this, switching back
    // to the Reader tab has nothing to restore its scroll position from.
    //
    // Stored as a full Verse (not just a verse number): a citation tapped
    // inside the definition can jump the Reader to a different book/
    // chapter entirely (see openVerseMentionPreview's "Open in Reader"),
    // so by the time either closeGreekWordPage/closeHebrewWordPage or
    // backToLexiconOriginVerse runs, currentBook/currentChapter no longer
    // necessarily match the chapter this word was originally tapped in —
    // a bare verse number would silently resolve against the wrong
    // chapter's verse list.
    private val _lexiconBaseVerse = MutableStateFlow<Verse?>(null)

    // Opens GreekWordScreen for this word — same "land on a full page,
    // fetch lazily" shape as CrossReferenceScreen/openCrossReferences.
    // Passing null instead clears the selection without switching tabs
    // (used internally by closeGreekWordPage(), and safe to leave as a
    // dedicated no-op path for any future non-navigating "just clear it"
    // caller); baseVerse is only meaningful on that real-selection path.
    fun selectGreekWord(greekWord: GreekWord?, baseVerse: Verse? = null) {
        _selectedGreekWord.value = greekWord
        lexiconLookupJob?.cancel()
        _lexiconResult.value = null
        _isLoadingLexicon.value = false
        if (greekWord == null) return
        if (baseVerse != null) _lexiconBaseVerse.value = baseVerse

        _greekWordScrollPosition.value = 0
        _isLoadingLexicon.value = true
        lexiconLookupJob = viewModelScope.launch {
            val result = repository.getLexiconEntry(greekWord.strongs)
            _lexiconResult.value = result
            _isLoadingLexicon.value = false
        }
        selectTab(NavTab.GREEK_WORD)
    }

    // GreekWordScreen's own back button — mirrors endCrossReferenceSession()
    // + selectTab(READER), plus focusing the base verse (see
    // jumpToLexiconBaseVerse).
    fun closeGreekWordPage() {
        selectGreekWord(null)
        jumpToLexiconBaseVerse()
        selectTab(NavTab.READER)
    }

    // Bookmark toggle for GreekWordScreen's Save action — see
    // BibleRepository.toggleSavedWord's doc. sourceBook/Chapter/Verse come
    // from _lexiconBaseVerse (the verse this lookup was opened from), the
    // same anchor closeGreekWordPage uses to jump back.
    fun toggleSaveCurrentGreekWord() {
        val word = _selectedGreekWord.value ?: return
        val foundEntry = (_lexiconResult.value as? LexiconLookupResult.Found)?.entry
        val baseVerse = _lexiconBaseVerse.value
        viewModelScope.launch {
            repository.toggleSavedWord(
                SavedWordItem(
                    language = SavedWordLanguage.GREEK,
                    word = word.greek,
                    transliteration = word.transliteration,
                    gloss = word.englishGloss.ifBlank { foundEntry?.gloss.orEmpty() },
                    definition = foundEntry?.definition.orEmpty(),
                    morphology = word.morphology,
                    strongs = word.strongs,
                    sourceBook = baseVerse?.book.orEmpty(),
                    sourceChapter = baseVerse?.chapter ?: 0,
                    sourceVerse = baseVerse?.number ?: 0
                )
            )
        }
    }

    fun selectHebrewWord(hebrewWord: HebrewWord?, baseVerse: Verse? = null) {
        _selectedHebrewWord.value = hebrewWord
        hebrewLexiconLookupJob?.cancel()
        _hebrewLexiconResult.value = null
        _isLoadingHebrewLexicon.value = false
        if (hebrewWord == null) return
        if (baseVerse != null) _lexiconBaseVerse.value = baseVerse

        _hebrewWordScrollPosition.value = 0
        _isLoadingHebrewLexicon.value = true
        hebrewLexiconLookupJob = viewModelScope.launch {
            val result = repository.getHebrewLexiconEntry(hebrewWord.strongs)
            _hebrewLexiconResult.value = result
            _isLoadingHebrewLexicon.value = false
        }
        selectTab(NavTab.HEBREW_WORD)
    }

    // Bookmark toggle for HebrewWordScreen's Save action — mirrors
    // toggleSaveCurrentGreekWord above.
    fun toggleSaveCurrentHebrewWord() {
        val word = _selectedHebrewWord.value ?: return
        val foundEntry = (_hebrewLexiconResult.value as? LexiconLookupResult.Found)?.entry
        val baseVerse = _lexiconBaseVerse.value
        viewModelScope.launch {
            repository.toggleSavedWord(
                SavedWordItem(
                    language = SavedWordLanguage.HEBREW,
                    word = word.hebrew,
                    transliteration = word.transliteration,
                    gloss = word.englishGloss.ifBlank { foundEntry?.gloss.orEmpty() },
                    definition = foundEntry?.definition.orEmpty(),
                    morphology = word.morphology,
                    strongs = word.strongs,
                    sourceBook = baseVerse?.book.orEmpty(),
                    sourceChapter = baseVerse?.chapter ?: 0,
                    sourceVerse = baseVerse?.number ?: 0
                )
            )
        }
    }

    fun closeHebrewWordPage() {
        selectHebrewWord(null)
        jumpToLexiconBaseVerse()
        selectTab(NavTab.READER)
    }

    // Shared by closeGreekWordPage/closeHebrewWordPage and
    // backToLexiconOriginVerse below: spotlight-jump back to the verse the
    // lookup was opened from — across book/chapter if a followed citation
    // moved the Reader elsewhere (see _lexiconBaseVerse's doc) — via the
    // same real "jump to verse X" path used by cross-reference/search
    // jumps (see jumpToVerse's doc), rather than just poking
    // focusedVerseNumber. A no-op if somehow no base verse was recorded,
    // rather than forcing a jump to nothing.
    private fun jumpToLexiconBaseVerse() {
        val base = _lexiconBaseVerse.value
        _lexiconBaseVerse.value = null
        if (base != null) {
            jumpToVerse(base.book, base.chapter, base.number)
            _isBlurModeEnabled.value = false
        }
    }

    // System back while Reader shows the "Return to Greek/Hebrew word"
    // banner — undoes the whole lexicon detour and lands back on the verse
    // the word lookup was originally opened from, rather than the lexicon
    // definition page (which is what the banner's own Return button does —
    // see returnToLexicon()). That's system back's conventional meaning
    // ("take me back to where I was"), distinct from the button's
    // deliberate "let me pick another reference from here" — see
    // MainActivity's back-handling comment for the parallel
    // cross-reference case. Also clears whichever word was selected so a
    // stray later tab switch into Greek/Hebrew word doesn't resurrect a
    // stale definition.
    fun backToLexiconOriginVerse() {
        _lexiconReturnTab.value = null
        selectGreekWord(null)
        selectHebrewWord(null)
        jumpToLexiconBaseVerse()
    }

    // Opens CrossReferenceScreen for this verse — same shape as a search:
    // fetch the list, land on the page. Remember the verse the xrefs were
    // opened *from*, independent of _selectedVerse — the dagger marker
    // (onCrossReferenceMarkerClick) opens xrefs without ever selecting the
    // verse.
    fun openCrossReferences(verse: Verse) {
        _crossReferenceSourceVerse.value = verse
        // Reset to "loading" (null) rather than leaving a previous verse's
        // list on screen — otherwise re-opening for a different verse
        // could briefly show the old list under the new source-verse
        // header until the fetch below resolves.
        _crossReferenceList.value = null
        _crossReferenceScrollIndex.value = 0
        _crossReferenceScrollOffset.value = 0
        viewModelScope.launch {
            _crossReferenceList.value = repository.getCrossReferences(verse.book, verse.chapter, verse.number)
        }
        selectTab(NavTab.CROSS_REFERENCES)
    }

    // Hops back to CrossReferenceScreen from the Reader's "Return to cross
    // references" banner — same scroll position, same list. Mirrors
    // returnToSearchResults(). This is the banner's own Return button;
    // system back does something different — see
    // backToCrossReferenceSourceVerse below.
    fun returnToCrossReferences() {
        _crossReferenceReturnAvailable.value = false
        selectTab(NavTab.CROSS_REFERENCES)
    }

    // System back while Reader shows the "Return to cross references"
    // banner — undoes the whole cross-reference detour and lands back on
    // the verse reading started from (e.g. Romans 2:3), rather than the
    // cross-reference list (Romans 11:2's references), which is what the
    // banner's own Return button goes to. That's system back's
    // conventional meaning ("take me back to where I was"), distinct from
    // the button's deliberate "let me pick another reference from here."
    // Ends the session too, same as dismissCrossReferenceReturnBanner():
    // there's no list left to come back to once you've backed out past it.
    fun backToCrossReferenceSourceVerse() {
        val source = _crossReferenceSourceVerse.value
        _crossReferenceReturnAvailable.value = false
        endCrossReferenceSession()
        if (source != null) {
            jumpToVerse(source.book, source.chapter, source.number)
            _isBlurModeEnabled.value = false
        }
    }

    // "Cancel" on the banner — end the session (mirrors
    // dismissSearchReturnBanner()/endSearchSession()) rather than just
    // hiding the banner and leaving a stale list around for next time.
    fun dismissCrossReferenceReturnBanner() {
        _crossReferenceReturnAvailable.value = false
        endCrossReferenceSession()
    }

    // Ends the whole cross-reference session: list, source verse, scroll
    // position, everything reset. Called when the user explicitly leaves
    // CrossReferenceScreen via its own back button, or dismisses the
    // "Return to cross references" banner.
    fun endCrossReferenceSession() {
        _crossReferenceList.value = null
        _crossReferenceSourceVerse.value = null
        _crossReferenceScrollIndex.value = 0
        _crossReferenceScrollOffset.value = 0
        _crossReferenceLastTappedKey.value = null
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
                _selectedVerse.value = null
            } else {
                repository.setHighlight(book, chapter, verse, colorHex)
                // Toolbar deliberately stays open here (unlike Clear above)
                // — a fresh highlight can optionally be followed by a quick
                // note (see addHighlightQuickNote) right in the same
                // toolbar, without a separate detour into the full Note
                // Editor. Dismisses the same way any other verse-selection
                // state already does: the toolbar's own Close button, or
                // tapping the verse again.
            }
        }
    }

    // "Add a quick note" row on the verse action toolbar, shown once a
    // highlight is active and the verse has no notes yet — creates a real
    // NoteItem (tagged with the highlight color's own label, linked to this
    // verse) rather than a separate, duplicate content store, and links it
    // back via HighlightItem.noteId so the Highlighted Verses browser can
    // show a preview of it. Kept out of the full Note Editor screen
    // deliberately: this is meant to stay a one-line "why did I mark this"
    // comment, not a detour into title/tags/full editor for what's usually
    // a single sentence.
    fun addHighlightQuickNote(
        book: String,
        chapter: Int,
        verse: Int,
        verseText: String,
        colorHex: String,
        colorLabel: String,
        noteText: String
    ) {
        val trimmed = noteText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val noteId = System.currentTimeMillis()
            repository.saveNote(
                id = noteId,
                title = "",
                text = trimmed,
                noteDate = "",
                refs = listOf(NoteReference(book, chapter, verse, verseText)),
                tags = listOf("Highlighted Verse", colorLabel)
            )
            repository.setHighlight(book, chapter, verse, colorHex, noteId = noteId)
        }
    }

    // Rename a highlight color's label — the one piece of the old "Manage
    // Highlight Colors" flow kept after the rest (add/delete/enable-disable)
    // was removed in favor of the fixed palette; see model/HighlightColors.kt.
    fun renameHighlightColor(colorHex: String, newLabel: String) {
        viewModelScope.launch { repository.renameHighlightColor(colorHex, newLabel) }
    }

    // Which tab NoteReaderScreen was opened from — the Notes tab (tapping a
    // note card there) or straight from Reader (the verse action toolbar's
    // "View notes", when a verse has exactly one existing note and there's
    // no need to detour through the Notes list at all). returnToNote()
    // needs this: following a verse mention out of a note that was opened
    // the second way, then coming back, previously always switched to the
    // Notes tab regardless — dropping the user somewhere they'd never
    // actually visited instead of back on Reader where they started.
    private val _noteReaderOriginTab = MutableStateFlow(NavTab.NOTES)

    fun openNoteReader(note: NoteItem, originTab: NavTab = NavTab.NOTES) {
        _noteReaderOriginTab.value = originTab
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

    // ── Saved Words screen ──────────────────────────────────────────────
    // Full page pushed over Search (opened from its top-bar bookmark icon),
    // same "overlay above the active tab" shape as Tags above — back
    // returns to Search, not Reader.
    private val _showSavedWordsScreen = MutableStateFlow(false)
    val showSavedWordsScreen: StateFlow<Boolean> = _showSavedWordsScreen.asStateFlow()

    fun openSavedWordsScreen() { _showSavedWordsScreen.value = true }
    fun closeSavedWordsScreen() { _showSavedWordsScreen.value = false }

    fun deleteSavedWord(id: Long) { viewModelScope.launch { repository.deleteSavedWord(id) } }

    // Tapping a Saved Words row reopens its full lexicon/dictionary entry
    // rather than just jumping to the verse — "Open in Reader" below is
    // the separate, explicit action for that. Greek/Hebrew are full-page
    // NavTab screens layered under the Saved Words overlay, so opening one
    // closes the overlay first (otherwise it would render behind it,
    // invisible); the English sheet is a bottom sheet independent of both
    // the active tab and this overlay, so it can show right on top of
    // Saved Words without closing anything. Closing the resulting
    // Greek/Hebrew page lands back in Reader at the source verse (if any)
    // — the same place it always returns to — not back in Saved Words.
    fun openLexiconForSavedWord(item: SavedWordItem) {
        val sourceVerse = if (item.sourceBook.isNotBlank()) {
            Verse(book = item.sourceBook, chapter = item.sourceChapter, number = item.sourceVerse, text = "")
        } else null
        when (item.language) {
            SavedWordLanguage.GREEK -> {
                closeSavedWordsScreen()
                selectGreekWord(
                    GreekWord(
                        greek = item.word,
                        transliteration = item.transliteration,
                        englishGloss = item.gloss,
                        strongs = item.strongs,
                        morphology = item.morphology
                    ),
                    baseVerse = sourceVerse
                )
            }
            SavedWordLanguage.HEBREW -> {
                closeSavedWordsScreen()
                selectHebrewWord(
                    HebrewWord(
                        hebrew = item.word,
                        transliteration = item.transliteration,
                        englishGloss = item.gloss,
                        strongs = item.strongs,
                        morphology = item.morphology
                    ),
                    baseVerse = sourceVerse
                )
            }
            SavedWordLanguage.ENGLISH -> openEnglishWordLookup(item.word, baseVerse = sourceVerse)
        }
    }

    // 3-dot menu's "Open in Reader" — jumps straight to the verse a saved
    // word came from, bypassing its lexicon/dictionary entry entirely.
    // No-op if this word has no recorded source verse (the menu item
    // itself is disabled in that case — see SavedWordsScreen).
    fun openReaderForSavedWord(item: SavedWordItem) {
        if (item.sourceBook.isBlank()) return
        closeSavedWordsScreen()
        jumpToVerse(item.sourceBook, item.sourceChapter, item.sourceVerse)
        selectTab(NavTab.READER)
    }

    fun setShowBookPicker(show: Boolean) {
        _showBookPicker.value = show
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
        val outcome = repository.searchBible(query, caseSensitive = _searchCaseSensitive.value)
        _searchResults.value = outcome.mainResults
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
        _searchLastTappedKey.value = null
        _searchSourceVerse.value = null
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
        if (_searchSourceVerse.value == null) {
            // readerAnchor (saved by ReaderScreen's own teardown effect when
            // leaving for the Search tab) has the exact verse if it's still
            // fresh for this book/chapter; falling back to the chapter's
            // top verse is fine here — the point is restoring the right
            // chapter, not necessarily the exact scroll pixel.
            val anchor = _readerAnchor.value
            val sourceVerseNumber = anchor?.takeIf {
                it.book == _currentBook.value && it.chapter == _currentChapter.value
            }?.verse ?: 1
            _searchSourceVerse.value = ReaderScrollAnchor(_currentBook.value, _currentChapter.value, sourceVerseNumber)
        }
        _searchReturnAvailable.value = true
        _searchLastTappedKey.value = "${verse.book}:${verse.chapter}:${verse.number}"
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

    // System back while Reader shows the "Return to search results" banner
    // — undoes the whole search detour and lands back on the verse reading
    // started from (e.g. Romans 4), rather than the search results list,
    // which is what the banner's own Return button goes to. Mirrors
    // backToCrossReferenceSourceVerse's distinction between the button and
    // system back. Ends the session too, same reasoning as
    // dismissSearchReturnBanner(): there's no list left to come back to
    // once you've backed out past it.
    fun backToSearchSourceVerse() {
        val source = _searchSourceVerse.value
        _searchReturnAvailable.value = false
        endSearchSession()
        if (source != null) {
            jumpToVerse(source.book, source.chapter, source.verse)
            _isBlurModeEnabled.value = false
        }
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
                    "${result.colorsRelabeled} colors relabeled"
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
                            "${importResult.colorsRelabeled} colors relabeled"
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
