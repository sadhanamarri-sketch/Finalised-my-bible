@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.mybible.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import com.example.mybible.ui.theme.notoSerifOrTestFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.DateField
import com.example.mybible.ui.components.NeSectionLabel

// Formats an ISO "yyyy-MM-dd" note date as "22nd Aug 2026" — matches the
// day-ordinal + short-month style requested for the Notes list, falling
// back to the raw string if it isn't parseable (e.g. a partially-typed
// date filter).
private fun formatNoteDate(iso: String): String {
    return try {
        val date = java.time.LocalDate.parse(iso)
        val day = date.dayOfMonth
        val suffix = if (day in 11..13) "th" else when (day % 10) {
            1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
        }
        val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)
        "$day$suffix $month ${date.year}"
    } catch (e: Exception) {
        iso
    }
}

@Composable
fun NotesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val tagDefinitions by viewModel.tagDefinitions.collectAsState(initial = emptyList())
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
    // Advanced filters (tags + date) live in a bottom sheet now, opened via
    // the compact search bar's Filter chip — search text itself is always
    // visible and never needs the sheet.
    var showFilterSheet by remember { mutableStateOf(false) }
    var textQuery by remember { mutableStateOf("") }
    var selectedFilterTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var dateFilter by remember { mutableStateOf("") }
    val filtersActive = selectedFilterTags.isNotEmpty() || dateFilter.isNotBlank()
    // Edit/Delete were also on the Note Reader's top bar before, but that
    // duplicated what's already one tap away here in the list — Delete now
    // confirms here since this is the only place it can be triggered from.
    var noteIdPendingDelete by remember { mutableStateOf<Long?>(null) }

    // One-shot handoff from the Tags screen: tapping a tag row there sets
    // this, closes Tags, and lands back here with that tag pre-selected as
    // a filter. Consumed once, then cleared, so it doesn't re-trigger on
    // later recompositions (e.g. after manually clearing the filter).
    val pendingTagFilter by viewModel.pendingNoteTagFilter.collectAsState()
    LaunchedEffect(pendingTagFilter) {
        val tag = pendingTagFilter ?: return@LaunchedEffect
        selectedFilterTags = listOf(tag)
        viewModel.clearPendingNoteTagFilter()
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
                        Icon(Icons.Default.Label, contentDescription = "Manage tags")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startNotePicking() },
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
                        value = textQuery,
                        onValueChange = { textQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = WorkSansFontFamily,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (textQuery.isEmpty()) {
                        Text(
                            text = "Search notes\u2026",
                            fontSize = 16.sp,
                            fontFamily = WorkSansFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (textQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { textQuery = "" }
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
                        contentDescription = "Filter notes",
                        tint = if (filtersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (filtersActive) {
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
            if (filtersActive) {
                val summaryParts = buildList {
                    if (selectedFilterTags.isNotEmpty()) {
                        add(if (selectedFilterTags.size == 1) "1 tag" else "${selectedFilterTags.size} tags")
                    }
                    if (dateFilter.isNotBlank()) add(formatNoteDate(dateFilter))
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
                        text = summaryParts.joinToString(" \u00b7 "),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            selectedFilterTags = emptyList()
                            dateFilter = ""
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val filteredNotes = notes.filter { note ->
                val q = textQuery.trim()
                val textMatch = q.isBlank() ||
                    note.title.contains(q, ignoreCase = true) ||
                    note.text.contains(q, ignoreCase = true)
                val tagsMatch = selectedFilterTags.isEmpty() || note.tags.any { noteTag ->
                    selectedFilterTags.any { filterTag -> noteTag.equals(filterTag, ignoreCase = true) }
                }
                val dateMatch = dateFilter.isBlank() || note.noteDate == dateFilter
                textMatch && tagsMatch && dateMatch
            }

            if (filteredNotes.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = if (notes.isEmpty()) "No notes created yet. Click + to add one!" else "No notes match the selected filters.",
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        // Stripped down to just what's needed to recognize
                        // a note at a glance: title, text, "Read more" —
                        // refs/date/tags used to make each row noisy and
                        // are already visible inside the note itself.
                        Card(
                            onClick = { viewModel.openNoteReader(note) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.title.ifBlank { "Untitled note" }.uppercase(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = WorkSansFontFamily,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                // ••• menu — kept by request even though
                                // Capacitor has no per-card menu (there,
                                // the whole card opens the editor and
                                // Delete lives inside it).
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
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            },
                                            onClick = {
                                                showMenu = false
                                                viewModel.openNoteEditor(note)
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
                                                noteIdPendingDelete = note.id
                                            }
                                        )
                                    }
                                }
                            }

                            // Note body preview — clipped to a few lines;
                            // "Read more" (below) opens the full note
                            // reader rather than expanding inline. Plain
                            // Text, not LinkifiedNoteText: the whole card is
                            // already tap-to-open-reader (see the Card's own
                            // onClick above), and a verse mention being
                            // separately clickable here stole that tap
                            // instead of opening the reader. Verse mentions
                            // are only meant to be tappable once you're
                            // actually inside the reader — see
                            // NoteReaderScreen's use of LinkifiedNoteText.
                            Text(
                                text = note.text,
                                fontFamily = notoSerifOrTestFontFamily,
                                fontSize = com.example.mybible.ui.theme.FontTestLab.listPreviewFontSizeSp.sp,
                                lineHeight = (com.example.mybible.ui.theme.FontTestLab.listPreviewFontSizeSp * com.example.mybible.ui.theme.FontTestLab.listPreviewLineHeightMultiplier).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp)
                            )

                            // "Read more" — right-aligned, coral (the
                            // theme's primary/accent tone). Only shown when
                            // the 3-line preview above is actually likely
                            // to have clipped something.
                            if (note.text.length > 140) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Read more",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(top = 4.dp)
                                            .clickable { viewModel.openNoteReader(note) }
                                    )
                                }
                            }
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
            tagDefinitions = tagDefinitions.map { it.name },
            selectedTags = selectedFilterTags,
            onTagsChange = { selectedFilterTags = it },
            dateFilter = dateFilter,
            onDateFilterChange = { dateFilter = it },
            onDismiss = { showFilterSheet = false }
        )
    }

    val pendingDeleteId = noteIdPendingDelete
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { noteIdPendingDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(pendingDeleteId)
                        noteIdPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteIdPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// Advanced filters, pulled out of permanent screen space and into a sheet —
// opened from the search bar's Filter icon. Reuses the same visual language
// as the Note editor (NeSectionLabel + chip pills + DateField) so filtering
// notes feels like the same app as writing them.
@Composable
private fun NotesFilterSheet(
    tagDefinitions: List<String>,
    selectedTags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    dateFilter: String,
    onDateFilterChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                Text("Filter Notes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (selectedTags.isNotEmpty() || dateFilter.isNotBlank()) {
                    Text(
                        text = "Clear all",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            onTagsChange(emptyList())
                            onDateFilterChange("")
                        }
                    )
                }
            }

            // ── Tags ─────────────────────────────────────────────────────
            NeSectionLabel("Tags")
            if (tagDefinitions.isEmpty()) {
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
                    tagDefinitions.forEach { tag ->
                        val selected = selectedTags.any { it.equals(tag, ignoreCase = true) }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onTagsChange(
                                    if (selected) selectedTags.filterNot { it.equals(tag, ignoreCase = true) }
                                    else (selectedTags + tag).distinctBy { it.lowercase() }
                                )
                            },
                            label = { Text(tag) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // ── Date ─────────────────────────────────────────────────────
            NeSectionLabel("Date")
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateField(
                    value = dateFilter,
                    onValueChange = onDateFilterChange,
                    placeholder = "YYYY-MM-DD",
                    modifier = Modifier.weight(1f)
                )
                if (dateFilter.isNotBlank()) {
                    Text(
                        text = "Clear",
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onDateFilterChange("") }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
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
}
