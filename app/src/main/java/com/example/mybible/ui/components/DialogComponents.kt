@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.mybible.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.mybible.model.EnglishDictionaryEntry
import com.example.mybible.model.HighlightColorDef

// A YYYY-MM-DD text field with a calendar icon that opens a native
// DatePickerDialog. Typing is still allowed (e.g. for quick edits or
// clearing the field), but most users will just tap the icon. [value]/
// [onValueChange] carry the same "yyyy-MM-dd" string the rest of the app
// (notes, filters, DB) already expects, so no callers elsewhere need to change.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    // Null skips the floating Material label entirely — used by the Note
    // editor, which puts its own gold NeSectionLabel ("Date") above the
    // field instead, matching Capacitor's separate .ne-section-label +
    // plain .date-input rather than a single floating-label box.
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    allowTyping: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    val isoFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (allowTyping) onValueChange(new.filter { it.isDigit() || it == '-' }.take(10))
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        readOnly = !allowTyping,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.DateRange, contentDescription = "Pick date")
            }
        },
        modifier = modifier
    )

    if (showPicker) {
        val initialMillis = runCatching { LocalDate.parse(value, isoFormatter) }
            .getOrNull()
            ?.atStartOfDay(ZoneId.of("UTC"))
            ?.toInstant()
            ?.toEpochMilli()

        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        onValueChange(picked.format(isoFormatter))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishDictionarySheet(
    word: String,
    entry: EnglishDictionaryEntry?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "ENGLISH DICTIONARY",
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = word.lowercase(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!entry?.phonetic.isNullOrEmpty()) {
                Text(
                    text = entry!!.phonetic!!,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            } else if (entry?.resolvedFrom != null) {
                // Webster's 1828 doesn't have "tribulations" as its own
                // headword, only "tribulation" — see
                // BibleRepository.lookupWebsterStem. Make clear the
                // definition below belongs to that base form, not a
                // literal entry for the word tapped.
                Text(
                    text = "Showing definition for \"${entry.resolvedFrom}\"",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (entry == null || entry.meanings.isEmpty()) {
                Text(
                    text = "No definition found for '$word'.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(entry.meanings) { meaning ->
                        Column {
                            if (meaning.partOfSpeech.isNotBlank()) {
                                Text(
                                    text = meaning.partOfSpeech.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            meaning.definitions.forEachIndexed { idx, def ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${idx + 1}. ",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = def,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    }
}

// NOTE: note editing moved off this file — see NoteEditorScreen (full page)
// in ui/screens/NoteEditorScreen.kt. Note reading moved similarly to
// ui/screens/NoteReaderScreen.kt. Both used to be ModalBottomSheet dialogs
// here/in NoteReaderDialog.kt; they're now pushed as full pages from
// MainActivity so "Add note" and tapping a note each open their own screen
// instead of a sheet layered over the notes list.

@Composable
fun OnboardingTourDialog(
    onDismiss: () -> Unit
) {
    var slide by remember { mutableStateOf(0) }

    val slides = listOf(
        Pair("Welcome to My Bible", "A minimalist, offline King James Version and Telugu Bible reader designed for focused Scripture study."),
        Pair("Bilingual & Interlinear", "Read KJV with optional inline Telugu translation and interlinear Greek NT / Hebrew OT word breakdowns."),
        Pair("Notes & Highlights", "Highlight verses in custom colors, take tagged personal study notes, and track your daily study progress."),
        Pair("Offline Ready", "All 66 Telugu books and KJV Bible text are stored right on your device for seamless offline reading.")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = slides[slide].first,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (slide) {
                        0 -> Icons.Default.MenuBook
                        1 -> Icons.Default.Translate
                        2 -> Icons.Default.EditNote
                        else -> Icons.Default.CloudDone
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = slides[slide].second,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    slides.indices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (idx == slide) 10.dp else 6.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (idx == slide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (slide < slides.lastIndex) {
                        slide++
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (slide < slides.lastIndex) "Next" else "Get Started")
            }
        },
        dismissButton = {
            if (slide > 0) {
                TextButton(onClick = { slide-- }) {
                    Text("Back")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Skip")
                }
            }
        }
    )
}

/**
 * Small preview sheet shown when a verse mention inside note text (e.g.
 * "See John 3:16") is tapped — mirrors Capacitor's openVerseTextSheet():
 * reference, verse text (or a "not available offline" fallback while/if it
 * can't be loaded), and an "Open in Reader" button. Deliberately does not
 * navigate to the Reader automatically — matches Capacitor requiring the
 * explicit tap on vtsOpenReader.
 */
@Composable
fun VerseMentionPreviewSheet(
    book: String,
    chapter: Int,
    verse: Int,
    verseText: String?,
    onOpenInReader: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "$book $chapter:$verse",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = verseText ?: "Loading\u2026",
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onOpenInReader,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open in Reader")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    }
}

// A modest curated palette for picking a new color's hex — avoids pulling
// in a full color-picker dependency for what's a fairly rare action.
private val MANAGE_COLOR_PRESETS = listOf(
    "#F2C94C", "#7DBE7D", "#7FB2E0", "#E38FA8",
    "#FFF1A8", "#C8E6C9", "#BBDEFB", "#F8BBD0",
    "#E1BEE7", "#FFCCBC", "#D7CCC8", "#B2DFDB"
)

private fun parseManageColorHex(hex: String): Color? {
    return try {
        val cleaned = hex.removePrefix("#")
        if (cleaned.length != 6) return null
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (e: Exception) {
        null
    }
}

// "Manage Highlight Colors" — opened from the verse action toolbar's
// "Manage" swatch (VerseActionToolbar's onManageHighlightColors). Lets the
// user rename a color's label, disable/re-enable it (hides it from the
// swatch picker without touching verses already highlighted with it, and
// is how you go from e.g. 8 colors down to just the 5 you actually use),
// delete a color outright, or add a new one from a small preset palette.
@Composable
fun ManageHighlightColorsSheet(
    colorDefs: List<HighlightColorDef>,
    onRename: (colorHex: String, newLabel: String) -> Unit,
    onSetEnabled: (colorHex: String, enabled: Boolean) -> Unit,
    onDelete: (colorHex: String) -> Unit,
    onAdd: (label: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit
) {
    val enabledCount = colorDefs.count { it.enabled }
    var showAddRow by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newColorHex by remember { mutableStateOf(MANAGE_COLOR_PRESETS.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manage Highlight Colors",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap a label to rename it. Turn a color off to hide it from the picker without deleting it.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            colorDefs.forEach { def ->
                var editingLabel by remember(def.colorHex) { mutableStateOf(def.label) }
                var isEditing by remember(def.colorHex) { mutableStateOf(false) }
                // Guard the very last enabled color's switch — disabling it
                // would leave nothing pickable in the toolbar.
                val isOnlyEnabledLeft = def.enabled && enabledCount <= 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(parseManageColorHex(def.colorHex) ?: Color.Gray)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editingLabel,
                            onValueChange = { editingLabel = it },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )
                        IconButton(onClick = {
                            val trimmed = editingLabel.trim()
                            if (trimmed.isNotEmpty()) onRename(def.colorHex, trimmed)
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save label", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Text(
                            text = def.label,
                            fontSize = 14.sp,
                            color = if (def.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isEditing = true }
                        )
                        IconButton(onClick = { isEditing = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit label",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = def.enabled,
                        onCheckedChange = { checked ->
                            if (!(isOnlyEnabledLeft && !checked)) onSetEnabled(def.colorHex, checked)
                        },
                        enabled = !isOnlyEnabledLeft,
                        modifier = Modifier.testTag("highlight_color_enabled_${def.colorHex}")
                    )

                    IconButton(onClick = { onDelete(def.colorHex) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete color",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (showAddRow) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("New color label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MANAGE_COLOR_PRESETS.forEach { hex ->
                            val isSelected = newColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseManageColorHex(hex) ?: Color.Gray)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { newColorHex = hex }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddRow = false; newLabel = "" }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = newLabel.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAdd(trimmed, newColorHex)
                                    newLabel = ""
                                    showAddRow = false
                                }
                            },
                            enabled = newLabel.trim().isNotEmpty()
                        ) {
                            Text("Add")
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showAddRow = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Color")
                }
            }
        }
    }
}
