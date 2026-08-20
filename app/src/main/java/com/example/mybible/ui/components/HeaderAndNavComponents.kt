package com.example.mybible.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.ThemeMode

val BIBLE_BOOKS = listOf(
    "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
    "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah",
    "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah",
    "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah",
    "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi",
    "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
    "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians",
    "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter",
    "1 John", "2 John", "3 John", "Jude", "Revelation"
)

val BOOK_CHAPTER_COUNTS = mapOf(
    "Genesis" to 50, "Exodus" to 40, "Leviticus" to 27, "Numbers" to 36, "Deuteronomy" to 34,
    "Joshua" to 24, "Judges" to 21, "Ruth" to 4, "1 Samuel" to 31, "2 Samuel" to 24,
    "1 Kings" to 22, "2 Kings" to 25, "1 Chronicles" to 29, "2 Chronicles" to 36, "Ezra" to 10,
    "Nehemiah" to 13, "Esther" to 10, "Job" to 42, "Psalms" to 150, "Proverbs" to 31,
    "Ecclesiastes" to 12, "Song of Solomon" to 8, "Isaiah" to 66, "Jeremiah" to 52, "Lamentations" to 5,
    "Ezekiel" to 48, "Daniel" to 12, "Hosea" to 14, "Joel" to 3, "Amos" to 9, "Obadiah" to 1,
    "Jonah" to 4, "Micah" to 7, "Nahum" to 3, "Habakkuk" to 3, "Zephaniah" to 3, "Haggai" to 2,
    "Zechariah" to 14, "Malachi" to 4, "Matthew" to 28, "Mark" to 16, "Luke" to 24, "John" to 21,
    "Acts" to 28, "Romans" to 16, "1 Corinthians" to 16, "2 Corinthians" to 13, "Galatians" to 6,
    "Ephesians" to 6, "Philippians" to 4, "Colossians" to 4, "1 Thessalonians" to 5, "2 Thessalonians" to 3,
    "1 Timothy" to 6, "2 Timothy" to 4, "Titus" to 3, "Philemon" to 1, "Hebrews" to 13,
    "James" to 5, "1 Peter" to 5, "2 Peter" to 3, "1 John" to 5, "2 John" to 1, "3 John" to 1,
    "Jude" to 1, "Revelation" to 22
)

// Reader no longer has a persistent header/bottom nav (see the floating pill
// in ReaderScreen instead) — Reader is "home" and Notes/Studied/Search/
// Settings are destinations you drill into and back out of, matching the
// Capacitor app's model. This is the minimal shared top bar for those 4
// destination screens: just a back arrow + title, nothing else. Search and
// Settings used to also live as icons in a Reader-specific header, and
// Theme toggling lives inside SettingsScreen itself now.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backContentDescription: String = "Back to Reader",
    backIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ArrowBack,
    // Optional trailing actions (e.g. Save / Edit / Delete) for screens that
    // are pushed as full pages rather than being a Reader-adjacent tab —
    // the Notes editor/reader screens use this instead of bottom buttons.
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = {
                // Serif, not the default Material sans — every other
                // "title" in the app (note titles, DsToggleRow labels, the
                // Reader's verse-ref chip) reads in the same serif family;
                // this was the one holdout still in plain Material type.
                Text(text = title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_reader")) {
                    Icon(imageVector = backIcon, contentDescription = backContentDescription)
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        // Flat hairline instead of Material's shadow-based elevation —
        // matches the line-bordered look used throughout the app (e.g.
        // NoteReaderScreen's own header divider) rather than a drop shadow.
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh, thickness = 1.dp)
    }
}

