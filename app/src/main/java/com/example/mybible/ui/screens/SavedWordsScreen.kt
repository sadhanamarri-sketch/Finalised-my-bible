@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mybible.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.SavedWordItem
import com.example.mybible.model.SavedWordLanguage
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.components.BackTopBar
import kotlinx.coroutines.delay

/**
 * Personal glossary of words bookmarked from a Greek/Hebrew interlinear
 * lookup or an English dictionary lookup (see MainViewModel's
 * toggleSaveCurrent*Word functions and BibleRepository.toggleSavedWord).
 * Full page pushed over Search (opened from its top-bar star icon), same
 * shape as [TagsScreen] being pushed over Notes.
 *
 * A segmented language filter (default English) replaces the old per-row
 * "GREEK"/"HEBREW"/"ENGLISH" label — with the list already scoped to one
 * language at a time, repeating it on every row would be redundant. Rows
 * favor English readability over the original script (see the row content
 * doc below), since most users saving a Greek/Hebrew word can't read the
 * native script and are here for the gloss.
 */
@Composable
fun SavedWordsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val savedWords by viewModel.savedWords.collectAsState(initial = emptyList())
    var selectedLanguage by remember { mutableStateOf(SavedWordLanguage.ENGLISH) }
    var wordPendingDelete by remember { mutableStateOf<SavedWordItem?>(null) }
    val filteredWords = savedWords.filter { it.language == selectedLanguage }
    val lastTappedKey by viewModel.savedWordsLastTappedKey.collectAsState()
    val listState = rememberLazyListState()

    // Marks the last-tapped row with an accent bar on return (e.g. coming
    // back from that word's lexicon/dictionary entry) — cleared on the
    // first scroll (same idea as Search/Cross References' own last-tapped
    // markers) or after 3s, whichever comes first: unlike a results list
    // you're actively scanning, Saved Words is often revisited to check
    // just the one word, so there may be no scroll at all to clear it.
    // clearSavedWordsLastTapped is idempotent, so both racing to call it
    // is harmless.
    LaunchedEffect(lastTappedKey) {
        if (lastTappedKey == null) return@LaunchedEffect
        val landedIndex = listState.firstVisibleItemIndex
        val landedOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, offset) ->
                if (idx != landedIndex || kotlin.math.abs(offset - landedOffset) > 4) {
                    viewModel.clearSavedWordsLastTapped()
                }
            }
    }
    LaunchedEffect(lastTappedKey) {
        if (lastTappedKey == null) return@LaunchedEffect
        delay(3000)
        viewModel.clearSavedWordsLastTapped()
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Saved Words",
                onBack = { viewModel.closeSavedWordsScreen() }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val languages = listOf(
                    SavedWordLanguage.ENGLISH to "English",
                    SavedWordLanguage.GREEK to "Greek",
                    SavedWordLanguage.HEBREW to "Hebrew"
                )
                languages.forEachIndexed { index, (language, label) ->
                    SegmentedButton(
                        selected = selectedLanguage == language,
                        onClick = { selectedLanguage = language },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size)
                    ) {
                        Text(label)
                    }
                }
            }

            if (savedWords.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "No saved words yet. Tap the star icon on a word's lookup (Greek, Hebrew, or English) to save it here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (filteredWords.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "No saved ${selectedLanguage.name.lowercase().replaceFirstChar { it.uppercase() }} words yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val dividerColor = MaterialTheme.colorScheme.outlineVariant
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredWords, key = { it.id }) { saved ->
                        val hasSourceVerse = saved.sourceBook.isNotBlank()
                        val isLastTapped = lastTappedKey == saved.id.toString()
                        // A soft background wash across the whole row
                        // instead of a card's left-edge accent bar — this
                        // list is flat/divider-separated, not cards, so an
                        // edge bar had nothing to visually attach to.
                        // Appears instantly, fades out slowly (see
                        // clearSavedWordsLastTapped's caller), same timing
                        // as the accent bar it replaced.
                        val rowTint by animateColorAsState(
                            targetValue = if (isLastTapped) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            },
                            animationSpec = if (isLastTapped) snap() else tween(durationMillis = 1000),
                            label = "savedWordRowTint"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowTint)
                                .clickable { viewModel.openLexiconForSavedWord(saved) }
                                .drawBehind {
                                    val strokeWidth = 1.dp.toPx()
                                    drawLine(
                                        color = dividerColor,
                                        start = Offset(0f, size.height - strokeWidth / 2),
                                        end = Offset(size.width, size.height - strokeWidth / 2),
                                        strokeWidth = strokeWidth
                                    )
                                }
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                                // Native word, then transliteration, then
                                // the English translation — reading order
                                // for a Greek/Hebrew entry. The translation
                                // stays the big/gold emphasis line (most
                                // users can't read the native script and
                                // are here for the gloss), it's just last
                                // rather than first. An English entry has
                                // no native/transliteration lines at all —
                                // the word itself is already the readable
                                // form.
                                val isForeign = saved.language != SavedWordLanguage.ENGLISH
                                if (isForeign && saved.word.isNotBlank()) {
                                    Text(
                                        text = saved.word,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (saved.transliteration.isNotBlank()) {
                                    Text(
                                        text = saved.transliteration,
                                        fontSize = 13.5.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                val translation = if (isForeign) saved.gloss.ifBlank { saved.word } else saved.word
                                Text(
                                    text = translation,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(top = if (isForeign) 4.dp else 0.dp)
                                )
                                if (hasSourceVerse) {
                                    Text(
                                        text = "${saved.sourceBook} ${saved.sourceChapter}:${saved.sourceVerse}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Box {
                                var showMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Word options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Open in Reader") },
                                        leadingIcon = { Icon(Icons.Default.Book, contentDescription = null) },
                                        enabled = hasSourceVerse,
                                        onClick = {
                                            showMenu = false
                                            viewModel.openReaderForSavedWord(saved)
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
                                            wordPendingDelete = saved
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
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
