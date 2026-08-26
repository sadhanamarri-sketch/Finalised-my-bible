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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.ThemeMode
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.theme.LiterataFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

data class HighlightedVerseItem(
    val key: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val colorName: String = "Default",
    // Text of the linked quick-note (see HighlightItem.noteId), when one
    // exists — shown as a truncated italic preview under the verse.
    val noteText: String? = null
)

/**
 * "Highlighted Verses" browser tab.
 *  - a real back-arrow BackTopBar instead of a plain-text "Close" button,
 *    matching every other destination screen pushed from Reader's pill.
 *  - each row's color label (e.g. "Key Verse") in small-caps terracotta
 *    (primary), the book/chapter/verse reference in small-caps gold
 *    (tertiary) — the same treatment NoteReaderScreen gives its ref line.
 *  - rows are filled, rounded cards (surfaceContainerHigh) — matches the
 *    elevated-card look used by Search results and the Notes list — rather
 *    than a flat row on the page background, which nearly matched it in
 *    the darker themes and made rows hard to tell apart.
 */
@Composable
fun HighlightedVersesScreen(
    highlights: List<HighlightedVerseItem>,
    themeMode: ThemeMode,
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
                            label = { Text(color, fontFamily = WorkSansFontFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                // Classic Dark's primaryContainer (accent-solid,
                                // a rust/coral) and primary (a lighter coral)
                                // sit too close in hue/lightness for the
                                // normal primary-on-primaryContainer label to
                                // read clearly, so this theme specifically
                                // gets plain black label text on the selected
                                // chip instead.
                                selectedLabelColor = if (themeMode == ThemeMode.CLASSIC_DARK) {
                                    Color.Black
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
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
                        fontFamily = WorkSansFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.key }) { item ->
                        HighlightedVerseRow(item = item, onClick = { onOpenVerse(item) })
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
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Color label — small caps, letter-spaced, terracotta (primary).
            Text(
                text = item.colorName.uppercase(),
                fontSize = 12.5.sp,
                letterSpacing = 1.5.sp,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            // Reference — matches DsSectionLabel's gold heading style
            // exactly (Settings/Search): 12.5sp, 1.5sp letter-spacing,
            // Work Sans, tertiary, not bold.
            Text(
                text = "${item.book} ${item.chapter}:${item.verse}".uppercase(),
                fontSize = 12.5.sp,
                letterSpacing = 1.5.sp,
                fontFamily = WorkSansFontFamily,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            // Verse text — onSurface (not a literal white) so it stays
            // readable against the light-toned themes too; in Classic Dark
            // this already resolves to a near-white cream.
            Text(
                text = item.text,
                fontSize = 15.sp,
                fontFamily = LiterataFontFamily,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.noteText.isNullOrBlank()) {
                Text(
                    text = item.noteText.let { if (it.length > 90) it.take(89) + "…" else it },
                    fontSize = 13.sp,
                    fontFamily = WorkSansFontFamily,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