// Reader's "picking mode" banner — the accent-colored bar shown while the
// user taps verses in the Reader to add them to a note or mark them
// studied. Matches Capacitor's #pickBanner/#selectBanner: fixed at the top
// (same slot as the cross-reference back bar, mutually exclusive with it),
// solid accent background, message on the left, a single pill-outline
// "Done" button on the right. Capacitor shows a second "Cancel" button only
// for the Studied variant (#studiedVSelectBanner) — this app's Studied
// picking is entered from the Reader menu, not from inside the Studied
// verse list, so a single Done (which for STUDY_PICK just closes the
// banner with whatever was toggled already applied) covers both cases; the
// hardware back button remains the Cancel/undo path for NOTE_PICK.
@Composable
fun PickModeBanner(
    message: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "Done"
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            OutlinedButton(
                onClick = onDone,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(doneLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Gold accent for the OT/NT group banners, matching Capacitor's `--gold`
// CSS variable (light/dark values ported verbatim — same constant used for
// the cross-reference dagger marker in VerseComponents).
internal val PickerPaperGold = Color(0xFFA9852F)
internal val PickerDarkGold = Color(0xFFD2A94F)

/**
 * Full-screen book/chapter picker, matching Capacitor's `#picker`: a
 * fixed, full-viewport panel (not a partial-height sheet) that slides up
 * over the whole app. Step 1 is the book list with "OLD TESTAMENT"/"NEW
 * TESTAMENT" group banners and the current book highlighted + auto-scrolled
 * into view on open (Capacitor's `openBookList`/`currentRow.scrollIntoView`).
 * Step 2, after tapping a book, is a 6-column chapter grid with a back row
 * (Capacitor's `openChapterGrid`) — Capacitor also has a verse-level step 3
 * after that, not ported here since this pass is specifically about book
 * navigation parity.
 */
@Composable
fun BookChapterPickerSheet(
    currentBook: String,
    onDismiss: () -> Unit,
    onSelectBookAndChapter: (String, Int) -> Unit,
    onSelectVerse: (String, Int, Int) -> Unit,
    getVerseCount: suspend (String, Int) -> Int,
    modifier: Modifier = Modifier
) {
    var selectedBookName by remember { mutableStateOf<String?>(null) }
    var selectedChapter by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    // Genesis..Malachi are the 39 Old Testament books; Matthew is the first
    // New Testament book — same OT_COUNT boundary Capacitor uses.
    val otCount = remember { BIBLE_BOOKS.indexOf("Matthew") }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val goldColor = if (isDark) PickerDarkGold else PickerPaperGold

    // Instant scroll to the current book the moment the list appears —
    // matches Capacitor's `scrollIntoView({block:'center'})` call right
    // after `picker.classList.add('open')`, not a delayed/animated scroll.
    // Group banners render inside the same item slot as the book that
    // follows them (see BookListStep below), so item index == BIBLE_BOOKS
    // index directly, no offset needed. LazyColumn has no built-in
    // scroll-to-center, so this nudges a few rows up to approximate it.
    LaunchedEffect(Unit) {
        val idx = BIBLE_BOOKS.indexOf(currentBook)
        if (idx >= 0) {
            listState.scrollToItem((idx - 4).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — title + close, matching Capacitor's #pickerHead.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        selectedChapter != null -> "$selectedBookName $selectedChapter"
                        selectedBookName != null -> selectedBookName!!
                        else -> "Books"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("picker_close")
                ) {
                    Text("Close", fontSize = 14.5.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                selectedBookName == null -> BookListStep(
                    listState = listState,
                    otCount = otCount,
                    currentBook = currentBook,
                    goldColor = goldColor,
                    onBookSelected = { selectedBookName = it }
                )
                selectedChapter == null -> ChapterGridStep(
                    bookName = selectedBookName!!,
                    onBack = { selectedBookName = null },
                    onChapterSelected = { chap -> selectedChapter = chap }
                )
                else -> VerseGridStep(
                    bookName = selectedBookName!!,
                    chapter = selectedChapter!!,
                    getVerseCount = getVerseCount,
                    onBack = { selectedChapter = null },
                    onStartOfChapter = { onSelectBookAndChapter(selectedBookName!!, selectedChapter!!) },
                    onVerseSelected = { verse -> onSelectVerse(selectedBookName!!, selectedChapter!!, verse) }
                )
            }
        }
    }
}

// `books` defaults to the full canon (BookChapterPickerSheet's use case).
// Callers that pass a filtered subset (e.g. the Studied browser, which only
// wants books containing a studied verse) still get correctly-placed
// "Old Testament"/"New Testament" banners because the banner check compares
// each book's *global* BIBLE_BOOKS index against otCount rather than
// assuming the list index lines up with the full canon.
@Composable
internal fun BookListStep(
    listState: LazyListState,
    otCount: Int,
    currentBook: String?,
    goldColor: Color,
    onBookSelected: (String) -> Unit,
    books: List<String> = BIBLE_BOOKS,
    trailingContent: @Composable (String) -> Unit = { book ->
        Text(
            text = "${BOOK_CHAPTER_COUNTS[book] ?: 1} ch",
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        itemsIndexed(books) { index, book ->
            val isNT = BIBLE_BOOKS.indexOf(book) >= otCount
            val prevIsNT = if (index == 0) null else BIBLE_BOOKS.indexOf(books[index - 1]) >= otCount
            if (index == 0) {
                BookListGroupBanner(text = if (isNT) "New Testament" else "Old Testament", color = goldColor)
            } else if (prevIsNT == false && isNT) {
                BookListGroupBanner(text = "New Testament", color = goldColor)
            }

            val isCurrent = currentBook != null && book == currentBook
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCurrent) {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        } else Modifier
                    )
                    .clickable { onBookSelected(book) }
                    .padding(vertical = 13.dp, horizontal = 4.dp)
                    .testTag(if (isCurrent) "book_row_current" else "book_row_$book"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = book,
                    fontSize = 17.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                trailingContent(book)
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun BookListGroupBanner(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        color = color,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

// `chapters` defaults to the book's full 1..N range (BookChapterPickerSheet's
// use case). The Studied browser passes a filtered subset (only chapters
// containing a studied verse) and can override `cellBackground`/`cellContent`
// to shade/badge each box without needing a second copy of the grid.
@Composable
internal fun ChapterGridStep(
    bookName: String,
    onBack: () -> Unit,
    onChapterSelected: (Int) -> Unit,
    chapters: List<Int> = (1..(BOOK_CHAPTER_COUNTS[bookName] ?: 1)).toList(),
    cellBackground: @Composable (Int) -> Color = { Color.Transparent },
    cellContent: @Composable BoxScope.(Int) -> Unit = { chap ->
        Text(
            text = "$chap",
            fontSize = 15.5.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Text(
            text = "\u2039 All books",
            fontSize = 14.5.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .testTag("picker_back_to_books")
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(chapters) { chap ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cellBackground(chap), RoundedCornerShape(6.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable { onChapterSelected(chap) }
                        .testTag("chapter_btn_$chap")
                ) {
                    cellContent(chap)
                }
            }
        }
    }
}

/**
 * Verse-level step, after Book -> Chapter. Matches Capacitor's
 * `openVerseGrid`: a "Start of chapter" row preserves the old
 * one-tap-per-screen behavior for anyone not hunting a specific verse,
 * followed by a verse-number grid once the count's known. Unlike
 * Capacitor (which fetches over the network to learn the verse count),
 * [getVerseCount] reads from the bundled/imported Room data directly —
 * see BibleRepository.getVerseCount — so this is normally instant with no
 * real loading state, but the brief-loading and not-yet-available paths
 * are kept for parity and as a safety net.
 */
// `onStartOfChapter` is nullable so callers that don't want that row can
// omit it. `cellBackground`/`cellContent` let the Studied browser fill+badge
// already-studied verses while every other verse in the chapter still
// renders (and is still tappable) for context, matching VerseGridStep's
// original outline-box look everywhere else.
@Composable
internal fun VerseGridStep(
    bookName: String,
    chapter: Int,
    getVerseCount: suspend (String, Int) -> Int,
    onBack: () -> Unit,
    onVerseSelected: (Int) -> Unit,
    onStartOfChapter: (() -> Unit)? = null,
    cellBackground: @Composable (Int) -> Color = { Color.Transparent },
    cellContent: @Composable BoxScope.(Int) -> Unit = { verse ->
        Text(
            text = "$verse",
            fontSize = 15.5.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
) {
    var verseCount by remember(bookName, chapter) { mutableStateOf<Int?>(null) }
    LaunchedEffect(bookName, chapter) {
        verseCount = try {
            getVerseCount(bookName, chapter)
        } catch (e: Exception) {
            0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Text(
            text = "\u2039 $bookName",
            fontSize = 14.5.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .testTag("picker_back_to_chapters")
        )
        if (onStartOfChapter != null) {
            Text(
                text = "Start of chapter",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStartOfChapter)
                    .padding(vertical = 13.dp, horizontal = 4.dp)
                    .testTag("picker_start_of_chapter")
            )
        }

        when (val count = verseCount) {
            null -> Text(
                text = "Loading verses\u2026",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
            )
            0 -> Text(
                text = "Verse numbers need this chapter downloaded first \u2014 tap Start of chapter above.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items((1..count).toList()) { verse ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellBackground(verse), RoundedCornerShape(6.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .clickable { onVerseSelected(verse) }
                            .testTag("verse_btn_$verse")
                    ) {
                        cellContent(verse)
                    }
                }
            }
        }
    }
}
