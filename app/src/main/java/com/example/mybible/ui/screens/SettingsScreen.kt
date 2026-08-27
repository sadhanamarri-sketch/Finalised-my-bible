@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.mybible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.ThemeMode
import com.example.mybible.reminders.ReminderFrequency
import com.example.mybible.reminders.ReminderScheduler
import com.example.mybible.reminders.ReminderTheme
import com.example.mybible.ui.MainViewModel
import com.example.mybible.ui.NavTab
import com.example.mybible.ui.components.BackTopBar
import com.example.mybible.ui.components.DsOutlineAccentButton
import com.example.mybible.ui.components.DsSectionLabel
import com.example.mybible.ui.components.DsSizeAdjustRow
import com.example.mybible.ui.components.DsSwitch
import com.example.mybible.ui.components.DsToggleRow
import com.example.mybible.ui.components.ReminderTimePickerDialog
import com.example.mybible.ui.components.formatMinutesLabel
import com.example.mybible.ui.theme.EbGaramondFontFamily
import com.example.mybible.ui.theme.GelasioFontFamily
import com.example.mybible.ui.theme.LoraFontFamily
import com.example.mybible.ui.theme.MerriweatherFontFamily
import com.example.mybible.ui.theme.PlayfairDisplayFontFamily
import com.example.mybible.ui.theme.VerseCardFontLab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatBackupTimestamp(millis: Long): String {
    if (millis <= 0L) return "Never"
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
}

private fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.PAPER -> "Paper"
    ThemeMode.SEPIA -> "Sepia"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.CLASSIC_DARK -> "Classic Dark"
}

data class FontOption(val id: String, val label: String, val family: FontFamily)

