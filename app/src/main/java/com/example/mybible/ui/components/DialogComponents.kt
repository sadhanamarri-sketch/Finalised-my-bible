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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import com.example.mybible.ui.theme.FrauncesFontFamily
import com.example.mybible.ui.theme.literataOrTestFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

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
    onDismiss: () -> Unit,
    // Bookmark toggle — null hides the button entirely (no caller wired
    // it up), same "null means don't show it" convention VerseActionToolbar
    // uses for its own optional actions.
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null
) {
    // Without skipPartiallyExpanded, the sheet has a third settle point
    // halfway up (Material's own "partially expanded" state) alongside
    // hidden/fully-expanded — a swipe or drag that doesn't clear its
    // velocity/distance threshold settles there instead of continuing to
    // either end, which reads as the sheet getting "stuck" with content
    // cut off. Matches VerseActionToolbar/TagEditorSheet/Notes' filter
    // sheet, which already skip it.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = word.lowercase(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = literataOrTestFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (onToggleSave != null) {
                    IconButton(onClick = onToggleSave) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (isSaved) "Remove from saved words" else "Save word",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!entry?.phonetic.isNullOrEmpty()) {
                Text(
                    text = entry!!.phonetic!!,
                    fontSize = 14.sp,
                    fontFamily = WorkSansFontFamily,
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
                    fontFamily = WorkSansFontFamily,
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
                    fontFamily = WorkSansFontFamily,
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
                                        fontFamily = WorkSansFontFamily,
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
                                            fontFamily = literataOrTestFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = def,
                                            fontSize = 14.sp,
                                            fontFamily = literataOrTestFontFamily,
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
                Text("Close", fontFamily = WorkSansFontFamily)
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
    // See EnglishDictionarySheet's identical doc above — same fix for the
    // same "stuck halfway" bug.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "$book $chapter:$verse",
                fontSize = 16.sp,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = verseText ?: "Loading\u2026",
                fontSize = 15.sp,
                fontFamily = literataOrTestFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onOpenInReader,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open in Reader", fontFamily = WorkSansFontFamily)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", fontFamily = WorkSansFontFamily)
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

/** "6:00 AM" / "6:30 AM" / "9:00 PM" style label for a minutes-since-
 *  midnight value, at the 30-minute granularity the reminder window picker
 *  uses (see [ReminderTimePickerDialog]). */
fun formatMinutesLabel(minutesOfDay: Int): String {
    val hour = minutesOfDay / 60
    val minute = minutesOfDay % 60
    val period = if (hour < 12) "AM" else "PM"
    val display = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val minuteStr = if (minute == 0) "00" else minute.toString()
    return "$display:$minuteStr $period"
}

/**
 * Purpose-built 30-minute-step time-of-day picker (see [formatMinutesLabel])
 * — Settings' "Active hours" reminder window uses two of these (start/end)
 * instead of Material3's full TimePicker. `options` is supplied by the
 * caller rather than a fixed 0-23 range, since Start and End each need a
 * different valid range (see ReminderScheduler.WINDOW_START_MINUTES /
 * MIN_GAP_MINUTES) — Start is capped so a valid End always exists, and
 * End's own options depend on whatever Start currently is. Tapping a row
 * selects it and dismisses immediately, matching how a single-value picker
 * list is expected to work.
 */
@Composable
fun ReminderTimePickerDialog(
    title: String,
    options: List<Int>,
    selectedMinutes: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                items(options) { minutesOfDay ->
                    val isSelected = minutesOfDay == selectedMinutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                onSelect(minutesOfDay)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMinutesLabel(minutesOfDay),
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
