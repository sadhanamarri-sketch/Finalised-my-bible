@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.mybible.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.example.mybible.ui.theme.NotoSerifFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.ui.theme.quoteColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteDates
import com.example.mybible.model.NoteItem
import com.example.mybible.model.NoteSharing
import com.example.mybible.model.NoteSort
import com.example.mybible.model.NotesFinder
import com.example.mybible.model.NotesQuery
import com.example.mybible.model.NotesRow
import com.example.mybible.model.QuoteSide
import com.example.mybible.model.groupRefs
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.NeSectionLabel
import com.example.mybible.ui.components.bibleBookIndex
import com.example.mybible.ui.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
private val LONG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

// A note's date on its card: "7 Oct", with the year when it isn't this one.
private fun cardDate(note: NoteItem, today: LocalDate): String {
    val day = NotesFinder.dateOf(note)
    return day.format(if (day.year == today.year) SHORT_DATE else LONG_DATE)
}

// What the date filter shows as: "This week", "7 Oct – 21 Oct".
private fun datesLabel(dates: NoteDates, today: LocalDate): String? = when (dates) {
    NoteDates.Any -> null
    NoteDates.ThisWeek -> "This week"
    NoteDates.ThisMonth -> "This month"
    is NoteDates.Between -> {
        val f = if (dates.from.year == today.year && dates.to.year == today.year) SHORT_DATE else LONG_DATE
        if (dates.from == dates.to) dates.from.format(f) else "${dates.from.format(f)} – ${dates.to.format(f)}"
    }
}

