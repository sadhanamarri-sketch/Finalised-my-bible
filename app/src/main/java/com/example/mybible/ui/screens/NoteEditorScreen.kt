@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.mybible.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import com.example.mybible.ui.theme.GelasioFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteItem
import com.example.mybible.model.NoteReference
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.DateField
import com.example.mybible.ui.components.NeSectionLabel
import com.example.mybible.ui.components.NeTextField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// Compose's Modifier.border() has no dashed-stroke option, so this draws
// one manually — matches Capacitor's #neAddRef (`border:1px dashed
// var(--gold)`).
private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp = 1.dp,
    dashLength: androidx.compose.ui.unit.Dp = 6.dp,
    gapLength: androidx.compose.ui.unit.Dp = 4.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp
) = this.drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f)
        ),
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
}

/**
 * Full-page note editor — replaces the old [ModalBottomSheet]-based
 * NoteEditorDialog. Creating or editing a note now opens an entirely new
 * screen instead of a sheet layered over the notes list, giving the note
 * body room to breathe and putting Save where a page naturally expects
 * it: in the top bar, always visible, rather than scrolled away at the
 * bottom of a long sheet.
 *
 * [onResolveVerseText] is called immediately after a reference is added
 * so verseText is populated before save — no blank text on partial DB.
 */
