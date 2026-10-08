package com.example.mybible.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.BookProgress
import com.example.mybible.ChapterProgress
import com.example.mybible.ChapterState
import com.example.mybible.StudiedProgress
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BOOK_CHAPTER_COUNTS
import com.example.mybible.ui.components.BookListStep
import com.example.mybible.ui.components.ChapterGridStep
import com.example.mybible.ui.components.PickerDarkGold
import com.example.mybible.ui.components.PickerPaperGold
import com.example.mybible.ui.components.VerseGridStep
import com.example.mybible.ui.theme.WorkSansFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Collapses a set of verse numbers into "1-9" / "1,3,5,7-10" style ranges —
// consecutive runs become a dash range, isolated numbers stay comma-joined.
private fun formatVerseRanges(verses: List<Int>): String {
    val sorted = verses.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var start = sorted[0]
    var prev = sorted[0]
    for (i in 1 until sorted.size) {
        val v = sorted[i]
        if (v == prev + 1) {
            prev = v
        } else {
            parts.add(if (start == prev) "$start" else "$start-$prev")
            start = v
            prev = v
        }
    }
    parts.add(if (start == prev) "$start" else "$start-$prev")
    return parts.joinToString(",")
}

private data class RecentGroup(
    val book: String,
    val chapter: Int,
    val verses: List<Int>,
    val latestCompletedAt: Long
)

/**
 * Studied tab: Old/New Testament progress, a "Recently Studied" summary for
 * the last active day, and a Book -> Chapter -> Verse browser (built on the
 * same step components as
 * [com.example.mybible.ui.components.BookChapterPickerSheet]) for revisiting
 * what's been studied. The book step lists the books with a studied verse,
 * each with how much of it is studied; the chapter step shows every chapter
 * of the book as done, partly studied or not started; the verse step shows
 * every verse in the chapter for context, with studied ones filled/checked.
 * Tapping any verse jumps to it in the Reader.
 *
 * Marking/unmarking a verse as studied is a Reader-tab action (long-press a
 * verse, the picking-mode banner, or a chapter's "Mark … studied" at its
 * end). Here there's only starting over: clearing every mark at once.
 */
