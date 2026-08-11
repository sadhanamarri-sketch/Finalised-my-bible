@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mybible.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteItem
import com.example.mybible.model.NoteReference

/**
 * Full-height bottom sheet that replaces the old AlertDialog for reading a
 * note. Key improvements over the dialog:
 *   - No 560 dp height cap — the sheet grows to fill the screen.
 *   - Each Bible reference is tappable; tapping calls [onNavigateToVerse]
 *     so the user lands directly at that verse in the reader.
 *   - Keyboard-aware by default (ModalBottomSheet handles WindowInsets.ime).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteReaderDialog(
    noteItem: NoteItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    // Navigates to a verse and closes the sheet; wired in MainActivity.
    onNavigateToVerse: ((book: String, chapter: Int, verse: Int) -> Unit)? = null
) {
    val refs = noteItem.refs.ifEmpty {
        if (noteItem.book.isNotBlank() && noteItem.chapter > 0 && noteItem.verse > 0) {
            listOf(NoteReference(noteItem.book, noteItem.chapter, noteItem.verse, noteItem.verseText))
        } else emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = noteItem.title.ifBlank { "Untitled note" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (noteItem.noteDate.isNotBlank()) {
                        Text(
                            text = noteItem.noteDate,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close note")
                }
            }

            // ── References ──────────────────────────────────────────────────
            if (refs.isNotEmpty()) {
                Text(
                    text = "References",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                refs.forEach { ref ->
                    val isNavigable = onNavigateToVerse != null
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isNavigable) Modifier.clickable {
                                    onNavigateToVerse!!(ref.book, ref.chapter, ref.verse)
                                } else Modifier
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${ref.book} ${ref.chapter}:${ref.verse}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        if (isNavigable) {
                            Text(
                                text = "Tap to open in reader",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (ref.verseText.isNotBlank()) {
                            Text(
                                text = "\"${ref.verseText}\"",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Note body ───────────────────────────────────────────────────
            Text(
                text = noteItem.text.ifBlank { "No note text." },
                fontSize = 16.sp,
                lineHeight = 26.sp
            )

            // ── Tags ────────────────────────────────────────────────────────
            if (noteItem.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    noteItem.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }
            }

            // ── Action buttons ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        }
    }
}