@Composable
fun NoteEditorScreen(
    noteItem: NoteItem,
    onSave: (String, String, String, List<NoteReference>, List<String>) -> Unit,
    onCancel: () -> Unit,
    // Matches Capacitor's #neDelete, shown only when editing an existing
    // note (draftNote.id != null). Deletes and closes the editor.
    onDelete: (() -> Unit)? = null,
    onResolveVerseText: (suspend (NoteReference) -> NoteReference)? = null,
    // Called when "+ Add another verse" is tapped, with the draft's
    // *current* in-progress state (not the original noteItem passed in —
    // the user may have already typed a title/text/tags). The Reader takes
    // over via picking mode; on Done, MainViewModel merges newly picked
    // refs into exactly this snapshot and reopens the editor. Matches
    // Capacitor's neAddRef handler, which hides the editor without
    // clearing draftNote.
    onAddAnotherVerse: ((NoteItem) -> Unit)? = null,
    // Tapping a reference chip (not its × ) opens the same verse-preview
    // sheet as a linkified mention in note body text — matches Capacitor's
    // openVerseTextSheet, wired centrally in MainActivity.
    onPreviewReference: ((book: String, chapter: Int, verse: Int) -> Unit)? = null,
    // All tag names known app-wide (TagDefinition list), used for Enter-key
    // fuzzy-match and the suggestion dropdown below the tag field — matches
    // Capacitor's tagDefs.
    knownTags: List<String> = emptyList(),
    // Defaults to filling the screen — this is pushed inside an
    // AnimatedVisibility in MainActivity, which (unlike Scaffold on its
    // own) doesn't impose full-screen sizing on its content.
    modifier: Modifier = Modifier.fillMaxSize()
) {
    var title by remember { mutableStateOf(noteItem.title) }
    var text by remember { mutableStateOf(noteItem.text) }
    var noteDate by remember {
        mutableStateOf(
            noteItem.noteDate.ifBlank {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(noteItem.createdAt))
            }
        )
    }
    var refs by remember {
        mutableStateOf(
            noteItem.refs.ifEmpty {
                if (noteItem.book.isNotBlank() && noteItem.chapter > 0 && noteItem.verse > 0) {
                    listOf(NoteReference(noteItem.book, noteItem.chapter, noteItem.verse, noteItem.verseText))
                } else emptyList()
            }
        )
    }
    var tagInput by remember { mutableStateOf("") }
    // No default tag — matches Capacitor's draftNote.tags starting as []
    // whether the note came from startNewNote or startNewNoteFromScratch.
    var tags by remember { mutableStateOf(noteItem.tags) }

    // Matches Capacitor's #neText: a readonly preview that opens a
    // dedicated full-screen writer (#noteTextEditor) on tap instead of
    // being edited in place. Keeps the keyboard from competing with
    // Title/Date/References/Tags on the same scrollable page.
    var showFullTextEditor by remember { mutableStateOf(false) }

    // Any ref freshly merged in by Reader picking (see onAddAnotherVerse
    // above) may not have verseText resolved yet — resolve on first
    // composition/whenever refs changes, same "eager resolve before Save"
    // intent the old addReference() had.
    LaunchedEffect(refs) {
        if (onResolveVerseText == null) return@LaunchedEffect
        val unresolved = refs.filter { it.verseText.isBlank() }
        if (unresolved.isEmpty()) return@LaunchedEffect
        unresolved.forEach { ref ->
            val resolved = onResolveVerseText(ref)
            refs = refs.map {
                if (it.book == resolved.book && it.chapter == resolved.chapter && it.verse == resolved.verse) resolved else it
            }
        }
    }

    Box(modifier = modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = if (noteItem.id > 0) "Edit Note" else "New Note",
                onBack = onCancel,
                backIcon = Icons.Default.Close,
                backContentDescription = "Cancel",
                actions = {
                    TextButton(
                        onClick = { onSave(title, text, noteDate, refs, tags) },
                        enabled = text.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

            // ── References (first, matches Capacitor's #neRefs / #neAddRef) ─
            NeSectionLabel("References")

            if (refs.isEmpty()) {
                Text(
                    text = "No Bible references attached",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Matches Capacitor's .ref-chip: a pill per reference, each
                // its own tap target rather than one dense row that reads
                // like a comma-separated list. Tapping the label opens the
                // verse-preview sheet (Capacitor's openVerseTextSheet);
                // only the trailing × removes the reference — previously
                // these were combined into a single AssistChip click, so
                // tapping a reference deleted it instead of previewing it.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    refs.forEach { ref ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                        ) {
                            Text(
                                text = "${ref.book} ${ref.chapter}:${ref.verse}",
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clickable {
                                        onPreviewReference?.invoke(ref.book, ref.chapter, ref.verse)
                                    }
                                    .padding(start = 12.dp, top = 7.dp, bottom = 7.dp, end = 4.dp)
                            )
                            Text(
                                text = "\u00d7",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        refs = refs.filterNot {
                                            it.book == ref.book && it.chapter == ref.chapter && it.verse == ref.verse
                                        }
                                    }
                                    .padding(start = 4.dp, end = 12.dp, top = 7.dp, bottom = 7.dp)
                            )
                        }
                    }
                }
            }

            // Matches Capacitor's neAddRef button — there is no typed
            // "Book chapter:verse" entry field; every reference is added by
            // picking verses directly in the Reader. Tapping this hides the
            // editor, hands off to Reader picking mode with the draft's
            // current state preserved, and reopens the editor (with the
            // newly picked ref(s) merged in) once Done is tapped there.
            // Dashed gold border (not a filled/solid OutlinedButton) —
            // matches Capacitor's #neAddRef exactly.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .dashedBorder(color = MaterialTheme.colorScheme.tertiary)
                    .clickable {
                        onAddAnotherVerse?.invoke(
                            noteItem.copy(
                                title = title,
                                text = text,
                                noteDate = noteDate,
                                refs = refs,
                                tags = tags
                            )
                        )
                    }
                    .padding(vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (refs.isEmpty()) "Add verse (optional)" else "Add another verse",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // ── Title ────────────────────────────────────────────────────
            NeSectionLabel("Title", optionalNote = "(optional)")
            NeTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Note title",
                bold = true,
                onImeAction = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Date ─────────────────────────────────────────────────────
            NeSectionLabel("Date")
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateField(
                    value = noteDate,
                    onValueChange = { noteDate = it },
                    placeholder = "YYYY-MM-DD",
                    modifier = Modifier.weight(1f)
                )
                if (noteDate.isNotBlank()) {
                    Text(
                        text = "Clear",
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { noteDate = "" }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Tags ─────────────────────────────────────────────────────
            NeSectionLabel("Tags")

            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    tags.forEach { tag ->
                        AssistChip(
                            onClick = { tags = tags.filter { it != tag } },
                            label = { Text(tag, fontSize = 11.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove tag", modifier = Modifier.size(12.dp))
                            }
                        )
                    }
                }
            }

            // Adds a tag to the draft. If typed text matches an existing
            // tag name (case-insensitive), reuses that tag's exact
            // spelling — matches Capacitor's addTagToDraft().
            fun commitTag(raw: String) {
                val cleaned = raw.trim()
                if (cleaned.isBlank()) return
                val existing = knownTags.firstOrNull { it.equals(cleaned, ignoreCase = true) }
                val finalTag = existing ?: cleaned
                if (tags.none { it.equals(finalTag, ignoreCase = true) }) {
                    tags = tags + finalTag
                }
                tagInput = ""
            }

            var tagFieldFocused by remember { mutableStateOf(false) }
            val tagSuggestions = remember(tagInput, tags, knownTags, tagFieldFocused) {
                if (!tagFieldFocused) emptyList()
                else {
                    val q = tagInput.trim().lowercase()
                    val available = knownTags.filter { t -> tags.none { it.equals(t, ignoreCase = true) } }
                    (if (q.isEmpty()) available else available.filter { it.lowercase().contains(q) }).take(8)
                }
            }

            NeTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                placeholder = "Add a tag\u2026",
                onImeAction = {
                    // Same fuzzy-match-first behavior as Capacitor's
                    // neTagInput keydown handler: if what's typed partially
                    // matches an unselected existing tag, Enter commits
                    // that match rather than the raw typed text.
                    val q = tagInput.trim().lowercase()
                    val fuzzyMatch = if (q.isNotEmpty()) {
                        knownTags.firstOrNull { t ->
                            tags.none { it.equals(t, ignoreCase = true) } && t.lowercase().contains(q)
                        }
                    } else null
                    commitTag(fuzzyMatch ?: tagInput)
                },
                trailingContent = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Tag",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { commitTag(tagInput) }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { tagFieldFocused = it.isFocused }
            )

            if (tagSuggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    tagSuggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = { commitTag(suggestion) },
                            label = { Text(suggestion, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // ── Note body ────────────────────────────────────────────────
            // Readonly preview — matches Capacitor's #neText, which is
            // itself `readonly` and blurs immediately on focus. Tapping
            // anywhere on it opens the full-screen writer below instead of
            // typing in place. The invisible Box on top intercepts the tap
            // before the field itself can take focus, so there's never a
            // flash of cursor/keyboard here.
            NeSectionLabel("Note")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Write your note\u2026",
                        fontFamily = GelasioFontFamily,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = text,
                        fontFamily = GelasioFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 27.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showFullTextEditor = true }
                )
            }

            // ── Delete (edit mode only) ─────────────────────────────────
            // Matches Capacitor's #neDelete, which is hidden for a brand
            // new note (draftNote.id == null) and shown at the bottom of
            // the body when editing an existing one.
            if (noteItem.id > 0 && onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete note")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

        // Sits on top of everything else in this screen — matches
        // Capacitor's #noteTextEditor, a fixed-position overlay toggled
        // independently of the rest of the note editor markup.
        if (showFullTextEditor) {
            NoteTextFullScreenEditor(
                initialText = text,
                noteTitle = title,
                onCancel = { showFullTextEditor = false },
                onDone = { newText ->
                    text = newText
                    showFullTextEditor = false
                }
            )
        }
    }
}