@Composable
fun StudiedScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val completedList by viewModel.completedVerses.collectAsState(initial = emptyList())
    val otTotalVerses by viewModel.otTotalVerses.collectAsState()
    val ntTotalVerses by viewModel.ntTotalVerses.collectAsState()
    val chapterVerseCounts by viewModel.chapterVerseCounts.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    // Remembered across a tab visit — StudiedScreen is fully disposed (not
    // just hidden) on a tab switch, so without this, leaving mid-browse and
    // coming back always reset to the top-level dashboard instead of
    // wherever you'd drilled into. Same reasoning/pattern as
    // notesScrollIndex/Offset.
    val savedSelectedBook by viewModel.studiedSelectedBook.collectAsState()
    val savedSelectedChapter by viewModel.studiedSelectedChapter.collectAsState()
    val savedScrollIndex by viewModel.studiedBookListScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.studiedBookListScrollOffset.collectAsState()

    var selectedBook by remember { mutableStateOf(savedSelectedBook) }
    var selectedChapter by remember { mutableStateOf(savedSelectedChapter) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveStudiedScreenState(
                selectedBook,
                selectedChapter,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    // System back used to always exit straight to Reader regardless of how
    // deep the book/chapter/verse drill-down was, since the only back
    // handling that existed lived in MainActivity with no visibility into
    // this screen's own local state — a jarring mismatch with the visible
    // back-arrow above, which steps out one level at a time. Enabled only
    // while at least one level deep; at the top level this falls through
    // to MainActivity's blanket handler (-> Reader), matching the arrow's
    // own top-level behavior.
    BackHandler(enabled = selectedChapter != null || selectedBook != null) {
        when {
            selectedChapter != null -> selectedChapter = null
            selectedBook != null -> selectedBook = null
        }
    }

    val otCount = remember { BIBLE_BOOKS.indexOf("Matthew") }

    val studiedByBook = remember(completedList) { completedList.groupBy { it.book } }
    val studiedBooks = remember(studiedByBook) { BIBLE_BOOKS.filter { studiedByBook.containsKey(it) } }
    // Each book's verse counts by chapter, once the import has them; before, its chapters with
    // their counts unknown (zero).
    fun verseCountsOf(book: String): List<Int> =
        chapterVerseCounts[book] ?: List(BOOK_CHAPTER_COUNTS[book] ?: 1) { 0 }
    val bookProgress = remember(studiedByBook, chapterVerseCounts) {
        studiedByBook.mapValues { (book, items) -> StudiedProgress.book(items, verseCountsOf(book)) }
    }
    val chapterProgress = remember(selectedBook, studiedByBook, chapterVerseCounts) {
        selectedBook?.let { book -> StudiedProgress.chapters(studiedByBook[book].orEmpty(), verseCountsOf(book)) } ?: emptyList()
    }
    val studiedVersesForChapter = remember(selectedBook, selectedChapter, completedList) {
        if (selectedBook != null && selectedChapter != null) {
            completedList.filter { it.book == selectedBook && it.chapter == selectedChapter }
                .map { it.verse }.toSet()
        } else emptySet()
    }

    // Old/New Testament progress — studied-verse count (deduped by book
    // membership against the canonical OT/NT split) over the imported
    // Bible's actual per-testament verse totals (see MainViewModel's
    // otTotalVerses/ntTotalVerses).
    val otStudiedCount = remember(completedList, otCount) {
        completedList.count { BIBLE_BOOKS.indexOf(it.book) < otCount }
    }
    val ntStudiedCount = completedList.size - otStudiedCount

    // Recently Studied — every verse marked on the most recent day that has
    // any studied verse at all, grouped by chapter and range-compressed.
    val studyDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val mostRecentDay = remember(completedList) {
        completedList.maxByOrNull { it.completedAt }?.let { studyDateFormat.format(Date(it.completedAt)) }
    }
    val recentGroups = remember(completedList, mostRecentDay) {
        if (mostRecentDay == null) emptyList()
        else completedList
            .filter { studyDateFormat.format(Date(it.completedAt)) == mostRecentDay }
            .groupBy { it.book to it.chapter }
            .map { (key, items) ->
                RecentGroup(
                    book = key.first,
                    chapter = key.second,
                    verses = items.map { it.verse },
                    latestCompletedAt = items.maxOf { it.completedAt }
                )
            }
            .sortedByDescending { it.latestCompletedAt }
    }
    val recentDayLabel = remember(mostRecentDay) {
        if (mostRecentDay == null) "" else formatDayLabel(mostRecentDay, studyDateFormat)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val goldColor = if (isDark) PickerDarkGold else PickerPaperGold

    Scaffold(
        topBar = {
            BackTopBar(
                title = when {
                    selectedChapter != null -> "$selectedBook $selectedChapter"
                    selectedBook != null -> selectedBook!!
                    else -> "Studied"
                },
                onBack = {
                    when {
                        selectedChapter != null -> selectedChapter = null
                        selectedBook != null -> selectedBook = null
                        else -> {
                            viewModel.backToStudiedSourceVerse()
                            viewModel.selectTab(NavTab.READER)
                        }
                    }
                },
                actions = {
                    // Hands off to the Reader with the pick-mode banner —
                    // tapping verses there marks them studied. Only shown at
                    // the top level. This is the one marking-related entry
                    // point that still lives in Studied; everything else
                    // (unmarking, single-verse marking) stays Reader-only,
                    // but for starting over, in the menu beside it.
                    if (selectedBook == null) {
                        TextButton(onClick = { viewModel.startStudyPicking() }) {
                            Text("+ Select", fontSize = 13.sp)
                        }
                        if (completedList.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Clear all studied") },
                                        onClick = {
                                            showMenu = false
                                            confirmClearAll = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedBook == null) {
                TestamentProgressCard(
                    otStudied = otStudiedCount,
                    otTotal = otTotalVerses,
                    ntStudied = ntStudiedCount,
                    ntTotal = ntTotalVerses,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                )
                if (recentGroups.isNotEmpty()) {
                    RecentStudiedCard(
                        dayLabel = recentDayLabel,
                        groups = recentGroups,
                        onGroupClick = { group ->
                            viewModel.markStudiedNavigation()
                            viewModel.disableBlurModeForNavigation()
                            viewModel.jumpToVerse(group.book, group.chapter, group.verses.min())
                            viewModel.selectTab(NavTab.READER)
                        },
                        modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp)
                    )
                }
            }

            if (studiedBooks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No verses marked studied yet.\nLong-press a verse while reading to mark it.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                when {
                    selectedBook == null -> BookListStep(
                        listState = listState,
                        otCount = otCount,
                        currentBook = null,
                        goldColor = goldColor,
                        books = studiedBooks,
                        trailingContent = { book ->
                            val progress = bookProgress[book]
                            Text(
                                text = buildAnnotatedString {
                                    if (progress == null || progress.verses == 0) {
                                        append("${studiedByBook[book]?.size ?: 0} studied")
                                    } else {
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                                            append(StudiedProgress.percent(progress.studied, progress.verses))
                                        }
                                        val chapters = if (progress.chapters == 1) "chapter" else "chapters"
                                        append(" \u00B7 ${progress.chaptersDone} of ${progress.chapters} $chapters")
                                    }
                                },
                                fontSize = 13.5.sp,
                                fontFamily = WorkSansFontFamily,
                                letterSpacing = 0.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onBookSelected = { selectedBook = it }
                    )
                    selectedChapter == null -> ChapterGridStep(
                        bookName = selectedBook!!,
                        chapters = chapterProgress.map { it.chapter },
                        onBack = { selectedBook = null },
                        onChapterSelected = { chap -> selectedChapter = chap },
                        cellBackground = { chap ->
                            when (chapterProgress.getOrNull(chap - 1)?.state) {
                                ChapterState.DONE -> MaterialTheme.colorScheme.primaryContainer
                                ChapterState.PARTLY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else -> Color.Transparent
                            }
                        },
                        cellContent = { chap -> ChapterCell(chap, chapterProgress.getOrNull(chap - 1)) },
                        header = { ChapterGridHeader(selectedBook!!, bookProgress[selectedBook!!]) }
                    )
                    else -> VerseGridStep(
                        bookName = selectedBook!!,
                        chapter = selectedChapter!!,
                        getVerseCount = viewModel::getVerseCount,
                        onBack = { selectedChapter = null },
                        onVerseSelected = { verse ->
                            // Browsing into a chapter here, not spotlighting
                            // one verse — no focus-blur (see jumpToVerse).
                            viewModel.markStudiedNavigation()
                            viewModel.disableBlurModeForNavigation()
                            viewModel.jumpToVerse(selectedBook!!, selectedChapter!!, verse, focusVerse = false)
                            selectedBook = null
                            selectedChapter = null
                            viewModel.selectTab(NavTab.READER)
                        },
                        cellBackground = { verse ->
                            if (studiedVersesForChapter.contains(verse))
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        },
                        cellContent = { verse ->
                            val isStudied = studiedVersesForChapter.contains(verse)
                            Text(
                                text = "$verse",
                                fontSize = 15.5.sp,
                                fontWeight = if (isStudied) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isStudied) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            if (isStudied) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Studied",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(10.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        val count = completedList.size
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Start over?") },
            text = {
                Text(
                    "This unmarks all ${StudiedProgress.count(count)} studied " +
                        (if (count == 1) "verse" else "verses") +
                        ". Your notes and highlights stay as they are."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.clearAllStudied()
                }) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

// "Today" / "Yesterday" / "MMM d" — a recent day the way people say it,
// rather than always a bare date.
private fun formatDayLabel(dayString: String, dayFormat: SimpleDateFormat): String {
    val todayStr = dayFormat.format(Date())
    val yesterdayStr = dayFormat.format(Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time)
    return when (dayString) {
        todayStr -> "Today"
        yesterdayStr -> "Yesterday"
        else -> dayFormat.parse(dayString)?.let { SimpleDateFormat("MMM d", Locale.US).format(it) } ?: dayString
    }
}

@Composable
private fun TestamentProgressCard(
    otStudied: Int,
    otTotal: Int,
    ntStudied: Int,
    ntTotal: Int,
    modifier: Modifier = Modifier
) {
    // Flat bordered box, not an elevated Card — matches the "boxed
    // preview" treatment used elsewhere (NoteEditorScreen's note-body
    // preview, CrossReferenceScreen's source-verse box) instead of a
    // Material surface-tint fill.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        TestamentProgressRow(label = "Old Testament", studied = otStudied, total = otTotal)
        Spacer(modifier = Modifier.height(14.dp))
        TestamentProgressRow(label = "New Testament", studied = ntStudied, total = ntTotal)
    }
}

// A testament's share studied: "0.8%" (see StudiedProgress.percent, which never rounds a
// little up to nothing or a lot up to done), a bar on a track of the same color, lighter, and
// the verses themselves: "200 of 23,145 verses".
@Composable
private fun TestamentProgressRow(label: String, studied: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = StudiedProgress.percent(studied, total),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) (studied.toFloat() / total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        if (total > 0) {
            Text(
                text = "${StudiedProgress.count(studied)} of ${StudiedProgress.count(total)} verses",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

// Above a book's chapters: how much of it is studied, and what the chapters' marks mean.
@Composable
private fun ChapterGridHeader(book: String, progress: BookProgress?) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        if (progress != null && progress.verses > 0) {
            Text(
                text = "${StudiedProgress.percent(progress.studied, progress.verses)} of $book studied \u00B7 " +
                    "${progress.chaptersDone} of ${progress.chapters} " + (if (progress.chapters == 1) "chapter" else "chapters") + " done",
                fontSize = 13.5.sp,
                fontFamily = WorkSansFontFamily,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            LegendItem(ChapterState.DONE, "Done")
            LegendItem(ChapterState.PARTLY, "Partly")
            LegendItem(ChapterState.NOT_STARTED, "Not started")
        }
    }
}

@Composable
private fun LegendItem(state: ChapterState, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    when (state) {
                        ChapterState.DONE -> MaterialTheme.colorScheme.primaryContainer
                        ChapterState.PARTLY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ChapterState.NOT_STARTED -> Color.Transparent
                    }
                )
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
        ) {
            if (state == ChapterState.PARTLY) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = WorkSansFontFamily,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// One chapter of the grid: its number, a check when every verse is studied, and a line along
// the bottom for how much of it is when it's partly.
@Composable
private fun BoxScope.ChapterCell(chapter: Int, progress: ChapterProgress?) {
    val state = progress?.state ?: ChapterState.NOT_STARTED
    Text(
        text = "$chapter",
        fontSize = 15.5.sp,
        fontFamily = WorkSansFontFamily,
        letterSpacing = 0.sp,
        fontWeight = if (state == ChapterState.DONE) FontWeight.SemiBold else FontWeight.Normal,
        color = if (state == ChapterState.DONE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.align(Alignment.Center)
    )
    when (state) {
        ChapterState.DONE -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Studied",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(10.dp)
        )
        ChapterState.PARTLY -> Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress!!.fraction.coerceIn(0.08f, 1f))
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        ChapterState.NOT_STARTED -> Unit
    }
}

@Composable
private fun RecentStudiedCard(
    dayLabel: String,
    groups: List<RecentGroup>,
    onGroupClick: (RecentGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Studied",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = dayLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(10.dp))
        groups.forEachIndexed { index, group ->
            Text(
                text = "${group.book} ${group.chapter} \u2014 ${formatVerseRanges(group.verses)}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGroupClick(group) }
                    .padding(vertical = 5.dp)
            )
            if (index != groups.lastIndex) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
