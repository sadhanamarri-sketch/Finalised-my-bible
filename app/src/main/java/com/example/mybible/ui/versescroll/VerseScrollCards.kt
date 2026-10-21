package com.example.mybible.ui.versescroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.theme.FrauncesFontFamily
import com.example.mybible.ui.theme.GelasioFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.versescroll.LONG_VERSE_WORDS
import com.example.mybible.versescroll.LinkedFrom
import com.example.mybible.versescroll.SceneSpec
import com.example.mybible.versescroll.VerseFeedCard
import com.example.mybible.versescroll.VerseLine
import com.example.mybible.versescroll.VerseRef
import kotlin.math.roundToInt

// Where on the card the text may sit: clear of the top bar and of the bottom bar and its counter.
private val CardTopPadding = 74.dp
private val InThreadTopPadding = 112.dp
private val CardBottomPadding = 122.dp

// One size for every verse, whatever its length. A verse with more than the card has room for is cut
// short with "…" (Read shows the rest).
private const val VERSE_SIZE = 26f
private const val VERSE_LINE_HEIGHT = 1.4f

// The Telugu under a verse stops after this many lines, with "…".
private const val TELUGU_LINES = 6

// The space between the parts of the card (eyebrow, verse, reference, note), and between a passage's verses.
private val STACK_GAP = 14.dp
private val VERSE_GAP = (VERSE_SIZE * 0.35f).dp

/** The part of the Bible a book belongs to, shown above each verse. */
internal fun genreOf(book: String): String {
    val i = BIBLE_BOOKS.indexOf(book)
    return when {
        i < 0 -> ""
        i <= 4 -> "The Law"
        i <= 16 -> "History"
        i <= 21 -> "Wisdom & Poetry"
        i <= 26 -> "Major Prophets"
        i <= 38 -> "Minor Prophets"
        i <= 42 -> "The Gospels"
        i == 43 -> "The Early Church"
        i <= 56 -> "Paul's Letters"
        i <= 64 -> "General Letters"
        else -> "Revelation"
    }
}

/** A highlight color on a card: [verses], the numbers of those it's on, when it isn't on all of them. */
internal data class CardColor(val hex: String, val verses: List<Int>?)

/**
 * The highlight colors of a card's verses ([hexes], one list per verse, numbered [verses]), for the
 * chips beside its reference: each color once, in the order they come, verse by verse.
 */
internal fun cardColors(verses: List<Int>, hexes: List<List<String>>): List<CardColor> {
    val on = LinkedHashMap<String, Pair<String, MutableSet<Int>>>()
    hexes.forEachIndexed { i, colors ->
        for (hex in colors) on.getOrPut(hex.lowercase()) { hex to LinkedHashSet() }.second += verses[i]
    }
    return on.values.map { (hex, those) -> CardColor(hex, those.toList().takeIf { it.size < verses.size }) }
}

internal fun hexToColor(hex: String): Color =
    Color(0xFF000000 or (hex.removePrefix("#").toLongOrNull(16) ?: 0xCCCCCC))

// Easing for the double-tap burst, as tuned in the design preview.
private val BurstEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private data class Burst(val id: Long, val at: Offset, val color: Color)

/** A color just picked for a card from the color box its double-tap opened: [id] new each time. */
internal data class CardBurst(val id: Long, val hex: String)

/**
 * One verse card: the background photo (or the plain theme background), and the verse, at the same size
 * as every other (cut short with "…" if it runs out of room). Double-tap opens the color box, long-press
 * the full verse sheet.
 *
 * @param teluguSize the Telugu under the verse's size, in sp (when [showTelugu]).
 * @param words for a search's verses, what found this one ("loveth", "charity", "Greek agapē").
 * @param highlightHexes a verse's highlight colors (up to three), in the order they were put on.
 * @param onDoubleTap opens the color box for the card.
 * @param burst a color picked in the box a double-tap opened: a ring pops in it where the finger was.
 */