@Composable
fun NotesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val tagDefinitions by viewModel.tagDefinitions.collectAsState(initial = emptyList())
    // The search, filters and order, kept by the view model while the app is open (left for a
    // verse and come back to, they're as they were).
    val query by viewModel.notesQuery.collectAsState()
    val savedScrollIndex by viewModel.notesScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.notesScrollOffset.collectAsState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveNotesScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }
    val today = remember { LocalDate.now() }
    val rows = remember(notes, query) { NotesFinder.rows(notes, query, today, ::bibleBookIndex) }
    val shown = remember(rows) { rows.filterIsInstance<NotesRow.Note>().map { it.note } }
    // The order and filters live in a sheet, opened from the search bar's Filter icon.
    var showFilterSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Saved as a document: the notes the list shows, as text to read, wherever it's put.
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = NoteSharing.allAsText(shown, today, ::bibleBookIndex)
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
            }
            val count = if (shown.size == 1) "1 note" else "${shown.size} notes"
            Toast.makeText(context, if (saved) "Saved $count" else "Couldn't save the notes", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Notes",
                onBack = {
                    viewModel.backToNotesSourceVerse()
                    viewModel.selectTab(NavTab.READER)
                },
                actions = {
                    IconButton(onClick = { viewModel.openTagsScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Manage tags")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (query.filtering || query.text.isNotBlank()) "Save these notes as a document"
                                        else "Save all notes as a document"
                                    )
                                },
                                enabled = shown.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    exporter.launch("Notes $today.txt")
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startNewNote() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Note")
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
            // ── Search bar — always visible, ~56dp, same flat-bordered
            // look as the Note editor's NeTextField (rounded 8dp, 1px
            // outline, surface fill) rather than a Material outlined field.
            // The Filter icon sits inline as a trailing action instead of
            // living in the top bar, and carries a small dot when any
            // advanced filter is active so it's clear at a glance that a
            // filter is applied even before opening the sheet.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query.text,
                        onValueChange = { viewModel.setNotesQuery(query.copy(text = it)) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = WorkSansFontFamily,
                            fontSize = 16.sp,
                            letterSpacing = 0.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (query.text.isEmpty()) {
                        Text(
                            // Verses ("John 15", "Jn 15:5") and tags are found too.
                            text = "Search notes, verses, tags…",
                            fontSize = 16.sp,
                            fontFamily = WorkSansFontFamily,
                            letterSpacing = 0.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (query.text.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.setNotesQuery(query.copy(text = "")) }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showFilterSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Sort and filter notes",
                        tint = if (query.filtering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (query.filtering) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // ── Active-filter summary — a slim chip row that only appears
            // once a tag or date filter is applied, so you can see (and
            // clear) what's filtering the list without reopening the sheet.
            if (query.filtering) {
                val summaryParts = buildList {
                    if (query.special) add("Special")
                    if (query.tags.isNotEmpty()) {
                        add(
                            when {
                                query.tags.size == 1 -> query.tags.first()
                                query.allTags -> "All of ${query.tags.size} tags"
                                else -> "${query.tags.size} tags"
                            }
                        )
                    }
                    datesLabel(query.dates, today)?.let(::add)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showFilterSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = summaryParts.joinToString(" · "),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.setNotesQuery(query.unfiltered()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (rows.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = if (notes.isEmpty()) "No notes yet. Tap + to write one." else "No notes match.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                // Elevated Cards, not flat/hairline-divided rows — same
                // treatment as Search's result list.
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rows, key = { row ->
                        when (row) {
                            is NotesRow.Book -> "book:${row.name}"
                            is NotesRow.Note -> row.note.id
                        }
                    }) { row ->
                        when (row) {
                            is NotesRow.Book -> BookHeading(row)
                            is NotesRow.Note -> {
                                val note = row.note
                                NoteCard(
                                    note = note,
                                    today = today,
                                    onOpen = { viewModel.openNoteReader(note) },
                                    onEdit = { viewModel.openNoteEditor(note) },
                                    onPin = { viewModel.setNotePinned(note, !note.pinned) },
                                    onSpecial = { viewModel.setNoteSpecial(note, !note.special) },
                                    onShare = { shareText(context, note.title, NoteSharing.asText(note, ::bibleBookIndex)) },
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(NoteSharing.asText(note, ::bibleBookIndex)))
                                        Toast.makeText(context, "Note copied", Toast.LENGTH_SHORT).show()
                                    },
                                    // At once, with Undo on the bar that comes up (see MainViewModel.deleteNote).
                                    onDelete = { viewModel.deleteNote(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showFilterSheet) {
        NotesFilterSheet(
            query = query,
            tagNames = tagDefinitions.map { it.name },
            today = today,
            onChange = viewModel::setNotesQuery,
            onDismiss = { showFilterSheet = false }
        )
    }
}

// In Bible order, the book the notes below it are on: "JOHN · 3 notes".
@Composable
private fun BookHeading(row: NotesRow.Book) {
    Row(Modifier.padding(top = 6.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.name.uppercase(),
            fontSize = 12.5.sp,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            " · ${row.count} ${if (row.count == 1) "note" else "notes"}",
            fontSize = 12.5.sp,
            fontFamily = WorkSansFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// A note in the list: its title (or, untitled, its first line), a quiet line with its verses and
// date, and the start of its text; a pin when it's pinned to the top; marked special, a purple edge
// down its left and its title in purple.
@Composable
private fun NoteCard(
    note: NoteItem,
    today: LocalDate,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onSpecial: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val lines = note.text.trim().lines()
    val untitled = note.title.isBlank()
    val heading = if (untitled) lines.firstOrNull { it.isNotBlank() }?.trim() ?: "Untitled note" else note.title
    val preview = if (untitled) lines.dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim() else note.text.trim()
    val passages = remember(note.refs) { groupRefs(note.refs, ::bibleBookIndex) }
    val quiet = listOfNotNull(
        passages.takeIf { it.isNotEmpty() }?.let { p ->
            p.take(2).joinToString(", ") { it.label } + if (p.size > 2) " +${p.size - 2}" else ""
        },
        cardDate(note, today)
    ).joinToString(" · ")
    // The app's purple (the quotations' too).
    val purple = quoteColor(QuoteSide.QUOTES)
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (note.special) Modifier.drawBehind { drawRect(purple, size = Size(4.dp.toPx(), size.height)) } else Modifier)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (note.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 6.dp).size(15.dp)
                    )
                }
                Text(
                    text = heading.uppercase(),
                    fontSize = 14.sp,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    color = if (note.special) purple else MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Note options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (note.pinned) "Unpin" else "Pin to top") },
                            leadingIcon = { Icon(if (note.pinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (note.special) "Not special" else "Mark as special") },
                            leadingIcon = { Icon(if (note.special) Icons.Outlined.StarOutline else Icons.Filled.Star, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onSpecial()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Text(
                text = quiet,
                fontSize = 12.5.sp,
                fontFamily = WorkSansFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // The start of the note's text. Plain: the whole card opens the note, where its verse
            // references can be tapped.
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    fontFamily = NotoSerifFontFamily,
                    // Looks the size of the verse cards' 17sp Source Serif 4 (Noto Serif runs
                    // large), spaced as they are.
                    fontSize = 15.sp,
                    lineHeight = 25.5.sp,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

// The list's order and filters, in a sheet opened from the search bar's Filter icon: the order,
// tags (any or all of them), and dates (this week, this month, or from one day to another).
@Composable
private fun NotesFilterSheet(
    query: NotesQuery,
    tagNames: List<String>,
    today: LocalDate,
    onChange: (NotesQuery) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingDates by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sort and filter", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (query.filtering) {
                    Text(
                        text = "Clear filters",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onChange(query.unfiltered()) }
                    )
                }
            }

            // ── Order ────────────────────────────────────────────────────
            NeSectionLabel("Sort by")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteSort.entries.forEach { sort ->
                    FilterChip(
                        selected = query.sort == sort,
                        onClick = { onChange(query.copy(sort = sort)) },
                        label = { Text(sort.label) }
                    )
                }
            }

            // ── Special ──────────────────────────────────────────────────
            NeSectionLabel("Show")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !query.special, onClick = { onChange(query.copy(special = false)) }, label = { Text("All notes") })
                FilterChip(
                    selected = query.special,
                    onClick = { onChange(query.copy(special = true)) },
                    label = { Text("Special") },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = quoteColor(QuoteSide.QUOTES), modifier = Modifier.size(16.dp)) }
                )
            }

            // ── Tags ─────────────────────────────────────────────────────
            NeSectionLabel("Tags")
            if (tagNames.isEmpty()) {
                Text(
                    text = "No tags yet — add some from Manage Tags.",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tagNames.sortedBy { it.lowercase() }.forEach { tag ->
                        val selected = query.tags.any { it.equals(tag, ignoreCase = true) }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onChange(
                                    query.copy(
                                        tags = if (selected) query.tags.filterNot { it.equals(tag, ignoreCase = true) }
                                        else (query.tags + tag).distinctBy { it.lowercase() }
                                    )
                                )
                            },
                            label = { Text(tag) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                if (query.tags.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clickable { onChange(query.copy(allTags = !query.allTags)) }
                    ) {
                        Text(
                            "Only notes with all of these tags",
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = query.allTags, onCheckedChange = { onChange(query.copy(allTags = it)) })
                    }
                }
            }

            // ── Date ─────────────────────────────────────────────────────
            NeSectionLabel("Date")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = query.dates == NoteDates.Any, onClick = { onChange(query.copy(dates = NoteDates.Any)) }, label = { Text("Any time") })
                FilterChip(selected = query.dates == NoteDates.ThisWeek, onClick = { onChange(query.copy(dates = NoteDates.ThisWeek)) }, label = { Text("This week") })
                FilterChip(selected = query.dates == NoteDates.ThisMonth, onClick = { onChange(query.copy(dates = NoteDates.ThisMonth)) }, label = { Text("This month") })
                FilterChip(
                    selected = query.dates is NoteDates.Between,
                    onClick = { choosingDates = true },
                    label = { Text(datesLabel(query.dates, today)?.takeIf { query.dates is NoteDates.Between } ?: "Choose dates…") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show results")
            }
        }
    }

    if (choosingDates) {
        val between = query.dates as? NoteDates.Between
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = between?.from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialSelectedEndDateMillis = between?.to?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        DatePickerDialog(
            onDismissRequest = { choosingDates = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = state.selectedStartDateMillis
                        if (from != null) {
                            val to = state.selectedEndDateMillis ?: from
                            onChange(query.copy(dates = NoteDates.Between(day(from), day(to))))
                        }
                        choosingDates = false
                    },
                    enabled = state.selectedStartDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { choosingDates = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f))
        }
    }
}