// Bug fix: these previously all pointed at FontFamily.Serif, so every chip
// in the picker rendered identically and picking a font looked unapplied.
// Each now uses its real bundled typeface (see ui/theme/AppFonts.kt).
// System Sans / Monospace / Cursive removed — not real reading fonts for
// Bible text, just leftover generic Android fallbacks.
val FONT_OPTIONS_LIST = listOf(
    FontOption("georgia", "Georgia", GelasioFontFamily),
    FontOption("lora", "Lora (Serif)", LoraFontFamily),
    FontOption("garamond", "EB Garamond", EbGaramondFontFamily),
    FontOption("merriweather", "Merriweather", MerriweatherFontFamily),
    FontOption("playfair", "Playfair Display", PlayfairDisplayFontFamily)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onShowTour: () -> Unit,
    onToggleReminders: (Boolean) -> Unit = { viewModel.setRemindersEnabled(it) },
    onExportLocal: () -> Unit = {},
    onImportLocal: () -> Unit = {},
    onDriveSignIn: () -> Unit = {},
    onDriveSignOut: () -> Unit = { viewModel.signOutOfDrive() },
    onDriveBackup: () -> Unit = {},
    onDriveRestore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val teluguFontSizeSp by viewModel.teluguFontSizeSp.collectAsState()
    val greekFontSizeSp by viewModel.greekFontSizeSp.collectAsState()
    val hebrewFontSizeSp by viewModel.hebrewFontSizeSp.collectAsState()
    val verseSpacingDp by viewModel.verseSpacingDp.collectAsState()
    val englishLineHeightMultiplier by viewModel.englishLineHeightMultiplier.collectAsState()
    val teluguLineHeightMultiplier by viewModel.teluguLineHeightMultiplier.collectAsState()
    val englishFontFamilyName by viewModel.englishFontFamilyName.collectAsState()
    val isRedLetterEnabled by viewModel.redLetterEnabled.collectAsState()
    val showTeluguInline by viewModel.showTeluguInline.collectAsState()
    val showInterlinear by viewModel.showInterlinear.collectAsState()
    val isBlurModeEnabled by viewModel.isBlurModeEnabled.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val reminderFrequency by viewModel.reminderFrequency.collectAsState()
    val reminderStartMinutes by viewModel.reminderStartMinutes.collectAsState()
    val reminderEndMinutes by viewModel.reminderEndMinutes.collectAsState()
    val reminderEnabledThemes by viewModel.reminderEnabledThemes.collectAsState()
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val driveAccount by viewModel.driveAccount.collectAsState()
    val isDriveSyncing by viewModel.isDriveSyncing.collectAsState()
    val lastDriveBackupAt by viewModel.lastDriveBackupAt.collectAsState()
    val lastDriveRestoreAt by viewModel.lastDriveRestoreAt.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()

    // SettingsScreen is fully disposed (not just hidden) on a tab switch,
    // same as Notes/Studied/Search — save scroll position on the way out so
    // leaving and coming back doesn't reset to the top of the page.
    val savedScrollPosition by viewModel.settingsScrollPosition.collectAsState()
    val scrollState = rememberScrollState(initial = savedScrollPosition)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveSettingsScrollPosition(scrollState.value)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupStatusMessage) {
        val message = backupStatusMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeBackupStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(title = "Settings", onBack = { viewModel.selectTab(NavTab.READER) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        // ---- Appearance ----
        DsSectionLabel("Appearance", isFirst = true)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = themeMode == mode
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setTheme(mode) },
                    label = { Text(mode.displayLabel(), fontSize = 12.sp) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Red letter ----
        DsSectionLabel("Red letter")
        DsToggleRow(
            label = "Words of Christ in red",
            subLabel = "Highlights verses spoken by Jesus",
            checked = isRedLetterEnabled,
            onCheckedChange = { viewModel.setRedLetterEnabled(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Greek ----
        DsSectionLabel("Greek")
        DsToggleRow(
            label = "Greek transliteration",
            subLabel = "Shows original Greek words under each New Testament verse, with transliteration and gloss. Text: TAGNT by STEPBible.org / Tyndale House Cambridge (CC BY 4.0)",
            checked = showInterlinear,
            onCheckedChange = { viewModel.toggleInterlinear() }
        )
        Spacer(modifier = Modifier.height(10.dp))
        DsSizeAdjustRow(
            valueLabel = "${greekFontSizeSp}px",
            onDecrease = { viewModel.adjustGreekFontSize(-1) },
            onIncrease = { viewModel.adjustGreekFontSize(1) }
        )

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Hebrew ----
        // Shares the same on/off switch as Greek above (one "interlinear"
        // setting for whichever testament you're actually reading — the
        // Reader's pill toggle is the same single flag too), so this row
        // and the Greek one always show the same checked state. Only the
        // text size is genuinely independent.
        DsSectionLabel("Hebrew")
        DsToggleRow(
            label = "Hebrew transliteration",
            subLabel = "Shows original Hebrew words under each Old Testament verse, with transliteration and gloss. Uses the same on/off switch as Greek above. Text: TAHOT by STEPBible.org / Tyndale House Cambridge (CC BY 4.0)",
            checked = showInterlinear,
            onCheckedChange = { viewModel.toggleInterlinear() }
        )
        Spacer(modifier = Modifier.height(10.dp))
        DsSizeAdjustRow(
            valueLabel = "${hebrewFontSizeSp}px",
            onDecrease = { viewModel.adjustHebrewFontSize(-1) },
            onIncrease = { viewModel.adjustHebrewFontSize(1) }
        )

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Telugu ----
        DsSectionLabel("Telugu")
        DsToggleRow(
            label = "Telugu verses",
            subLabel = "Shows a Telugu translation under each verse. Text: BSI Telugu O.V., via wordproject.org",
            checked = showTeluguInline,
            onCheckedChange = { viewModel.toggleTeluguInline() }
        )
        Spacer(modifier = Modifier.height(10.dp))
        DsSizeAdjustRow(
            valueLabel = "${teluguFontSizeSp}px",
            onDecrease = { viewModel.adjustTeluguFontSize(-1) },
            onIncrease = { viewModel.adjustTeluguFontSize(1) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Line spacing",
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        DsSizeAdjustRow(
            valueLabel = "%.2fx".format(teluguLineHeightMultiplier),
            onDecrease = { viewModel.adjustTeluguLineHeight(-0.1f) },
            onIncrease = { viewModel.adjustTeluguLineHeight(0.1f) },
            decreaseGlyph = "\u2212",
            increaseGlyph = "+",
            decreaseGlyphSize = 18.sp,
            increaseGlyphSize = 18.sp
        )

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- English text size ----
        DsSectionLabel("English text size")
        DsSizeAdjustRow(
            valueLabel = "${fontSizeSp}px",
            onDecrease = { viewModel.adjustFontSize(-1) },
            onIncrease = { viewModel.adjustFontSize(1) }
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Line spacing",
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        DsSizeAdjustRow(
            valueLabel = "%.2fx".format(englishLineHeightMultiplier),
            onDecrease = { viewModel.adjustEnglishLineHeight(-0.1f) },
            onIncrease = { viewModel.adjustEnglishLineHeight(0.1f) },
            decreaseGlyph = "\u2212",
            increaseGlyph = "+",
            decreaseGlyphSize = 18.sp,
            increaseGlyphSize = 18.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ---- Verse spacing ----
        DsSectionLabel("Verse spacing")
        DsSizeAdjustRow(
            valueLabel = "${verseSpacingDp}dp",
            onDecrease = { viewModel.adjustVerseSpacing(-2) },
            onIncrease = { viewModel.adjustVerseSpacing(2) },
            decreaseGlyph = "\u2212",
            increaseGlyph = "+",
            decreaseGlyphSize = 18.sp,
            increaseGlyphSize = 18.sp
        )

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Font ----
        DsSectionLabel("Font")
        val selectedFontOpt = FONT_OPTIONS_LIST.firstOrNull {
            it.id.equals(englishFontFamilyName, ignoreCase = true)
        } ?: FONT_OPTIONS_LIST.first() // legacy "Serif" and anything unrecognized -> Georgia (now first/default)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_font_section")
        ) {
            FONT_OPTIONS_LIST.forEach { fontOpt ->
                val isActive = fontOpt.id == selectedFontOpt.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setEnglishFontFamilyName(fontOpt.id) }
                        .padding(vertical = 14.dp)
                        .testTag("settings_font_option_${fontOpt.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fontOpt.label,
                        fontFamily = fontOpt.family,
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Text(
                            text = "\u2713",
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "In the beginning God created the heaven and the earth.",
                fontFamily = selectedFontOpt.family,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.85f).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 22.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Verse Card Font Lab (testing) ----
        // Temporary: see ui/theme/VerseCardFontLab.kt. Not persisted —
        // resets on app restart by design. One shared choice drives all
        // four surfaces at once (Search, Cross References, verse-mention
        // preview, Highlighted Verses) since they're kept in sync.
        DsSectionLabel("Verse Card Font Lab (testing)")
        Text(
            text = "Not saved — resets when you restart the app. Applies to Search, Cross References, the verse-mention preview, and Highlighted Verses cards together.",
            fontSize = 12.5.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { VerseCardFontLab.selectedFontKey = null }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Default (Literata)",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (VerseCardFontLab.selectedFontKey == null) {
                    Text("✓", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            VerseCardFontLab.testFonts.forEach { (key, family) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { VerseCardFontLab.selectedFontKey = key }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = key, fontFamily = family, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (VerseCardFontLab.selectedFontKey == key) {
                        Text("✓", fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Font size",
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        DsSizeAdjustRow(
            valueLabel = "${VerseCardFontLab.fontSizeSp.toInt()}px",
            onDecrease = { VerseCardFontLab.fontSizeSp = (VerseCardFontLab.fontSizeSp - 1f).coerceAtLeast(12f) },
            onIncrease = { VerseCardFontLab.fontSizeSp = (VerseCardFontLab.fontSizeSp + 1f).coerceAtMost(28f) }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Line spacing",
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        DsSizeAdjustRow(
            valueLabel = "%.2fx".format(VerseCardFontLab.lineHeightMultiplier),
            onDecrease = { VerseCardFontLab.lineHeightMultiplier = (VerseCardFontLab.lineHeightMultiplier - 0.1f).coerceAtLeast(1f) },
            onIncrease = { VerseCardFontLab.lineHeightMultiplier = (VerseCardFontLab.lineHeightMultiplier + 0.1f).coerceAtMost(2.5f) },
            decreaseGlyph = "−",
            increaseGlyph = "+",
            decreaseGlyphSize = 18.sp,
            increaseGlyphSize = 18.sp
        )

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Focus mode ----
        DsSectionLabel("Focus mode")
        DsToggleRow(
            label = "Blur unread verses",
            subLabel = "Keeps attention on the verse you're reading",
            checked = isBlurModeEnabled,
            onCheckedChange = { viewModel.toggleBlurMode() }
        )

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Reminders ----
        DsSectionLabel("Reminders")
        val startMinutesForSummary = reminderStartMinutes
        val endMinutesForSummary = reminderEndMinutes
        DsToggleRow(
            label = "Reading reminders",
            subLabel = if (startMinutesForSummary != null && endMinutesForSummary != null) {
                val notificationsPerDay = (endMinutesForSummary - startMinutesForSummary) / (reminderFrequency.stepHours * 60) + 1
                "$notificationsPerDay ${if (notificationsPerDay == 1) "nudge" else "nudges"} a day, " +
                    "every ${reminderFrequency.stepHours}h from ${formatMinutesLabel(startMinutesForSummary)}\u2013" +
                    "${formatMinutesLabel(endMinutesForSummary)}, pointing back to where you left off"
            } else {
                "Choose active hours below to turn these on"
            },
            checked = remindersEnabled,
            onCheckedChange = onToggleReminders
        )

        if (remindersEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Frequency",
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReminderFrequency.entries.forEach { freq ->
                    val selected = freq == reminderFrequency
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.setReminderFrequency(freq) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = freq.label,
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Active hours",
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = reminderStartMinutes?.let { formatMinutesLabel(it) } ?: "Select",
                    fontSize = 14.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { showStartTimePicker = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Text(
                    text = "to",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Disabled (no click, dimmed) until Start has a value —
                // there's no valid End range to offer otherwise.
                Text(
                    text = reminderEndMinutes?.let { formatMinutesLabel(it) } ?: "Select",
                    fontSize = 14.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = if (reminderStartMinutes != null) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .then(
                            if (reminderStartMinutes != null) Modifier.clickable { showEndTimePicker = true }
                            else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Message themes",
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ReminderTheme.entries.forEach { theme ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = theme.label,
                        fontSize = 14.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 14.dp)
                    )
                    DsSwitch(
                        checked = theme in reminderEnabledThemes,
                        onCheckedChange = { viewModel.setReminderThemeEnabled(theme, it) }
                    )
                }
            }
        }

        if (showStartTimePicker) {
            // Capped at WINDOW_END_MINUTES - MIN_GAP_MINUTES (5:00 PM) so a
            // valid 4h+ End always exists within the 6am-9pm window,
            // whatever Start ends up being.
            val startOptions = generateSequence(ReminderScheduler.WINDOW_START_MINUTES) { it + ReminderScheduler.MINUTE_STEP }
                .takeWhile { it <= ReminderScheduler.WINDOW_END_MINUTES - ReminderScheduler.MIN_GAP_MINUTES }
                .toList()
            ReminderTimePickerDialog(
                title = "Start time",
                options = startOptions,
                selectedMinutes = reminderStartMinutes,
                onDismiss = { showStartTimePicker = false },
                onSelect = { viewModel.setReminderStartMinutes(it) }
            )
        }
        val startMinutesForEndPicker = reminderStartMinutes
        if (showEndTimePicker && startMinutesForEndPicker != null) {
            val endOptions = generateSequence(startMinutesForEndPicker + ReminderScheduler.MIN_GAP_MINUTES) { it + ReminderScheduler.MINUTE_STEP }
                .takeWhile { it <= ReminderScheduler.WINDOW_END_MINUTES }
                .toList()
            ReminderTimePickerDialog(
                title = "End time",
                options = endOptions,
                selectedMinutes = reminderEndMinutes,
                onDismiss = { showEndTimePicker = false },
                onSelect = { viewModel.setReminderEndMinutes(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Backup & sync ----
        DsSectionLabel("Backup & sync")
        Text(
            text = "Notes, tags, highlights, and completed verses",
            fontSize = 13.5.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        Text(
            text = "This device",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DsOutlineAccentButton(
                text = "Export file",
                onClick = onExportLocal,
                modifier = Modifier.weight(1f).testTag("settings_export_local_button")
            )
            DsOutlineAccentButton(
                text = "Import file",
                onClick = onImportLocal,
                modifier = Modifier.weight(1f).testTag("settings_import_local_button")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Google Drive",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (driveAccount == null) {
            Text(
                text = "Not signed in",
                fontSize = 13.5.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            DsOutlineAccentButton(
                text = "Sign in to Google Drive",
                onClick = onDriveSignIn,
                modifier = Modifier.testTag("settings_drive_signin_button")
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = driveAccount?.email ?: "Signed in",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sign out",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onDriveSignOut)
                        .padding(4.dp)
                )
            }
            Text(
                text = "Last backup: ${formatBackupTimestamp(lastDriveBackupAt)} \u00b7 " +
                    "Last restore: ${formatBackupTimestamp(lastDriveRestoreAt)}",
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DsOutlineAccentButton(
                    text = "Back up",
                    onClick = onDriveBackup,
                    enabled = !isDriveSyncing,
                    modifier = Modifier.weight(1f).testTag("settings_drive_backup_button")
                )
                DsOutlineAccentButton(
                    text = "Restore",
                    onClick = onDriveRestore,
                    enabled = !isDriveSyncing,
                    modifier = Modifier.weight(1f).testTag("settings_drive_restore_button")
                )
            }
            if (isDriveSyncing) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(14.dp))
            DsToggleRow(
                label = "Auto backup",
                subLabel = "Sync to Drive daily",
                checked = autoBackupEnabled,
                onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                modifier = Modifier.testTag("settings_auto_backup_toggle")
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 22.dp), color = MaterialTheme.colorScheme.surfaceVariant)

        // ---- Help ----
        DsSectionLabel("Help")
        Text(
            text = "A quick refresher on navigating, highlighting, notes, and offline sync.",
            fontSize = 15.5.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        DsOutlineAccentButton(
            text = "Show app tour",
            onClick = onShowTour,
            modifier = Modifier.testTag("settings_start_tour_button")
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
    }
}