@Composable
internal fun VerseCardPage(
    card: VerseFeedCard,
    scene: SceneSpec?,
    image: ImageBitmap?,
    colors: VsColors,
    motion: Boolean,
    reduceMotion: Boolean,
    isCurrent: Boolean,
    showTelugu: Boolean,
    teluguSize: Int,
    words: List<String>,
    highlightHexes: (VerseRef) -> List<String>,
    colorLabel: (String) -> String,
    notePreview: String?,
    onPress: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onLinkedFrom: () -> Unit = {},
    burst: CardBurst? = null
) {
    val content = card.content
    val long = content.wordCount > LONG_VERSE_WORDS
    val onScene = scene != null
    val c = if (onScene) colors.onScene() else colors
    val haptics = LocalHapticFeedback.current
    val bursts = remember { mutableStateListOf<Burst>() }
    // Where the card was last double-tapped, for the ring once a color's picked.
    var tappedAt by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(burst) {
        val at = tappedAt
        if (burst != null && at != null && !reduceMotion) bursts += Burst(burst.id, at, hexToColor(burst.hex))
    }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        if (scene != null) {
            SceneLayer(
                scene = scene,
                cardKey = content.ref.key,
                image = image,
                dark = colors.dark,
                motion = motion,
                running = isCurrent
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(card.uid) {
                    detectTapGestures(
                        onPress = { currentOnPress() },
                        onDoubleTap = { at ->
                            tappedAt = at
                            currentOnDoubleTap()
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnLongPress()
                        }
                    )
                }
                // Cards inside a Rabbit hole leave room for the "Back to …" button under the top bar.
                .padding(cardPadding(top = if (card.from != null) InThreadTopPadding else CardTopPadding))
        ) {
            VerseStack(
                eyebrow = {
                    when {
                        card.from != null -> LinkedFromEyebrow(card.from, c, onLinkedFrom)
                        card.newThread -> IconEyebrow(VerseScrollIcons.Spark, "New thread", c)
                        else -> Eyebrow(genreOf(content.ref.book), c)
                    }
                },
                lines = content.lines,
                label = content.label,
                refs = content.refs,
                long = long,
                colors = c,
                onScene = onScene,
                showTelugu = showTelugu,
                teluguSize = teluguSize,
                words = words,
                highlightHexes = highlightHexes,
                colorLabel = colorLabel,
                notePreview = notePreview
            )
        }
        bursts.forEach { burst ->
            key(burst.id) { BurstRing(burst) { bursts.remove(burst) } }
        }
    }
}

