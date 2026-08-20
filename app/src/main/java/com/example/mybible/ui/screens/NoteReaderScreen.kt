@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.mybible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteItem
import com.example.mybible.ui.components.LinkifiedNoteText

/**
 * Full-page note reader — ported to match Capacitor's #noteReaderSheet
 * exactly (see www/index.html: #noteReaderHead / #noteReaderBody /
 * #noteReaderRef / #noteReaderTitle / #noteReaderText), rather than the
 * Material TopAppBar look the rest of this app's full-page screens use.
 *
 * Layout, top to bottom:
 *  - Header: back-arrow icon (left) / the note's own title, centered,
 *    uppercase and styled to match the Notes-list card title (bold
 *    sans-serif, gold/tertiary), falling back to "Untitled note" when
 *    untitled / pen (edit) icon (right), thin bottom border. Both icons
 *    use onBackground (not a literal white) so they stay visible in the
 *    light-toned themes too, not just the dark ones.
 *    Capacitor's header title is always the literal word "Note" instead —
 *    this is a deliberate deviation so it's clear which note is open at a
 *    glance, same reasoning as the full-screen note-text writer's header.
 *  - Ref line: gold outlined pill per reference (matches the same
 *    ref-chip style used in the Notes list and editor), or the note's
 *    date if it has no references, or "Note" if it has neither — matching
 *    Capacitor's `n.refs.length ? ... : (n.date ? formatDateStr(n.date) :
 *    'Note')`. Capacitor's own #noteReaderRef has no click handler at
 *    all, but each reference here is individually tappable and opens the
 *    verse-preview sheet (verse text + "Open in Reader") — same as
 *    tapping a verse mention in the body text below. A deliberate
 *    deviation kept from the pre-Capacitor version rather than a
 *    straight port.
 *  - Body text: no separate title block — the title only appears once, in
 *    the header. Verse mentions inside the body (e.g. "See John 3:16") are
 *    still tappable via LinkifiedNoteText, opening the small verse-preview
 *    sheet — matches Capacitor's linkifyXrefs applied to #noteReaderText.
 *
 * Capacitor's note reader doesn't show tags at all, so this doesn't
 * either — they're still visible on the note's row in the list and in
 * the editor.
 */
@Composable
fun NoteReaderScreen(
    noteItem: NoteItem,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    // Tapping a reference in the ref line — or a verse mention inside the
    // body text (see LinkifiedNoteText below) — opens the small
    // verse-preview sheet (verse text + "Open in Reader"), same as tapping
    // a reference chip anywhere else in the Notes tab. Capacitor's own
    // #noteReaderRef has no click handler at all, so making the ref line
    // tappable here is a deliberate deviation kept from the pre-Capacitor
    // version rather than a straight port.
    onOpenVerseMention: ((book: String, chapter: Int, verse: Int) -> Unit)? = null,
    // Defaults to filling the screen — this is pushed inside an
    // AnimatedVisibility in MainActivity, which (unlike Scaffold on its
    // own) doesn't impose full-screen sizing on its content.
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val refFallback = if (noteItem.noteDate.isNotBlank()) noteItem.noteDate else "Note"

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Header ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(onClick = onBack)
                )
                Text(
                    text = noteItem.title.ifBlank { "Untitled note" }.uppercase(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                if (onEdit != null) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable(onClick = onEdit)
                    )
                } else {
                    // Keeps the title centered even without an Edit action —
                    // matches the left icon's width so the Row's weight(1f)
                    // title stays balanced.
                    Spacer(Modifier.width(24.dp))
                }
            }
            HorizontalDividerLine()

            // ── Body ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)
            ) {
                if (noteItem.refs.isNotEmpty()) {
                    // Boxed pills — matches the same ref-chip style used
                    // in the Notes list and Note editor (gold outlined
                    // rounded rectangle), rather than a comma-joined
                    // underlined line.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        noteItem.refs.forEach { ref ->
                            Text(
                                text = "${ref.book} ${ref.chapter}:${ref.verse}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .then(
                                        if (onOpenVerseMention != null) {
                                            Modifier.clickable {
                                                onOpenVerseMention(ref.book, ref.chapter, ref.verse)
                                            }
                                        } else Modifier
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = refFallback.uppercase(),
                        fontSize = 12.5.sp,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                LinkifiedNoteText(
                    text = noteItem.text,
                    onMentionClick = { b, c, v -> onOpenVerseMention?.invoke(b, c, v) },
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        lineHeight = 29.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
private fun HorizontalDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}
