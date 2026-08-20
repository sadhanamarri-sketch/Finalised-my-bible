package com.example.mybible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BookListStep
import com.example.mybible.ui.components.ChapterGridStep
import com.example.mybible.ui.components.PickerDarkGold
import com.example.mybible.ui.components.PickerPaperGold
import com.example.mybible.ui.components.VerseGridStep
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
 * what's been studied. Book and chapter steps are filtered down to only
 * what contains a studied verse; the verse step shows every verse in the
 * chapter for context, with studied ones filled/checked. Tapping any verse
 * jumps to it in the Reader.
 *
 * Marking/unmarking a verse as studied is not available anywhere in this
 * screen — that's exclusively a Reader-tab action (long-press a verse, or
 * the picking-mode banner started from Reader's own menu).
 */
@Composable
fun StudiedScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val completedList by viewModel.completedVerses.collectAsState(initial = emptyList())
    val otTotalVerses by viewModel.otTotalVerses.collectAsState()
    val ntTotalVerses by viewModel.ntTotalVerses.collectAsState()

    var selectedBook by remember { mutableStateOf<String?>(null) }
    var selectedChapter by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    val otCount = remember { BIBLE_BOOKS.indexOf("Matthew") }

    val studiedByBook = remember(completedList) { completedList.groupBy { it.book } }
    val studiedBooks = remember(studiedByBook) { BIBLE_BOOKS.filter { studiedByBook.containsKey(it) } }
    val studiedChaptersForBook = remember(selectedBook, studiedByBook) {
        selectedBook?.let { book -> studiedByBook[book]?.map { it.chapter }?.distinct()?.sorted() } ?: emptyList()
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
    val otProgress = if (otTotalVerses > 0) otStudiedCount.toFloat() / otTotalVerses else 0f
    val ntProgress = if (ntTotalVerses > 0) ntStudiedCount.toFloat() / ntTotalVerses else 0f

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
                        else -> viewModel.selectTab(NavTab.READER)
                    }
                },
                actions = {
                    // Hands off to the Reader with the pick-mode banner —
                    // tapping verses there marks them studied. Only shown at
                    // the top level. This is the one marking-related entry
                    // point that still lives in Studied; everything else
                    // (unmarking, single-verse marking) stays Reader-only.
                    if (selectedBook == null) {
                        TextButton(onClick = { viewModel.startStudyPicking() }) {
                            Text("+ Select", fontSize = 13.sp)
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
                    otProgress = otProgress,
                    ntProgress = ntProgress,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                )
                if (recentGroups.isNotEmpty()) {
                    RecentStudiedCard(
                        dayLabel = recentDayLabel,
                        groups = recentGroups,
                        onGroupClick = { group ->
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
                            Text(
                                text = "${studiedByBook[book]?.size ?: 0} studied",
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onBookSelected = { selectedBook = it }
                    )
                    selectedChapter == null -> ChapterGridStep(
                        bookName = selectedBook!!,
                        chapters = studiedChaptersForBook,
                        onBack = { selectedBook = null },
                        onChapterSelected = { chap -> selectedChapter = chap },
                        cellBackground = { MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) }
                    )
                    else -> VerseGridStep(
                        bookName = selectedBook!!,
                        chapter = selectedChapter!!,
                        getVerseCount = viewModel::getVerseCount,
                        onBack = { selectedChapter = null },
                        onVerseSelected = { verse ->
                            // Browsing into a chapter here, not spotlighting
                            // one verse — no focus-blur (see jumpToVerse).
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
}

// "Today" / "Yesterday" / "MMM d" — mirrors how most reading-streak style
// UIs label a relative day rather than always showing a bare date.
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
    otProgress: Float,
    ntProgress: Float,
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
        TestamentProgressRow(label = "Old Testament", progress = otProgress)
        Spacer(modifier = Modifier.height(14.dp))
        TestamentProgressRow(label = "New Testament", progress = ntProgress)
    }
}

@Composable
private fun TestamentProgressRow(label: String, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