@Composable
private fun cardPadding(top: Dp = CardTopPadding): PaddingValues {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 24.dp, end = 24.dp, top = top + statusBar, bottom = CardBottomPadding + bottom)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerseStack(
    eyebrow: @Composable () -> Unit,
    lines: List<VerseLine>,
    label: String,
    refs: List<VerseRef>,
    long: Boolean,
    colors: VsColors,
    onScene: Boolean,
    showTelugu: Boolean,
    teluguSize: Int,
    words: List<String>,
    highlightHexes: (VerseRef) -> List<String>,
    colorLabel: (String) -> String,
    notePreview: String?
) {
    val telugu = if (showTelugu) lines.mapNotNull { it.telugu }.joinToString(" ") else ""
    val style = verseStyle(colors, onScene)
    // The chips next to the reference say what found the verse, for a search's, outlined, and how the
    // card's verses are highlighted, in their colors: the verse's text itself is left plain.
    val chips = cardColors(refs.map { it.verse }, refs.map(highlightHexes))

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Layout(
            contents = listOf(
                eyebrow,
                {
                    VerseLines(
                        count = lines.size,
                        text = { i, words -> verseString(lines[i], showNumber = lines.size > 1, colors.soft, words) },
                        words = { i -> wordsOf(lines[i].text).size },
                        style = style
                    ) { text ->
                        Text(text = text, style = style)
                    }
                },
                {
                    Column(verticalArrangement = Arrangement.spacedBy(STACK_GAP)) {
                        if (telugu.isNotEmpty()) {
                            Text(
                                text = telugu,
                                color = colors.soft,
                                fontSize = teluguSize.sp,
                                lineHeight = 1.65.em,
                                maxLines = TELUGU_LINES,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = label.uppercase(),
                                color = colors.gold,
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                letterSpacing = 0.12.em,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(end = 5.dp)
                            )
                            for (word in words) {
                                Text(
                                    text = word.uppercase(),
                                    color = colors.gold,
                                    fontFamily = WorkSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.1.em,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .border(1.dp, colors.gold.copy(alpha = 0.7f), CircleShape)
                                        .padding(start = 9.dp, end = 9.dp, top = 4.dp, bottom = 3.dp)
                                )
                            }
                            for (chip in chips) {
                                Text(
                                    text = colorLabel(chip.hex).uppercase() + chip.verses?.joinToString(", ", prefix = " \u00b7 ").orEmpty(),
                                    color = Color(0xFF2C221E),
                                    fontFamily = WorkSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.1.em,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .clip(CircleShape)
                                        .background(hexToColor(chip.hex))
                                        .padding(start = 9.dp, end = 9.dp, top = 5.dp, bottom = 4.dp)
                                )
                            }
                        }
                        if (notePreview != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(
                                    VerseScrollIcons.Note,
                                    contentDescription = "Note",
                                    tint = colors.soft,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(15.dp)
                                )
                                Text(
                                    text = notePreview,
                                    color = colors.soft,
                                    fontFamily = GelasioFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 14.sp,
                                    lineHeight = 1.4.em
                                )
                            }
                        }
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (long && onScene) Modifier.readingPanel(colors.dark) else Modifier)
        ) { (top, verses, below), constraints ->
            // The eyebrow and what's under the verse keep their size; the verse gets the room that's left.
            val loose = constraints.copy(minHeight = 0)
            val gap = STACK_GAP.roundToPx()
            val topParts = top.map { it.measure(loose) }
            val belowParts = below.map { it.measure(loose) }
            val fixed = topParts.sumOf { it.height } + belowParts.sumOf { it.height } + 2 * gap
            val verseRoom = (constraints.maxHeight - fixed).coerceAtLeast(0)
            val verseParts = verses.map { it.measure(loose.copy(maxHeight = verseRoom)) }
            val height = fixed + verseParts.sumOf { it.height }
            layout(constraints.maxWidth, height) {
                var y = 0
                for (part in topParts) { part.place(0, y); y += part.height }
                y += gap
                for (part in verseParts) { part.place(0, y); y += part.height }
                y += gap
                for (part in belowParts) { part.place(0, y); y += part.height }
            }
        }
    }
}

/**
 * A card's verses, one under another, in the room the card has for them. A verse that runs out of room
 * stops at its last whole word that fits, with "…"; a verse with no room for even a line is left out,
 * and the one before it then gives up its last words so that it ends in "…". Read shows the rest.
 *
 * @param text a verse's text: whole (words null), or its first so-many words and "…".
 */
