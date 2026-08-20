package com.example.mybible

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.ReaderPickMode
import com.example.mybible.ui.components.*
import com.example.mybible.ui.screens.*
import com.example.mybible.ui.theme.MyBibleTheme
import com.example.mybible.widget.WidgetActionKeys
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: MainViewModel = viewModel()
            val context = LocalContext.current

            // POST_NOTIFICATIONS (Android 13+) is required for the reminder
            // notifications to actually display; ReminderScheduler.setEnabled
            // itself doesn't request it since that needs an Activity. Alarms
            // still get scheduled either way — if permission is denied here,
            // they'll just silently not show until the user grants it later
            // (e.g. from system Settings), no re-toggle needed.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> viewModel.setRemindersEnabled(true) }

            val coroutineScope = rememberCoroutineScope()

            // ---- Backup & Sync: local file (SAF) + Google Drive ----

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    coroutineScope.launch {
                        val backupJson = viewModel.exportBackupJson()
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(backupJson.toByteArray(Charsets.UTF_8))
                            }
                        } catch (e: Exception) {
                            // Nothing more actionable to do — the file picker itself
                            // already handles permission/cancel cases before this runs.
                        }
                    }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    coroutineScope.launch {
                        val text = try {
                            context.contentResolver.openInputStream(uri)
                                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        } catch (e: Exception) {
                            null
                        }
                        if (text != null) viewModel.importFromBackupJson(text)
                    }
                }
            }

            // A consent (or re-consent) prompt for the drive.appdata scope can
            // interrupt either backup or restore; whichever one triggered it is
            // stashed here and re-run once the user grants access, so the retry
            // is indistinguishable from having granted it up front.
            var pendingDriveRetry by remember { mutableStateOf<(() -> Unit)?>(null) }

            val driveConsentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { _ ->
                val retry = pendingDriveRetry
                pendingDriveRetry = null
                retry?.invoke()
            }

            val driveSignInLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result -> viewModel.handleDriveSignInResult(result.data) }

            fun runDriveBackup() {
                viewModel.backupToDrive(onNeedsConsent = { intent ->
                    pendingDriveRetry = ::runDriveBackup
                    driveConsentLauncher.launch(intent)
                })
            }

            fun runDriveRestore() {
                viewModel.restoreFromDrive(onNeedsConsent = { intent ->
                    pendingDriveRetry = ::runDriveRestore
                    driveConsentLauncher.launch(intent)
                })
            }

            val onToggleReminders: (Boolean) -> Unit = onToggle@{ enabled ->
                if (!enabled) {
                    viewModel.setRemindersEnabled(false)
                    return@onToggle
                }
                val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsRuntimePermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setRemindersEnabled(true)
                }
            }

            // Resolves widget-launch extras into initial nav state.
            //
            // This is deliberately `remember(intent) { ... }`, not
            // `LaunchedEffect(intent)`. LaunchedEffect runs *after* the first
            // composition commits, so on a cold launch from the widget the
            // very first frame rendered whatever activeTab's ViewModel
            // default was (Reader) before the effect fired and switched
            // tabs — a visible flash of Reader before the requested tab.
            // `remember` runs synchronously as part of composition, so by
            // the time `viewModel.activeTab.collectAsState()` below reads
            // the StateFlow, selectTab() has already mutated it and the
            // first frame renders the correct tab directly. None of the
            // calls here are suspend functions, so no coroutine is needed.
            remember(intent) {
                // VerseOfDayWidget (Glance) extras — see widget/WidgetActionKeys.kt.
                val verseBook = intent.getStringExtra(WidgetActionKeys.EXTRA_VERSE_BOOK)
                val verseChapter = intent.getIntExtra(WidgetActionKeys.EXTRA_VERSE_CHAPTER, -1)
                if (!verseBook.isNullOrEmpty() && verseChapter != -1) {
                    viewModel.loadChapter(verseBook, verseChapter)
                    viewModel.selectTab(NavTab.READER)
                    return@remember Unit
                }

                if (intent.getBooleanExtra(WidgetActionKeys.EXTRA_CONTINUE_READING, false)) {
                    // App already resumes the last read position on normal
                    // launch, so no explicit action is needed here beyond
                    // making sure Reader is the visible tab.
                    viewModel.selectTab(NavTab.READER)
                    return@remember Unit
                }

                // Quick-action icon row on the widget (Highlights/Studied/
                // Notes/Search) — jumps straight to that tab.
                val openTabName = intent.getStringExtra(WidgetActionKeys.EXTRA_OPEN_TAB)
                if (!openTabName.isNullOrEmpty()) {
                    val tab = try { NavTab.valueOf(openTabName) } catch (e: IllegalArgumentException) { null }
                    if (tab != null) viewModel.selectTab(tab)
                }
                Unit
            }

            val themeMode by viewModel.themeMode.collectAsState()
            val activeTab by viewModel.activeTab.collectAsState()

            val showBookPicker by viewModel.showBookPicker.collectAsState()
            val currentBook by viewModel.currentBook.collectAsState()
            val selectedEnglishWord by viewModel.selectedEnglishWord.collectAsState()
            val dictionaryEntry by viewModel.dictionaryEntry.collectAsState()
            val isLoadingDictionary by viewModel.isLoadingDictionary.collectAsState()
            val showNoteEditor by viewModel.showNoteEditor.collectAsState()
            val showTagsScreen by viewModel.showTagsScreen.collectAsState()
            val noteToEdit by viewModel.noteToEdit.collectAsState()
            val noteToRead by viewModel.noteToRead.collectAsState()
            val readerPickMode by viewModel.readerPickMode.collectAsState()
            val tagDefinitions by viewModel.tagDefinitions.collectAsState(initial = emptyList())
            val showOnboarding by viewModel.showOnboarding.collectAsState()
            val importProgress by viewModel.importProgress.collectAsState()
            val importError by viewModel.importError.collectAsState()

            var manualShowTour by remember { mutableStateOf(false) }

            // Hardware/gesture back returns to Reader from any destination
            // screen, instead of backgrounding the app — Reader is "home"
            // now that there's no persistent tab bar to tap back with.
            BackHandler(enabled = activeTab != NavTab.READER) {
                viewModel.selectTab(NavTab.READER)
            }

            // Note editor/reader are now full pages pushed over the Notes
            // list (see below) instead of bottom sheets, so — like the
            // Book/Chapter picker — they need their own back handling.
            // These are registered after the tab handler above, which
            // gives them priority while visible: back closes the note
            // page first and returns to the Notes list, only falling
            // through to "back to Reader" once no note page is open.
            BackHandler(enabled = showNoteEditor) {
                viewModel.closeNoteEditor()
            }
            BackHandler(enabled = showTagsScreen) {
                viewModel.closeTagsScreen()
            }
            BackHandler(enabled = noteToRead != null) {
                viewModel.closeNoteReader()
            }

            // Picking mode (note-verse picking or studied-verse picking)
            // takes over the Reader with a banner — back cancels it the
            // same way tapping the banner's own Cancel/Done would, rather
            // than backgrounding the app or falling through to some other
            // handler. Registered after the note editor/reader handlers
            // above since picking mode and the editor are never both
            // visible at once, but ordering keeps intent explicit.
            BackHandler(enabled = readerPickMode != ReaderPickMode.NONE) {
                viewModel.cancelPicking()
            }

            MyBibleTheme(themeMode = themeMode) {
                // No Scaffold topBar/bottomBar at the app level anymore.
                // Reader supplies its own floating pill and owns the full
                // screen edge-to-edge (see ReaderScreen) — there's no bar
                // height for a Scaffold to reserve space for, which is what
                // caused the leftover whitespace when bars auto-hid before.
                // Notes/Studied/Search/Settings each carry their own small
                // back-arrow top bar (BackTopBar) since they're destinations
                // you drill into from Reader's pill, not sibling tabs.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        label = "TabTransition"
                    ) { tab ->
                        when (tab) {
                            NavTab.READER -> ReaderScreen(viewModel = viewModel)
                            NavTab.STUDIED -> StudiedScreen(viewModel = viewModel)
                            NavTab.NOTES -> NotesScreen(viewModel = viewModel)
                            NavTab.SEARCH -> SearchScreen(viewModel = viewModel)
                            NavTab.CROSS_REFERENCES -> CrossReferenceScreen(viewModel = viewModel)
                            NavTab.GREEK_WORD -> GreekWordScreen(viewModel = viewModel)
                            NavTab.HEBREW_WORD -> HebrewWordScreen(viewModel = viewModel)
                            NavTab.HIGHLIGHTS -> {
                                val highlightedItems by viewModel.highlightedVerseItems.collectAsState()
                                HighlightedVersesScreen(
                                    highlights = highlightedItems,
                                    onOpenVerse = { viewModel.openHighlightedVerse(it) },
                                    onClose = { viewModel.selectTab(NavTab.READER) }
                                )
                            }
                            NavTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onShowTour = { manualShowTour = true },
                                onToggleReminders = onToggleReminders,
                                onExportLocal = {
                                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    exportLauncher.launch("mybible_backup_$stamp.json")
                                },
                                onImportLocal = { importLauncher.launch(arrayOf("application/json")) },
                                onDriveSignIn = { driveSignInLauncher.launch(viewModel.getDriveSignInIntent()) },
                                onDriveBackup = { runDriveBackup() },
                                onDriveRestore = { runDriveRestore() }
                            )
                        }
                    }

                    // Bible data import banner — bundled/downloaded Telugu
                    // + KJV text loading into Room on first launch.
                    val progress = importProgress
                    if (progress != null) {
                        BibleDataImportBanner(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            label = progress.label,
                            detail = progress.detail,
                            done = progress.done,
                            total = progress.total
                        )
                    } else if (importError != null) {
                        BibleDataImportErrorBanner(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            message = importError!!,
                            onRetry = { viewModel.retryBibleDataImport() }
                        )
                    }

                    // Book/Chapter Picker — full-screen, matching Capacitor's
                    // #picker (a fixed, full-viewport panel that slides up
                    // over everything, not a partial-height sheet).
                    AnimatedVisibility(
                        visible = showBookPicker,
                        enter = slideInVertically(
                            animationSpec = tween(340),
                            initialOffsetY = { fullHeight -> fullHeight }
                        ),
                        exit = slideOutVertically(
                            animationSpec = tween(340),
                            targetOffsetY = { fullHeight -> fullHeight }
                        )
                    ) {
                        BookChapterPickerSheet(
                            currentBook = currentBook,
                            onDismiss = { viewModel.setShowBookPicker(false) },
                            onSelectBookAndChapter = { book, chap ->
                                // Clears any stale "spotlight this verse"
                                // target from an earlier jump so a plain
                                // book/chapter browse (no specific verse)
                                // doesn't inherit it — see clearVerseFocus()'s
                                // doc for what that broke previously.
                                viewModel.clearVerseFocus()
                                viewModel.disableBlurModeForNavigation()
                                viewModel.loadChapter(book, chap)
                                viewModel.setShowBookPicker(false)
                            },
                            onSelectVerse = { book, chap, verse ->
                                viewModel.disableBlurModeForNavigation()
                                viewModel.jumpToVerse(book, chap, verse)
                                viewModel.setShowBookPicker(false)
                            },
                            getVerseCount = { book, chap -> viewModel.getVerseCount(book, chap) }
                        )
                    }

                    // Manage Highlight Colors Sheet — opened from the verse
                    // action toolbar's "Manage" swatch.
                    val showManageHighlightColors by viewModel.showManageHighlightColors.collectAsState()
                    val highlightColorDefsForManage by viewModel.highlightColorDefs.collectAsState(initial = emptyList())
                    if (showManageHighlightColors) {
                        ManageHighlightColorsSheet(
                            colorDefs = highlightColorDefsForManage,
                            onRename = { colorHex, newLabel -> viewModel.renameHighlightColor(colorHex, newLabel) },
                            onSetEnabled = { colorHex, enabled -> viewModel.setHighlightColorEnabled(colorHex, enabled) },
                            onDelete = { colorHex -> viewModel.deleteHighlightColor(colorHex) },
                            onAdd = { label, colorHex -> viewModel.addHighlightColor(label, colorHex) },
                            onDismiss = { viewModel.setShowManageHighlightColors(false) }
                        )
                    }

                    // English Dictionary Sheet
                    if (selectedEnglishWord != null) {
                        EnglishDictionarySheet(
                            word = selectedEnglishWord!!,
                            entry = dictionaryEntry,
                            isLoading = isLoadingDictionary,
                            onDismiss = { viewModel.dismissEnglishWordSheet() }
                        )
                    }

                    // Verse-mention preview sheet — opened by tapping a
                    // linkified reference inside note text (Notes list card
                    // or Note Reader body). Matches Capacitor's
                    // #verseTextSheet: shows the verse without leaving the
                    // note; "Open in Reader" is a separate explicit tap
                    // that also closes any open note screen, matching
                    // vtsOpenReader's onclick clearing closeNotesList()/
                    // closeNoteEditor() before loading the chapter.
                    val verseMentionPreview by viewModel.verseMentionPreview.collectAsState()
                    if (verseMentionPreview != null) {
                        val preview = verseMentionPreview!!
                        VerseMentionPreviewSheet(
                            book = preview.book,
                            chapter = preview.chapter,
                            verse = preview.verse,
                            verseText = preview.text,
                            onOpenInReader = {
                                viewModel.closeVerseMentionPreview()
                                viewModel.closeNoteReader()
                                viewModel.closeNoteEditor()
                                viewModel.disableBlurModeForNavigation()
                                viewModel.jumpToVerse(preview.book, preview.chapter, preview.verse)
                                viewModel.selectTab(NavTab.READER)
                            },
                            onDismiss = { viewModel.closeVerseMentionPreview() }
                        )
                    }

                    // Note Reader — full page, slides up over Notes just
                    // like the Book/Chapter picker does over Reader.
                    // `lastReadNote` keeps the most recent non-null note
                    // around through the exit animation — `noteToRead`
                    // itself flips to null the instant close is requested,
                    // and without this the page would blank out mid-slide
                    // instead of sliding away with its content intact.
                    // Updated synchronously during composition (not via
                    // LaunchedEffect) so NoteReaderScreen never composes even
                    // one frame with a stale note — LaunchedEffect runs after
                    // composition commits, which was late enough to flash the
                    // previous note's content before this one.
                    var lastReadNote by remember { mutableStateOf(noteToRead) }
                    if (noteToRead != null) {
                        lastReadNote = noteToRead
                    }
                    AnimatedVisibility(
                        visible = noteToRead != null,
                        enter = slideInVertically(
                            animationSpec = tween(300),
                            initialOffsetY = { fullHeight -> fullHeight }
                        ),
                        exit = slideOutVertically(
                            animationSpec = tween(300),
                            targetOffsetY = { fullHeight -> fullHeight }
                        )
                    ) {
                        val note = lastReadNote
                        if (note != null) {
                            NoteReaderScreen(
                                noteItem = note,
                                onBack = { viewModel.closeNoteReader() },
                                // Matches Capacitor's nrEditPage.onclick:
                                // closeNoteReader(); closeNotesList();
                                // openNoteEditor(n) — this app's Notes tab
                                // has no separate "list" overlay to close,
                                // so just close the reader and open the
                                // editor for this note.
                                onEdit = {
                                    viewModel.closeNoteReader()
                                    viewModel.openNoteEditor(note)
                                },
                                onOpenVerseMention = { book, chapter, verse ->
                                    viewModel.openVerseMentionPreview(book, chapter, verse)
                                }
                            )
                        }
                    }

                    // Note Editor — full page for both "Add new note" and
                    // editing an existing one, instead of a bottom modal.
                    // Same last-value trick as the reader above, keyed off
                    // showNoteEditor rather than the note itself so a brand
                    // new note (id == 0) still counts as "has content".
                    // Synchronous update during composition, not a
                    // LaunchedEffect — see the identical fix/comment on
                    // lastReadNote above. With the effect-based version,
                    // NoteEditorScreen's onAddNote -> openNoteEditor(verse)
                    // could compose once with the previous note still in
                    // lastEditingNote (LaunchedEffect hadn't run yet), and
                    // since NoteEditorScreen seeds its title/text/refs from
                    // noteItem via unkeyed remember{}, that stale first frame
                    // got baked in permanently instead of self-correcting.
                    var lastEditingNote by remember { mutableStateOf(noteToEdit) }
                    if (showNoteEditor && noteToEdit != null) {
                        lastEditingNote = noteToEdit
                    }
                    AnimatedVisibility(
                        visible = showNoteEditor && noteToEdit != null,
                        enter = slideInVertically(
                            animationSpec = tween(300),
                            initialOffsetY = { fullHeight -> fullHeight }
                        ),
                        exit = slideOutVertically(
                            animationSpec = tween(300),
                            targetOffsetY = { fullHeight -> fullHeight }
                        )
                    ) {
                        val editing = lastEditingNote
                        if (editing != null) {
                            NoteEditorScreen(
                                noteItem = editing,
                                onSave = { title, text, noteDate, refs, tags ->
                                    viewModel.saveNote(title, text, noteDate, refs, tags)
                                },
                                onCancel = { viewModel.closeNoteEditor() },
                                onDelete = {
                                    if (editing.id > 0) {
                                        viewModel.deleteNote(editing.id)
                                    }
                                    viewModel.closeNoteEditor()
                                },
                                onResolveVerseText = { ref ->
                                    viewModel.resolveNoteReferenceText(ref)
                                },
                                onAddAnotherVerse = { draft ->
                                    viewModel.addAnotherVerseToDraft(draft)
                                },
                                onPreviewReference = { book, chapter, verse ->
                                    viewModel.openVerseMentionPreview(book, chapter, verse)
                                },
                                knownTags = tagDefinitions.map { it.name }
                            )
                        }
                    }

                    // Tags screen — full page pushed over Notes, same
                    // slide-up treatment as the Note editor/reader above.
                    // Unlike those, it has no per-item "current tag" state
                    // to preserve across the exit animation, so it doesn't
                    // need the lastValue-during-composition trick.
                    AnimatedVisibility(
                        visible = showTagsScreen,
                        enter = slideInVertically(
                            animationSpec = tween(300),
                            initialOffsetY = { fullHeight -> fullHeight }
                        ),
                        exit = slideOutVertically(
                            animationSpec = tween(300),
                            targetOffsetY = { fullHeight -> fullHeight }
                        )
                    ) {
                        TagsScreen(viewModel = viewModel)
                    }

                    // Onboarding Tour Dialog
                    if (showOnboarding || manualShowTour) {
                        OnboardingTourDialog(
                            onDismiss = {
                                viewModel.dismissOnboarding()
                                manualShowTour = false
                            }
                        )
                    }
                }
            }
        }
    }
}
