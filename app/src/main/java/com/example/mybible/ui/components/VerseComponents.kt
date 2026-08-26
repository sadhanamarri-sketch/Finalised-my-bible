package com.example.mybible.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.example.mybible.model.GreekWord
import com.example.mybible.model.HebrewWord
import com.example.mybible.model.HighlightColorDef
import com.example.mybible.model.NoteItem
import com.example.mybible.model.Verse
import androidx.compose.foundation.BorderStroke
import com.example.mybible.ui.theme.*

// Default line-height ratio, matching Capacitor's fixed 1.85 — now the
// starting point for the independently user-adjustable English/Telugu
// line-spacing sliders in Settings (englishLineHeightMultiplier /
// teluguLineHeightMultiplier above), not a hard constant like it was there.
private const val FIXED_LINE_HEIGHT_RATIO = 1.85f

/**
 * The English font picker in Settings offers 8 named options (Lora, EB
 * Garamond, Merriweather, Playfair Display, Georgia, System Sans,
 * Monospace, Cursive). The 5 serif ones now resolve to their real
 * downloadable typefaces (see ui/theme/AppFonts.kt) instead of all
 * collapsing onto the single built-in FontFamily.Serif, which previously
 * made picking a different "font" look like it did nothing.
 */
private data class EnglishFontStyle(
    val family: FontFamily,
    val weight: FontWeight = FontWeight.Normal,
    val style: FontStyle = FontStyle.Normal,
    val letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp
)

private fun resolveEnglishFontStyle(englishFontFamilyName: String): EnglishFontStyle =
    when (englishFontFamilyName.lowercase()) {
        "sans", "sans-serif", "system" -> EnglishFontStyle(FontFamily.SansSerif)
        "monospace", "mono" -> EnglishFontStyle(FontFamily.Monospace)
        "cursive" -> EnglishFontStyle(FontFamily.Cursive)
        "lora" -> EnglishFontStyle(LoraFontFamily)
        "garamond" -> EnglishFontStyle(EbGaramondFontFamily)
        "merriweather" -> EnglishFontStyle(MerriweatherFontFamily)
        "playfair" -> EnglishFontStyle(PlayfairDisplayFontFamily, weight = FontWeight.Bold)
        else -> EnglishFontStyle(GelasioFontFamily) // "georgia" (default), legacy "Serif", and anything unrecognized
    }

private val DarkRedLetter = Color(0xFFFF6B6B)
private val PaperRedLetter = Color(0xFFB71C1C)