@Composable
private fun VerseLines(
    count: Int,
    text: (index: Int, words: Int?) -> AnnotatedString,
    words: (index: Int) -> Int,
    style: TextStyle,
    verse: @Composable (text: AnnotatedString) -> Unit
) {
    val measurer = rememberTextMeasurer()
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val gap = VERSE_GAP.roundToPx()
        val oneLine = (VERSE_SIZE * VERSE_LINE_HEIGHT).sp.roundToPx()
        fun heightOf(t: AnnotatedString) = measurer.measure(t, style, constraints = Constraints(maxWidth = width)).size.height
        // The most words of verse i (and "…") that fit in room; at least one.
        fun cut(i: Int, room: Int): AnnotatedString {
            var lo = 1
            var hi = words(i) - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (heightOf(text(i, mid)) <= room) lo = mid else hi = mid - 1
            }
            return text(i, lo)
        }

        val shown = mutableListOf<AnnotatedString>()
        val heights = mutableListOf<Int>()
        var room = constraints.maxHeight
        for (i in 0 until count) {
            val before = if (i > 0) gap else 0
            val whole = text(i, null)
            val full = heightOf(whole)
            if (full <= room - before) {
                shown += whole
                heights += full
                room -= before + full
                continue
            }
            if (room - before >= oneLine) shown += cut(i, room - before)
            else if (shown.isNotEmpty()) shown[shown.lastIndex] = cut(shown.lastIndex, heights.last())
            break
        }
        val placed = shown.mapIndexed { i, t -> subcompose(i) { verse(t) }.first().measure(Constraints(maxWidth = width)) }
        val height = placed.sumOf { it.height } + gap * (placed.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            for (part in placed) {
                part.place(0, y)
                y += part.height + gap
            }
        }
    }
}

private fun wordsOf(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

/**
 * A verse as the card shows it: its number when it's one of a passage's, then its words, or only the first
 * [words] of them and "…" when the card hasn't room for the rest.
 */
private fun verseString(line: VerseLine, showNumber: Boolean, numberColor: Color, words: Int? = null): AnnotatedString =
    buildAnnotatedString {
        if (showNumber) {
            withStyle(
                SpanStyle(
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 0.5.em,
                    letterSpacing = 0.02.em,
                    baselineShift = BaselineShift(0.85f),
                    color = numberColor
                )
            ) { append("${line.number}\u2009") }
        }
        if (words == null) append(line.text)
        else append(wordsOf(line.text).take(words).joinToString(" ").trimEnd(',', ';', ':') + "\u2026")
    }

/** The type every verse is set in: one size, whatever the verse's length. */
@Composable
private fun verseStyle(colors: VsColors, onScene: Boolean): TextStyle {
    val density = LocalDensity.current
    val shadow = if (!onScene) null else with(density) {
        if (colors.dark) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.dp.toPx()), 12.dp.toPx())
        else Shadow(Color.White.copy(alpha = 0.6f), Offset(0f, 1.dp.toPx()), 10.dp.toPx())
    }
    return TextStyle(
        color = colors.ink,
        fontFamily = GelasioFontFamily,
        fontSize = VERSE_SIZE.sp,
        lineHeight = VERSE_LINE_HEIGHT.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        lineBreak = LineBreak.Paragraph,
        shadow = shadow
    )
}

@Composable
private fun Eyebrow(text: String, colors: VsColors) {
    Text(
        text = text.uppercase(),
        color = colors.gold,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 1.3.em,
        letterSpacing = 0.14.em
    )
}

@Composable
private fun IconEyebrow(icon: ImageVector, text: String, colors: VsColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = colors.gold, modifier = Modifier.size(13.dp))
        Eyebrow(text, colors)
    }
}

/** "Linked from Isaiah 41:10 · 2 of 3" on a Rabbit hole card; tapping the verse goes back to it. */
@Composable
private fun LinkedFromEyebrow(from: LinkedFrom, colors: VsColors, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(role = Role.Button, onClickLabel = "Go back to ${from.label}", onClick = onClick)
                .padding(vertical = 8.dp)
        ) {
            Icon(VerseScrollIcons.Links, contentDescription = null, tint = colors.gold, modifier = Modifier.size(13.dp))
            Text(
                text = "Linked from ${from.label}".uppercase(),
                color = colors.gold,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                lineHeight = 1.3.em,
                letterSpacing = 0.14.em,
                textDecoration = TextDecoration.Underline
            )
        }
        if (from.of > 1) {
            Text(
                text = "\u00b7 ${from.nth} of ${from.of}".uppercase(),
                color = colors.soft,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                letterSpacing = 0.14.em
            )
        }
    }
}

/**
 * A soft panel behind a long verse, with feathered edges, so tree tops, horizons and glitter don't run
 * through a dense paragraph: solid behind the text, then fading out smoothly over [FEATHER]. Drawn in
 * nine pieces, each on whole pixels so they meet without seams: the middle, four edges with straight
 * gradients, and four corners with round ones.
 */
