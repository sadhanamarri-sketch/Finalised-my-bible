package com.example.mybible.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.SavedWordItem
import com.example.mybible.model.SavedWordLanguage
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.components.BackTopBar

/**
 * Personal glossary of words bookmarked from a Greek/Hebrew interlinear
 * lookup or an English dictionary lookup (see MainViewModel's
 * toggleSaveCurrent*Word functions and BibleRepository.toggleSavedWord).
 * Full page pushed over Search (opened from its top-bar bookmark icon),
 * same shape as [TagsScreen] being pushed over Notes.
 *
 * Rows are flat, most-recently-saved first — not grouped by language —
 * since the list is expected to stay short enough that a Greek/Hebrew/
 * English section split would be more chrome than it's worth. Tapping a
 * row with a recorded source verse (Greek/Hebrew only — see
 * SavedWordItem's doc) opens the same verse-mention preview sheet a note
 * or lexicon citation uses; English saves have no single source verse to
 * jump to, so their rows just aren't clickable.
 */
@Composable
fun SavedWordsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val savedWords by viewModel.savedWords.collectAsState(initial = emptyList())
    var wordPendingDelete by remember { mutableStateOf<SavedWordItem?>(null) }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Saved Words",
                onBack = { viewModel.closeSavedWordsScreen() }
            )
        },
        modifier = modifier
    ) { padding ->
        if (savedWords.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "No saved words yet. Tap the bookmark icon on a word's lookup (Greek, Hebrew, or English) to save it here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            val dividerColor = MaterialTheme.colorScheme.outlineVariant
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(savedWords, key = { it.id }) { saved ->
                    val hasSourceVerse = saved.sourceBook.isNotBlank()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (hasSourceVerse) {
                                    Modifier.clickable {
                                        viewModel.openVerseMentionPreview(
                                            saved.sourceBook,
                                            saved.sourceChapter,
                                            saved.sourceVerse
                                        )
                                    }
                                } else Modifier
                            )
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, size.height - strokeWidth / 2),
                                    end = Offset(size.width, size.height - strokeWidth / 2),
                                    strokeWidth = strokeWidth
                                )
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (saved.language) {
                                    SavedWordLanguage.GREEK -> "GREEK"
                                    SavedWordLanguage.HEBREW -> "HEBREW"
                                    SavedWordLanguage.ENGLISH -> "ENGLISH"
                                },
                                fontSize = 10.5.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = saved.word,
                                fontSize = 19.sp,
                                fontFamily = if (saved.language == SavedWordLanguage.ENGLISH) FontFamily.Default else FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            if (saved.transliteration.isNotBlank()) {
                                Text(
                                    text = saved.transliteration,
                                    fontSize = 13.5.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                            if (saved.gloss.isNotBlank()) {
                                Text(
                                    text = saved.gloss,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            if (hasSourceVerse) {
                                Text(
                                    text = "${saved.sourceBook} ${saved.sourceChapter}:${saved.sourceVerse}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (hasSourceVerse) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = { wordPendingDelete = saved }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove saved word",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    val deleteTarget = wordPendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { wordPendingDelete = null },
            title = { Text("Remove “${deleteTarget.word}”?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSavedWord(deleteTarget.id)
                        wordPendingDelete = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { wordPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
