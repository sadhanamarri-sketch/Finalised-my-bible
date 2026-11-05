package com.example.mybible.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteReference
import com.example.mybible.model.RefRange
import com.example.mybible.model.Verse
import com.example.mybible.model.groupRefs
import com.example.mybible.ui.theme.SourceSerif4FontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

/**
 * The verses a note is on (made on a verse, or added with + Verse), apart from what's written: a row
 * for each passage, its reference and a chevron, folded at first so the note's own words come first.
 * A tap on the row opens or folds the verse's words; on the reference itself, opens the verse
 * ([onOpen], as the reference always has). Verses only named in the note's text aren't here: they're
 * links in it. Nothing at all for a note on no verse.
 *
 * Folded or open is kept, per note ([noteKey]) and passage, while the page is open (across a turn of
 * the screen too); every note opens with them folded. The words are what the note keeps with each
 * verse, or ([loadVerses]) the Bible's, with the Telugu under them when [showTelugu].
 */
@Composable
fun NoteVerseSection(
    refs: List<NoteReference>,
    noteKey: Long,
    onOpen: ((RefRange) -> Unit)?,
    modifier: Modifier = Modifier,
    loadVerses: (suspend (RefRange) -> List<Verse>)? = null,
    showTelugu: Boolean = false
) {
    val passages = remember(refs) { notePassages(refs) }
    if (passages.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((range, kept) in passages) {
            PassageRow(range, kept, noteKey, onOpen, loadVerses, showTelugu)
        }
    }
}

/**
 * The passages a note is on, in Bible order, each with the verses of it the note keeps: those it was
 * made on or had added (+ Verse), not those only named in its text (links there).
 */
internal fun notePassages(refs: List<NoteReference>): List<Pair<RefRange, List<NoteReference>>> {
    val attached = refs.filterNot { it.named }
    return groupRefs(attached, ::bibleBookIndex).map { range -> range to attached.filter { range.covers(it) }.sortedBy { it.verse } }
}

// "Proverbs 3:5–6 · 2 verses", "John 15 · whole chapter": how much a passage is, after its reference.
internal fun passageSize(range: RefRange): String? = when {
    range.wholeChapter -> "whole chapter"
    range.to > range.from -> "${range.to - range.from + 1} verses"
    else -> null
}

@Composable
private fun PassageRow(
    range: RefRange,
    kept: List<NoteReference>,
    noteKey: Long,
    onOpen: ((RefRange) -> Unit)?,
    loadVerses: (suspend (RefRange) -> List<Verse>)?,
    showTelugu: Boolean
) {
    var open by rememberSaveable(noteKey, range.label) { mutableStateOf(false) }
    // The Bible's words, read the first time it's opened; until then (or without them) the note's own.
    var loaded by remember(range, showTelugu) { mutableStateOf<List<Verse>?>(null) }
    LaunchedEffect(open, range, showTelugu) {
        if (open && loaded == null && loadVerses != null) loaded = runCatching { loadVerses(range) }.getOrNull().orEmpty()
    }
    val verses = loaded?.takeIf { it.isNotEmpty() }
        ?: kept.filter { it.verseText.isNotBlank() }
            .map { Verse(it.book, it.chapter, it.verse, it.verseText) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val gold = MaterialTheme.colorScheme.tertiary
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, gold.copy(alpha = 0.55f), shape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Fold the verse" else "Show the verse") { open = !open }
                .semantics { stateDescription = if (open) "Open" else "Folded" }
                .padding(start = 4.dp, end = 10.dp)
        ) {
            // The reference: opens the verse, as it always has.
            Text(
                text = range.label,
                fontSize = 13.5.sp,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                color = gold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onOpen != null) Modifier.clickable(onClickLabel = "Open ${range.label}") { onOpen(range) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            )
            passageSize(range)?.let { size ->
                Text(
                    text = "· $size",
                    fontSize = 13.sp,
                    fontFamily = WorkSansFontFamily,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(turn)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            ) {
                if (verses.isEmpty()) {
                    Text(
                        text = if (loadVerses != null && loaded == null) "Loading…" else "Open it to read it in the Reader.",
                        fontSize = 13.sp,
                        fontFamily = WorkSansFontFamily,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val numbered = verses.size > 1
                for (v in verses) {
                    PassageVerse(v, numbered, showTelugu)
                }
            }
        }
    }
}

// A verse in the Bible's look (as the verse preview shows one): its number first when there are more.
@Composable
private fun PassageVerse(verse: Verse, numbered: Boolean, showTelugu: Boolean) {
    val number = MaterialTheme.colorScheme.tertiary
    Column {
        Text(
            text = buildAnnotatedString {
                if (numbered) {
                    withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = number, baselineShift = BaselineShift(0.3f))) {
                        append("${verse.number}")
                    }
                    append(" ")
                }
                append(verse.text.trim())
            },
            fontSize = 16.sp,
            fontFamily = SourceSerif4FontFamily,
            lineHeight = 27.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        val telugu = verse.teluguText?.trim()
        if (showTelugu && !telugu.isNullOrEmpty()) {
            Row {
                if (numbered) Spacer(Modifier.width(14.dp))
                Text(
                    text = telugu,
                    fontSize = 15.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