// Dedicated distraction-free writer for the note body — matches
// Capacitor's #noteTextEditor overlay (Cancel / "Note" / Done header +
// a full-bleed textarea, autofocused ~50ms after opening). Edits here are
// local to `draft`; Cancel discards them, Done copies `draft` back up to
// the caller's `text` state.
@Composable
private fun NoteTextFullScreenEditor(
    initialText: String,
    // Shown in the header in place of the static "Note" label, so it's
    // clear which note this full-bleed writer belongs to — falls back to
    // "Note" for an untitled note, matching Capacitor's plain header when
    // draftNote.title is empty.
    noteTitle: String = "",
    onCancel: () -> Unit,
    onDone: (String) -> Unit
) {
    var draft by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        focusRequester.requestFocus()
    }
    // Hardware/gesture back cancels — matches Capacitor's back-button
    // handling, which routes #noteTextEditor through closeNoteTextEditor()
    // (discarding the draft) rather than through Done.
    BackHandler(onBack = onCancel)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Text(
                    text = noteTitle.ifBlank { "Note" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                )
                TextButton(onClick = { onDone(draft) }) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Matches Capacitor's #nteTextarea exactly: no border, no
            // background box — just text on the page. OutlinedTextField
            // always draws its outline (that's the whole point of it),
            // which is why this was showing a boxed/outlined rectangle
            // instead of a plain full-bleed writing surface.
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = GelasioFontFamily,
                        fontSize = 18.sp,
                        lineHeight = 29.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
                if (draft.isEmpty()) {
                    Text(
                        text = "Write your note\u2026",
                        fontFamily = GelasioFontFamily,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}
