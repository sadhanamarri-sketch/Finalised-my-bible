@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.mybible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.ThemeMode
import com.example.mybible.ui.NoteDraft
import com.example.mybible.ui.richtext.NoteBodyField
import com.example.mybible.ui.richtext.NoteFormattingBar
import com.example.mybible.ui.richtext.rememberNoteLook
import com.example.mybible.ui.theme.NotoSerifFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The note's one page to write on: the title and the text together, with the note's verses, date
 * and tags in a strip between them. Saved as it's written (see MainViewModel.saveDraft), so Back
 * ([onClose]) just closes it; the menu has Discard changes (back to how it was opened) and Delete.
 * Over the keyboard while the text is written, the formatting toolbar (see NoteFormattingBar);
 * undo and redo in the top bar.
 */
@Composable
fun NoteEditorScreen(
    draft: NoteDraft,
    themeMode: ThemeMode,
    knownTags: List<String>,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: () -> Unit,
    onAddVerse: () -> Unit,
    onPreviewReference: (book: String, chapter: Int, verse: Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val look = rememberNoteLook(themeMode)
    val body = draft.body
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var bodyFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    val accent = MaterialTheme.colorScheme.primary

    // Ready to write: the cursor at the end of the text, once the page has slid up.
    LaunchedEffect(draft) {
        body.moveCursorToEnd()
        delay(320)
        runCatching { body.focusRequester.requestFocus() }
        keyboard?.show()
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    if (draft.isNew) "New note" else "Note",
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = { body.undo() }, enabled = body.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { body.redo() }, enabled = body.canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Discard changes") },
                            enabled = draft.key() != draft.openedKey,
                            onClick = {
                                menuOpen = false
                                confirmDiscard = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete note", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                TitleField(
                    value = draft.title,
                    onChange = { draft.title = it },
                    focus = titleFocus,
                    onNext = { runCatching { body.focusRequester.requestFocus() } }
                )
                Spacer(Modifier.height(10.dp))
                NoteDetails(
                    draft = draft,
                    onAddVerse = onAddVerse,
                    onPreviewReference = onPreviewReference,
                    onPickDate = { pickingDate = true },
                    onAddTag = { addingTag = true }
                )
                Spacer(Modifier.height(14.dp))
                NoteBodyField(
                    state = body,
                    look = look,
                    placeholder = "Start writing…",
                    onFocusChanged = { bodyFocused = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                // Below the text: a tap there goes on writing at its end.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            body.moveCursorToEnd()
                            runCatching { body.focusRequester.requestFocus() }
                            keyboard?.show()
                        }
                )
            }

            if (bodyFocused && imeVisible) {
                NoteFormattingBar(
                    state = body,
                    look = look,
                    onHideKeyboard = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = {
                Text(
                    if (draft.isNew) "This note won't be kept."
                    else "The note goes back to how it was when you opened it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }

    if (addingTag) {
        TagDialog(
            knownTags = knownTags.filter { k -> draft.tags.none { it.equals(k, ignoreCase = true) } },
            onAdd = { tag ->
                val name = knownTags.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag
                if (draft.tags.none { it.equals(name, ignoreCase = true) }) draft.tags = draft.tags + name
                addingTag = false
            },
            onDismiss = { addingTag = false }
        )
    }

    if (pickingDate) {
        val initial = remember(draft.date) { parseNoteDate(draft.date) ?: System.currentTimeMillis() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { draft.date = storedDate(it) }
                    pickingDate = false
                }) { Text("OK", color = accent) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = picker)
        }
    }
}

@Composable
private fun TitleField(value: String, onChange: (String) -> Unit, focus: FocusRequester, onNext: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val style = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, color = ink)
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) Text("Title", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        BasicTextField(
            value = value,
            // A title is one line: Enter goes on to the text.
            onValueChange = { v ->
                if ('\n' in v) onNext() else onChange(v)
            },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
    }
}

// The note's verses (a tap shows one, × takes it off), + Verse, its date, its tags, + Tag.
@Composable
private fun NoteDetails(
    draft: NoteDraft,
    onAddVerse: () -> Unit,
    onPreviewReference: (String, Int, Int) -> Unit,
    onPickDate: () -> Unit,
    onAddTag: () -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        draft.refs.forEach { ref ->
            DetailChip(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                text = "${ref.book} ${ref.chapter}:${ref.verse}",
                onClick = { onPreviewReference(ref.book, ref.chapter, ref.verse) },
                removeLabel = "Remove ${ref.book} ${ref.chapter}:${ref.verse}",
                onRemove = { draft.refs = draft.refs.filterNot { it.book == ref.book && it.chapter == ref.chapter && it.verse == ref.verse } }
            )
        }
        DetailChip(icon = Icons.Default.Add, text = "Verse", onClick = onAddVerse, quiet = true)
        DetailChip(
            icon = Icons.Default.Event,
            text = shownDate(draft.date) ?: "Date",
            onClick = onPickDate,
            quiet = draft.date.isBlank()
        )
        draft.tags.forEach { tag ->
            DetailChip(
                icon = Icons.Default.Tag,
                text = tag,
                onClick = null,
                removeLabel = "Remove tag $tag",
                onRemove = { draft.tags = draft.tags.filterNot { it == tag } }
            )
        }
        DetailChip(icon = Icons.Default.Add, text = "Tag", onClick = onAddTag, quiet = true)
    }
}

@Composable
private fun DetailChip(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)?,
    quiet: Boolean = false,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.tertiary
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (quiet) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = if (onRemove != null) 2.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontFamily = WorkSansFontFamily,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (quiet) accent else MaterialTheme.colorScheme.onSurface
        )
        if (onRemove != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = removeLabel, onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = removeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// A tag typed, or one already used picked.
@Composable
private fun TagDialog(knownTags: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = remember(typed, knownTags) {
        val q = typed.trim()
        (if (q.isEmpty()) knownTags else knownTags.filter { it.contains(q, ignoreCase = true) }).take(8)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.replace("\n", "") },
                    singleLine = true,
                    placeholder = { Text("Prayer, Faith…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) onAdd(typed.trim()) })
                )
                if (matches.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        matches.forEach { tag -> AssistChip(onClick = { onAdd(tag) }, label = { Text(tag) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(typed.trim()) }, enabled = typed.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// A note's date is kept as "2026-10-22", and shown as "22 Oct 2026".
private fun parseNoteDate(stored: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(stored)?.time
}.getOrNull()

private fun shownDate(stored: String): String? = parseNoteDate(stored)?.let {
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it))
}

private fun storedDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