private fun Modifier.readingPanel(dark: Boolean): Modifier = drawBehind {
    val color = if (dark) Color(0xFF0A0808) else Color(0xFFFAF7F0)
    val target = if (dark) 0.42f else 0.55f
    val spread = 30.dp.toPx()
    val sigma = 22.dp.toPx()
    val reach = FEATHER.toPx()
    val r = 26.dp.toPx().roundToInt().toFloat()
    // The panel: the text's bounds plus a margin, on whole pixels.
    val inset = 14.dp.toPx()
    val left = (-inset).roundToInt().toFloat()
    val top = (-inset).roundToInt().toFloat()
    val right = (size.width + inset).roundToInt().toFloat()
    val bottom = (size.height + inset).roundToInt().toFloat()
    val far = reach.roundToInt().toFloat()

    // Opacity at a distance outside the panel's edge: like a CSS shadow (solid for a while, then a
    // gaussian fall-off), scaled so it starts exactly at the panel's own opacity.
    val start = 1f - normalCdf(-spread / sigma)
    fun alphaAt(d: Float) = target * (1f - normalCdf((d - spread) / sigma)) / start
    val samples = 12
    fun edgeStops(): Array<Pair<Float, Color>> =
        Array(samples + 1) { i -> val f = i.toFloat() / samples; f to color.copy(alpha = alphaAt(f * far)) }
    val solid = color.copy(alpha = target)

    // The middle (a cross that leaves the four corner squares to the round pieces).
    drawRect(solid, Offset(left + r, top), Size(right - left - 2 * r, bottom - top))
    drawRect(solid, Offset(left, top + r), Size(r, bottom - top - 2 * r))
    drawRect(solid, Offset(right - r, top + r), Size(r, bottom - top - 2 * r))
    // Edges.
    val stops = edgeStops()
    drawRect(Brush.verticalGradient(*stops, startY = top, endY = top - far), Offset(left + r, top - far), Size(right - left - 2 * r, far))
    drawRect(Brush.verticalGradient(*stops, startY = bottom, endY = bottom + far), Offset(left + r, bottom), Size(right - left - 2 * r, far))
    drawRect(Brush.horizontalGradient(*stops, startX = left, endX = left - far), Offset(left - far, top + r), Size(far, bottom - top - 2 * r))
    drawRect(Brush.horizontalGradient(*stops, startX = right, endX = right + far), Offset(right, top + r), Size(far, bottom - top - 2 * r))
    // Corners: solid out to the panel's own rounded corner, then the same fall-off.
    val radius = r + far
    val cornerStops = arrayOf(0f to solid, (r / radius) to solid) +
        Array(samples) { i -> val d = (i + 1).toFloat() / samples * far; ((r + d) / radius) to color.copy(alpha = alphaAt(d)) }
    for ((cx, cy) in listOf(left + r to top + r, right - r to top + r, left + r to bottom - r, right - r to bottom - r)) {
        val x0 = if (cx < (left + right) / 2) left - far else cx
        val y0 = if (cy < (top + bottom) / 2) top - far else cy
        drawRect(
            Brush.radialGradient(*cornerStops, center = Offset(cx, cy), radius = radius),
            Offset(x0, y0),
            Size(r + far, r + far)
        )
    }
}

// How far the reading panel's edge fades out.
private val FEATHER = 74.dp

/** Standard normal CDF (Abramowitz and Stegun 7.1.26), for the panel's feathered edge. */
private fun normalCdf(z: Float): Float {
    val x = kotlin.math.abs(z) / kotlin.math.sqrt(2f)
    val t = 1f / (1f + 0.3275911f * x)
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * kotlin.math.exp(-x * x)
    return if (z >= 0) 0.5f * (1f + y) else 0.5f * (1f - y)
}

