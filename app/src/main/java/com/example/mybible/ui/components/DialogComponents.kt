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
                    color = MaterialTheme.colorScheme.tertiary,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(entry.meanings) { meaning ->
                        // Elevated Card per part-of-speech group — matches
                        // the same list-row treatment used by Search,
                        // Notes, Highlighted Verses and Cross References.
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
                                            color = MaterialTheme.colorScheme.primary
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

// OnboardingTourDialog (a static 4-slide AlertDialog) has been replaced by
// the real guided tour — see ui/components/GuidedTourComponents.kt
// (TourChoiceDialog/GuidedTourOverlay/TourCuratedEndDialog) and
// MainViewModel's TourMode state machine.

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
                color = MaterialTheme.colorScheme.tertiary
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

/** Renames one fixed highlight color's label — the one piece of the old
 *  "Manage Highlight Colors" sheet kept after the rest (add/delete/
 *  enable-disable) was dropped for the fixed 12-color palette; see
 *  model/HighlightColors.kt. Triggered by long-pressing a swatch in
 *  VerseActionToolbar's highlight row (VerseComponents.kt). */
@Composable
fun RenameHighlightColorDialog(
    colorDef: HighlightColorDef,
    onDismiss: () -> Unit,
    onRename: (newLabel: String) -> Unit
) {
    var label by remember(colorDef.colorHex) { mutableStateOf(colorDef.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Color") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = label.trim()
                    if (trimmed.isNotEmpty()) onRename(trimmed)
                },
                enabled = label.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** "6:00 AM" / "9:00 PM" style label for an hour-of-day (0-23), no minutes
 *  — reminders only ever fire on the hour, so a full HH:MM time picker
 *  would be showing a control the user can't actually use meaningfully. */
fun formatHourLabel(hour: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val display = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$display:00 $period"
}

/**
 * Purpose-built hour-of-day picker (see [formatHourLabel]) — Settings'
 * "Active hours" reminder window uses two of these (start/end) instead of
 * Material3's full TimePicker, which includes a minute wheel that would
 * just be dead weight here. Tapping a row selects it and dismisses
 * immediately, matching how a single-value picker list is expected to work.
 */
@Composable
fun HourPickerDialog(
    title: String,
    selectedHour: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                items(24) { hour ->
                    val isSelected = hour == selectedHour
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                onSelect(hour)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatHourLabel(hour),
                            fontSize = 16.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