// Capacitor's `--gold` CSS variable, used for the has-xref dagger marker
// (and note/highlight accents there). Light/dark values ported verbatim.
private val PaperGold = Color(0xFFA9852F)
private val DarkGold = Color(0xFFD2A94F)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VerseCard(
    verse: Verse,
    isSelected: Boolean,
    isCompleted: Boolean,
    highlightColorHex: String?,
    redLetterEnabled: Boolean,
    showInterlinear: Boolean,
    fontSizeSp: Int,
    hasNotes: Boolean,
    hasXref: Boolean = false,
    onVerseClick: () -> Unit,
    onVerseLongClick: () -> Unit,
    onGreekWordClick: (GreekWord) -> Unit,
    onHebrewWordClick: (HebrewWord) -> Unit = {},
    onCrossReferenceMarkerClick: (() -> Unit)? = null,
    isBlurModeEnabled: Boolean = false,
    isFocusedVerse: Boolean = false,
    englishFontFamilyName: String = "Serif",
    teluguFontSizeSp: Int = 16,
    greekFontSizeSp: Int = 15,
    hebrewFontSizeSp: Int = 15,
    englishLineHeightMultiplier: Float = FIXED_LINE_HEIGHT_RATIO,
    teluguLineHeightMultiplier: Float = FIXED_LINE_HEIGHT_RATIO,
    verseSpacingDp: Int = 14,
    onEnglishWordClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Highlight colors are user-editable now (see model/HighlightColors.kt),
    // not a fixed 5, so this parses whatever hex is actually stored rather
    // than pattern-matching a hardcoded set — a pattern-match would silently
    // stop rendering any color added or recolored since the original 5.
    //
    // Rendered as a slim left-edge accent bar rather than a translucent
    // full-row fill — a full-row wash across every highlighted verse in a
    // chapter made the page read as "so much color" with no easy way to
    // tell which color was which without staring at it. The bar keeps the
    // reading text itself on the plain background (fully legible in every
    // theme) while still being unmistakable at a glance, and it's a
    // full-opacity color rather than translucent since it's a thin accent,
    // not a wash over text. (Also tinting the verse number was tried and
    // dropped as redundant once the bar was in place.)
    val highlightColor = highlightColorHex?.let { parseHexColorOrNull(it) }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val redLetterColor = if (isDark) DarkRedLetter else PaperRedLetter
    val textColor = MaterialTheme.colorScheme.onBackground
    val isDimmedByBlur = isBlurModeEnabled && !isFocusedVerse && !isSelected

    // Eased rather than instant — the focused verse can jump by more than
    // one position in a single frame right near the top of a chapter (the
    // centerItemIndex refPoint snaps from the viewport's top edge to true
    // center the moment you scroll past the "atTop" threshold, see
    // ReaderScreen), which used to read as an abrupt pop from sharp to
    // dimmed. Animating alpha/blur here smooths that regardless of how
    // many verses the focus target jumps by.
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isDimmedByBlur) 0.45f else 1.0f,
        animationSpec = tween(durationMillis = 350),
        label = "verseBlurAlpha"
    )
    val animatedBlurRadius by animateDpAsState(
        targetValue = if (isDimmedByBlur) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "verseBlurRadius"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verseSpacingDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .graphicsLayer {
                alpha = animatedAlpha
            }
            .then(
                // Real blur, matching Capacitor's `filter: blur(4px)` — not
                // just an opacity fade. RenderEffect-backed blur needs API
                // 31+; below that it silently no-ops and the opacity fade
                // above still does the (weaker) job on its own. Always
                // applied (rather than added/removed by isDimmedByBlur)
                // now that the radius itself animates 0dp -> 4dp, so the
                // blur eases in/out along with the alpha instead of
                // popping in at full strength.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Modifier.blur(animatedBlurRadius)
                } else {
                    Modifier
                }
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onVerseClick,
                onLongClick = onVerseLongClick
            )
            .testTag("verse_item_${verse.number}")
    ) {
        // A Box, not a Row+IntrinsicSize.Min (what this used to be) — that
        // combination measures the content Column's required height via
        // *intrinsic* estimation rather than its real layout pass, and for
        // nested weight()ed wrapping content (the Greek/Hebrew interlinear
        // FlowRows below) that estimate could come in short, silently
        // clipping the last wrapped line — this was the actual cause of
        // "the last Greek word or two is missing," a rendering bug, not a
        // data/import problem. A Box's matchParentSize() children (see the
        // highlight bar below) are measured only after the box's regular
        // children resolve their real size, so this always gets the
        // content Column's true rendered height instead.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 2.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Verse number
                Row(
                    modifier = Modifier
                        .width(32.dp)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${verse.number}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocusedVerse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    // Cross-reference marker (Capacitor's `.verse.has-xref
                    // .vnum::after` dagger, '\u2020', gold, superscript-ish).
                    // A separate small clickable next to the number rather
                    // than inline in the verse text — same pattern as the
                    // Greek word chips below — so it doesn't fall through to
                    // the row's combinedClickable (select/long-press) or
                    // require threading it through the annotated verse text.
                    if (hasXref && onCrossReferenceMarkerClick != null) {
                        Text(
                            text = "\u2020",
                            fontSize = 11.5.sp,
                            color = if (isDark) DarkGold else PaperGold,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .clickable(onClick = onCrossReferenceMarkerClick)
                                .testTag("xref_marker_${verse.number}")
                        )
                    }
                    // Note marker — a small dot next to the verse number,
                    // matching Capacitor's inline note indicator, rather
                    // than a separate icon off at the far end of the row.
                    if (hasNotes) {
                        Text(
                            text = "\u2022",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .testTag("note_marker_${verse.number}")
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    val englishStyle = resolveEnglishFontStyle(englishFontFamilyName)

                    // English text with Red Letter formatting & word dictionary lookup
                    val verseTextAnnotated = buildAnnotatedString {
                        val wordRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*|[^A-Za-z]+", RegexOption.IGNORE_CASE)
                        wordRegex.findAll(verse.text).forEach { result ->
                            val token = result.value
                            if (token.any { it.isLetter() }) {
                                pushStringAnnotation(tag = "WORD", annotation = token)
                                withStyle(
                                    style = SpanStyle(
                                        color = if (redLetterEnabled && verse.isRedLetter) redLetterColor else textColor,
                                        // Focus is now conveyed purely by
                                        // sharpness/opacity (blur-dim on the
                                        // other verses), matching the
                                        // Capacitor app — the focused verse's
                                        // own weight is never touched, which
                                        // also avoids synthetic-bold artifacts
                                        // when a custom font has no real
                                        // SemiBold cut.
                                        fontWeight = englishStyle.weight
                                    )
                                ) {
                                    append(token)
                                }
                                pop()
                            } else {
                                withStyle(
                                    style = SpanStyle(
                                        color = if (redLetterEnabled && verse.isRedLetter) redLetterColor else textColor
                                    )
                                ) {
                                    append(token)
                                }
                            }
                        }
                    }

                    ClickableText(
                        text = verseTextAnnotated,
                        style = TextStyle(
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * englishLineHeightMultiplier).sp,
                            fontFamily = englishStyle.family,
                            fontWeight = englishStyle.weight,
                            fontStyle = englishStyle.style,
                            letterSpacing = englishStyle.letterSpacing
                        ),
                        onClick = { offset ->
                            val annotations = verseTextAnnotated.getStringAnnotations(tag = "WORD", start = offset, end = offset)
                            if (annotations.isNotEmpty() && onEnglishWordClick != null) {
                                onEnglishWordClick(annotations.first().item)
                            } else {
                                onVerseClick()
                            }
                        }
                    )

                    // Optional inline Telugu translation. Bug fix: line-height
                    // was 1.4x (cramped) vs Capacitor's .vtelugu{line-height:1.85}
                    // — same ratio as the English text, not a smaller one — and
                    // the gap above it was 6dp vs Capacitor's 8px margin-top.
                    // Font family intentionally left unset: Android's script-based
                    // font fallback already substitutes a Telugu-capable system
                    // font (matching what Capacitor's explicit 'Noto Sans Telugu'
                    // stack achieves in the browser) since the Latin reading font
                    // has no Telugu glyphs to begin with.
                    if (!verse.teluguText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = verse.teluguText,
                            fontSize = teluguFontSizeSp.sp,
                            lineHeight = (teluguFontSizeSp * teluguLineHeightMultiplier).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Interlinear Greek words if enabled
                    if (showInterlinear && !verse.greekWords.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            verse.greekWords.forEach { gWord ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .clickable { onGreekWordClick(gWord) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = gWord.greek,
                                        fontSize = greekFontSizeSp.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Text(
                                        text = gWord.transliteration,
                                        fontSize = (greekFontSizeSp - 1).sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = gWord.englishGloss,
                                        fontSize = (greekFontSizeSp - 1).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }

                    // Interlinear Hebrew words if enabled — same toggle/data
                    // shape as Greek above, just for OT verses (verse.greekWords
                    // and verse.hebrewWords are never both non-null for the
                    // same verse, since a verse belongs to exactly one
                    // testament). Wrapped in a right-to-left layout scope so
                    // the row of word chips reads in natural Hebrew order
                    // (rightmost chip = first word of the verse) instead of
                    // the LTR order the rest of the screen uses.
                    if (showInterlinear && !verse.hebrewWords.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                verse.hebrewWords.forEach { hWord ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .clickable { onHebrewWordClick(hWord) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = hWord.hebrew,
                                            fontSize = hebrewFontSizeSp.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = FontFamily.Serif
                                        )
                                        Text(
                                            text = hWord.transliteration,
                                            fontSize = (hebrewFontSizeSp - 1).sp,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = hWord.englishGloss,
                                            fontSize = (hebrewFontSizeSp - 1).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Note: the "Studied" checkmark badge that used to sit here
                // was removed — being marked studied is already visible via
                // the toolbar's toggle state when the verse is selected, and
                // stamping every studied verse with a checkmark just added
                // visual noise to a chapter you already know you've read.
            }
            }
            // Sibling of the content Column above (not nested inside it) —
            // so this renders as a bar on the *right edge* of the whole
            // card, not a sliver stacked underneath the verse text.
            // matchParentSize() (see this Box's own doc above) gives it the
            // content Column's true rendered height, wrapped interlinear
            // lines included.
            //
            // Proportional and top-anchored rather than full-height: spans
            // 5%-30% of the verse's own measured height (English text plus
            // Telugu/Greek-Hebrew interlinear when those are on), so a
            // one-line verse gets a short mark and a verse with every
            // script enabled gets a taller one, but neither ever reads as a
            // full-height stripe running the whole card. Anchored near the
            // top (a small 5% gap, not flush) rather than centered on the
            // midpoint since the English text — what's actually being
            // read — always starts at the top; the midpoint drifts further
            // down the longer the optional scripts underneath it get.
            // The three weights (0.05/0.25/0.70) carve exact proportional
            // segments out of whatever height matchParentSize() resolves
            // to, same technique as any weighted Column, just used for
            // spacing rather than visible content.
            if (highlightColor != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(2.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top offset stays proportional (10% of the verse's
                            // own height) so the bar still starts near the
                            // English text regardless of how tall the verse
                            // is — but the bar itself is a fixed 20dp rather
                            // than scaling with verse height, so the remaining
                            // space just absorbs whatever's left.
                            Spacer(modifier = Modifier.weight(0.10f))
                            Box(
                                modifier = Modifier
                                    .height(20.dp)
                                    .fillMaxWidth()
                                    .background(highlightColor)
                            )
                            Spacer(modifier = Modifier.weight(0.90f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseActionToolbar(
    verse: Verse,
    highlightColorDefs: List<HighlightColorDef>,
    currentHighlightColorHex: String?,
    onSetHighlight: (String) -> Unit,
    onAddNote: () -> Unit,
    // Tapping the reference pill itself toggles the Greek/Hebrew
    // interlinear view — Studied and Cross Ref dropped out of the toolbar
    // entirely (both already reachable from the Reader directly: a
    // long-press starts study-picking, and the verse's own cross-reference
    // marker jumps straight there), which left Greek as the only action
    // still needing a home once the bottom action row was removed.
    onToggleInterlinear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // Notes already attached to this exact verse — one preview row per
    // note (not a collapsed "N notes" summary), each opening straight into
    // that specific note.
    existingNotes: List<NoteItem> = emptyList(),
    onOpenNote: ((NoteItem) -> Unit)? = null,
    // Inline "why did I mark this" comment offered right after a highlight
    // is applied — see MainViewModel.addHighlightQuickNote. Null when the
    // caller doesn't wire this up, so the row simply doesn't show.
    onAddQuickNote: ((String) -> Unit)? = null,
    // Whether the *current highlight itself* already has a linked quick
    // note (HighlightItem.noteId != null) — deliberately separate from
    // existingNotes.isEmpty(): a verse can already carry an older, unrelated
    // note (added before this verse was ever highlighted), and that must
    // not hide the quick-note row for a highlight that has no note of its
    // own yet.
    highlightHasLinkedNote: Boolean = false,
    // Long-press a swatch to rename it — see RenameHighlightColorDialog.
    // Null hides nothing (there's no separate affordance to hide), it just
    // makes long-press a no-op.
    onRenameColor: ((HighlightColorDef) -> Unit)? = null
) {
    // A real modal bottom sheet (dimmed scrim, swipe-down-to-dismiss, drag
    // handle) rather than a floating Surface pinned above the nav bar —
    // matches the sheet pattern already used for Notes' filter sheet and
    // the Tag editor, instead of being the one action panel in Reader that
    // looked like a toolbar wedged into the layout.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // The quick-note field below can hold focus (and the keyboard open)
    // when this sheet closes — swipe-to-dismiss, tapping the scrim, or
    // system back all skip the field's own IME "done" handling entirely.
    // Left as-is, the keyboard stayed up over whatever Reader showed
    // underneath once the sheet was gone. Explicit here rather than
    // relying on focus-follows-composition, since a composable being torn
    // down doesn't reliably hide the IME on its own.
    val dismiss = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val currentColorDef = highlightColorDefs.find { it.colorHex == currentHighlightColorHex }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gold outlined pill — matches the reference-chip style
                    // already established in the Notes list/editor/reader
                    // (see NoteReaderScreen's ref line). Also doubles as the
                    // Greek/Hebrew interlinear toggle (see onToggleInterlinear
                    // above) — no separate button needed for it.
                    Text(
                        text = "${verse.book} ${verse.chapter}:${verse.number}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleInterlinear)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )

                    // Filled label pill for the current highlight color —
                    // replaces the old separate "Highlighted — X" banner
                    // row; the swatch row below still lets you change it.
                    if (currentColorDef != null) {
                        val swatchTint = parseHexColorOrNull(currentColorDef.colorHex) ?: Color.Gray
                        Text(
                            text = currentColorDef.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(swatchTint)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                IconButton(onClick = dismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Existing-note preview — one plain row per note (not a
            // collapsed "N notes" summary), each showing that note's own
            // first line and opening straight into it in the note reader.
            // Rows are separated by a hairline divider rather than a tinted
            // card background, so the list reads as one continuous strip
            // instead of a stack of separate colored chips.
            if (existingNotes.isNotEmpty()) {
                Column {
                    existingNotes.forEachIndexed { index, note ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (onOpenNote != null) Modifier.clickable { onOpenNote(note) } else Modifier)
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StickyNote2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = notePreviewLine(note),
                                fontSize = 13.sp,
                                fontFamily = literataOrTestFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Highlight Colors — fixed palette (see model/HighlightColors.kt),
            // no add/manage flow. Horizontally scrollable since 12 swatches
            // plus Clear doesn't comfortably fit most screens at once.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (onRenameColor != null) "Highlight: (long-press a color to rename)" else "Highlight:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Clear swatch — always first, matches removing a
                    // highlight via onSetHighlight("") elsewhere.
                    HighlightSwatchItem(
                        label = "Clear",
                        content = {
                            Icon(
                                Icons.Default.FormatColorReset,
                                contentDescription = "Clear Highlight",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        swatchColor = MaterialTheme.colorScheme.background,
                        isActive = currentHighlightColorHex.isNullOrEmpty(),
                        onClick = { onSetHighlight("") }
                    )

                    highlightColorDefs.forEach { def ->
                        val isActive = currentHighlightColorHex == def.colorHex
                        HighlightSwatchItem(
                            label = def.label,
                            swatchColor = parseHexColorOrNull(def.colorHex) ?: Color.Gray,
                            isActive = isActive,
                            onClick = { onSetHighlight(if (isActive) "" else def.colorHex) },
                            onLongClick = onRenameColor?.let { rename -> { rename(def) } }
                        )
                    }
                }
            }

            // Optional inline "quick note" — offered right after a
            // highlight is applied, so a one-line "why did I mark this"
            // (doubt / prayer / learning) can be jotted down without
            // leaving the toolbar for the full Note Editor. Only shown
            // once a color is active and that highlight has no note yet.
            if (onAddQuickNote != null && !currentHighlightColorHex.isNullOrEmpty() && !highlightHasLinkedNote) {
                var quickNoteText by remember(verse.book, verse.chapter, verse.number, currentHighlightColorHex) {
                    mutableStateOf("")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quickNoteText,
                        onValueChange = { quickNoteText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a quick note… (e.g. doubt, prayer)", fontSize = 12.sp) },
                        textStyle = TextStyle(fontSize = 13.sp),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            val trimmed = quickNoteText.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddQuickNote(trimmed)
                                quickNoteText = ""
                            }
                        },
                        enabled = quickNoteText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Save quick note",
                            tint = if (quickNoteText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Always available — full editor, not the inline quick-note
            // field above. Hollow/outlined rather than filled so it doesn't
            // compete with the highlight swatches for attention. Kept as
            // the sheet's last element deliberately: it's a single
            // full-width button rather than a horizontal-scroll drag
            // target, so landing at the bottom edge (inside the strip
            // Android reserves for the gesture-nav home swipe) doesn't
            // create the same drag-vs-system-gesture conflict the
            // highlight-color row had when it used to end up last.
            OutlinedButton(
                onClick = onAddNote,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "+ Add New Note",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** First non-blank line of a note's body, trimmed and truncated — what
 *  each row in the note-preview section shows instead of the full text. */
private fun notePreviewLine(note: NoteItem, maxChars: Int = 90): String {
    val firstLine = note.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isEmpty()) return "(empty note)"
    return if (firstLine.length > maxChars) firstLine.take(maxChars - 1) + "…" else firstLine
}

/** One labeled swatch in the highlight-color row: circle + short label
 *  underneath, checkmark overlay when active — matches Capacitor's
 *  `.hl-swatch-item` (dot + `.hl-label`, `.active` shows a check). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HighlightSwatchItem(
    label: String,
    swatchColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlineOnly: Boolean = false,
    content: (@Composable () -> Unit)? = null,
    // Long-press to rename — see RenameHighlightColorDialog. Null on the
    // Clear swatch, which isn't a real color and has nothing to rename.
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (outlineOnly) Color.Transparent else swatchColor)
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = if (outlineOnly) 0.6f else 1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (content != null) {
                content()
            } else if (isActive) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (swatchColor.luminance() < 0.5f) Color.White else Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Parses a "#RRGGBB" hex string (as stored in HighlightColorDef/HighlightItem)
 *  into a Compose Color, or null if it's malformed — callers fall back to a
 *  neutral gray rather than crashing on a corrupted/hand-edited pref value. */
private fun parseHexColorOrNull(hex: String): Color? {
    return try {
        val cleaned = hex.removePrefix("#")
        if (cleaned.length != 6) return null
        Color(android.graphics.Color.parseColor("#$cleaned"))
    } catch (e: Exception) {
        null
    }
}

/**
 * Renders note body text with Bible-reference mentions (e.g. "John 3:16")
 * turned into tappable links, matching Capacitor's linkifyXrefs() applied
 * in the notes list card and the note reader. [onMentionClick] receives the
 * resolved book/chapter/verse; the caller opens the verse-preview sheet
 * (see ui/components/DialogComponents.kt's VerseMentionPreviewSheet) rather
 * than navigating straight to the Reader — same two-step behavior as
 * Capacitor's openVerseTextSheet.
 */
@Composable
fun LinkifiedNoteText(
    text: String,
    onMentionClick: (book: String, chapter: Int, verse: Int) -> Unit,
    modifier: Modifier = Modifier,
    // TextStyle.Default has an unspecified color, and ClickableText (unlike
    // Material3's Text) does not fall back to LocalContentColor for that —
    // it renders literal black regardless of theme. Defaulting to
    // onSurface here means a call site that forgets to set a color still
    // gets a legible, theme-correct result instead of invisible text on a
    // dark background.
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip
) {
    val annotated = remember(text) { com.example.mybible.data.buildLinkifiedNoteText(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    val resolvedStyle = if (style.color.isSpecified) style
        else style.copy(color = MaterialTheme.colorScheme.onSurface)
    val styledAnnotated = remember(annotated, linkColor) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(annotated)
            annotated.getStringAnnotations(com.example.mybible.data.VERSE_MENTION_TAG, 0, annotated.length)
                .forEach { addStyle(SpanStyle(color = linkColor), it.start, it.end) }
        }
    }
    ClickableText(
        text = styledAnnotated,
        style = resolvedStyle,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            val hit = styledAnnotated.getStringAnnotations(
                com.example.mybible.data.VERSE_MENTION_TAG, offset, offset
            ).firstOrNull()
            if (hit != null) {
                val parts = hit.item.split("|")
                if (parts.size == 3) {
                    val chapter = parts[1].toIntOrNull()
                    val verse = parts[2].toIntOrNull()
                    if (chapter != null && verse != null) {
                        onMentionClick(parts[0], chapter, verse)
                    }
                }
            }
        }
    )
}