/** The ring that pops where the finger double-tapped, in the color then picked for the verse. */
@Composable
private fun BurstRing(burst: Burst, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(550, easing = BurstEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val scale = 0.25f + 1.1f * p
        val alpha = 0.95f * (1f - p)
        val radius = 48.dp.toPx() * scale
        val ring = 3.dp.toPx() * scale
        drawCircle(burst.color, radius = radius * 0.26f, center = burst.at, alpha = alpha)
        drawCircle(burst.color, radius = radius - ring / 2, center = burst.at, alpha = alpha, style = Stroke(ring))
    }
}

/**
 * A pause every few dozen verses: how many so far, and an offer to slow down and read the chapter
 * the last verse comes from. Always on the plain background, as a breather.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckInPage(
    colors: VsColors,
    versesToday: Int,
    highlighted: Int,
    lastVerse: VerseRef?,
    lastVerseLabel: String?,
    onRead: () -> Unit,
    onKeepGoing: () -> Unit,
    rabbitHoleFrom: String? = null,
    rabbitHoleDepth: Int = 0,
    // The mode the Rabbit hole was left from ("Full", "Romans 8"): the way back to it.
    backToLabel: String = "Discover",
    onBackToDiscover: () -> Unit = {}
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(cardPadding()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("Check-in", colors)
            Text(
                text = "That's $versesToday verse${if (versesToday == 1) "" else "s"}.",
                color = colors.ink,
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 31.sp,
                lineHeight = 1.15.em
            )
            val text = buildString {
                if (rabbitHoleFrom != null) {
                    append("You're $rabbitHoleDepth verse${if (rabbitHoleDepth == 1) "" else "s"} into a Rabbit hole from $rabbitHoleFrom. ")
                }
                if (highlighted > 0) append("You highlighted $highlighted along the way. ")
                append(
                    if (lastVerseLabel != null) "Want to slow down and read the chapter $lastVerseLabel comes from?"
                    else "Want to slow down for a moment?"
                )
            }
            Text(
                text = text,
                color = colors.soft,
                fontFamily = WorkSansFontFamily,
                fontSize = 15.5.sp,
                lineHeight = 1.5.em,
                modifier = Modifier.widthIn(max = 300.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (lastVerse != null) {
                    PillButton("Read ${lastVerse.book} ${lastVerse.chapter}", primary = true, colors = colors, onClick = onRead)
                }
                if (rabbitHoleFrom != null) {
                    PillButton("Back to $backToLabel", primary = false, colors = colors, onClick = onBackToDiscover)
                }
                PillButton(if (rabbitHoleFrom != null) "Keep going" else "Keep scrolling", primary = false, colors = colors, onClick = onKeepGoing)
            }
        }
    }
}

/**
 * The end of a book or chapter read in order ("That's the end of Romans 8."): start it again, or pick
 * other verses. On the plain background, like a check-in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EndPage(
    colors: VsColors,
    label: String,
    onStartAgain: () -> Unit,
    onOtherVerses: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(cardPadding()),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("The end", colors)
            Text(
                text = "That's the end of $label.",
                color = colors.ink,
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 31.sp,
                lineHeight = 1.15.em
            )
            Text(
                text = "Go through it again from the start, or choose other verses to scroll.",
                color = colors.soft,
                fontFamily = WorkSansFontFamily,
                fontSize = 15.5.sp,
                lineHeight = 1.5.em,
                modifier = Modifier.widthIn(max = 300.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                PillButton("Start again", primary = true, colors = colors, onClick = onStartAgain)
                PillButton("Other verses", primary = false, colors = colors, onClick = onOtherVerses)
            }
        }
    }
}

/** The design's rounded buttons: filled for the main action, outlined for the other. */
@Composable
internal fun PillButton(label: String, primary: Boolean, colors: VsColors, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = if (primary) colors.onPrimary else colors.ink,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (primary) Modifier.background(colors.primary)
                else Modifier.border(1.dp, colors.line, CircleShape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    )
}
