@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.mybible

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.components.BackTopBar

data class HighlightedVerseItem(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val colorName: String = "Default"
)

/**
 * "Highlighted Verses" browser tab. Restyled to match the plain, flat-list
 * look used elsewhere in the app (e.g. NoteReaderScreen's ref line) rather
 * than a Material ListItem card:
 *  - a real back-arrow BackTopBar instead of a plain-text "Close" button,
 *    matching every other destination screen pushed from Reader's pill.
 *  - each row's color label (e.g. "Key Verse") in small-caps terracotta
 *    (primary), the book/chapter/verse reference in small-caps gold
 *    (tertiary) — the same treatment NoteReaderScreen gives its ref line.
 *  - no card/surface background on the row itself; rows are separated by a
 *    single hairline divider instead.
 */
@Composable
fun HighlightedVersesScreen(
    highlights: List<HighlightedVerseItem>,
    onOpenVerse: (HighlightedVerseItem) -> Unit,
    onClose: () -> Unit
) {
    var selectedColor by remember { mutableStateOf("All") }
    val colors = remember(highlights) {
        listOf("All") + highlights.map { it.colorName }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(highlights, selectedColor) {
        if (selectedColor == "All") highlights
        else highlights.filter { it.colorName == selectedColor }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Highlighted Verses",
                onBack = onClose,
                backContentDescription = "Back to Reader"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (colors.size > 1) {
                // FlowRow, not Row: a plain Row doesn't wrap, so once there
                // are enough color labels to overflow the screen width the
                // rest were simply clipped off-screen with no way to reach
                // them. FlowRow wraps overflow chips onto additional rows
                // instead, so every color stays reachable regardless of
                // count.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { color ->
                        val selected = selectedColor == color
                        FilterChip(
                            selected = selected,
                            onClick = { selectedColor = color },
                            label = { Text(color) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (highlights.isEmpty()) "No highlighted verses" else "No verses for this color",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.key }) { item ->
                        HighlightedVerseRow(item = item, onClick = { onOpenVerse(item) })
                        HighlightDividerLine()
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedVerseRow(
    item: HighlightedVerseItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        // Color label — small caps, letter-spaced, terracotta (primary).
        Text(
            text = item.colorName.uppercase(),
            fontSize = 12.5.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        // Reference — small caps, letter-spaced, gold (tertiary).
        Text(
            text = "${item.book} ${item.chapter}:${item.verse}".uppercase(),
            fontSize = 12.5.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        // Verse text.
        Text(
            text = item.text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HighlightDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}
