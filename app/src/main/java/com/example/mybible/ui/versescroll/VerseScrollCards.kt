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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.theme.FrauncesFontFamily
import com.example.mybible.ui.theme.GelasioFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.versescroll.LONG_VERSE_WORDS
import com.example.mybible.versescroll.SceneSpec
import com.example.mybible.versescroll.VerseFeedCard
import com.example.mybible.versescroll.VerseLine
import com.example.mybible.versescroll.VerseRef
import kotlin.math.roundToInt

// Where on the card the text may sit: clear of the top bar and of the bottom bar and its counter.
private val CardTopPadding = 74.dp
private val CardBottomPadding = 122.dp

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

/** The verse size before fitting: big for a short verse, smaller as it grows; long verses start roomier. */
internal fun baseVerseSize(words: Int, long: Boolean, telugu: Boolean): Float {
    val size = if (long) {
        when {
            words <= 55 -> 23f
            words <= 75 -> 22f
            else -> 21f
        }
    } else {
        when {
            words <= 6 -> 36f
            words <= 12 -> 31f
            words <= 20 -> 27f
            words <= 30 -> 24f
            words <= 42 -> 22f
            else -> 20f
        }
    }
    return size - if (telugu) 2f else 0f
}

/** A highlight's band behind the text: see-through on dark themes (more so over a scene), solid on light ones. */
internal fun bandColor(hex: String, dark: Boolean, onScene: Boolean): Color =
    hexToColor(hex).copy(alpha = if (dark) (if (onScene) 0.38f else 0.3f) else 0.88f)

internal fun hexToColor(hex: String): Color =
    Color(0xFF000000 or (hex.removePrefix("#").toLongOrNull(16) ?: 0xCCCCCC))

// Easing for the highlighter sweep and the double-tap burst, as tuned in the design preview.
private val SweepEasing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
private val BurstEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private data class Burst(val id: Long, val at: Offset, val color: Color)

/**
 * One verse card: the painted scene (or the plain theme background), and the verse with the verses
 * either side of it, sized to fit. Double-tap highlights, long-press opens the full verse sheet.
 *
 * @param highlightHex the highlight color of a verse, if it has one.
 * @param sweepToken goes up each time this card's highlight should sweep in (again).
 * @param onDoubleTap highlights the card; returns the color for the burst under the finger.
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
    highlightHex: (VerseRef) -> String?,
    colorLabel: (String) -> String,
    notePreview: String?,
    sweepToken: Int,
    onPress: () -> Unit,
    onDoubleTap: () -> String?,
    onLongPress: () -> Unit
) {
    val content = card.content
    val long = content.wordCount > LONG_VERSE_WORDS
    val onScene = scene != null
    val c = if (onScene) colors.onScene() else colors
    val haptics = LocalHapticFeedback.current
    val sweep = remember(card.uid) { Animatable(1f) }
    LaunchedEffect(sweepToken) {
        if (sweepToken > 0) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(650, easing = SweepEasing))
        }
    }
    val bursts = remember { mutableStateListOf<Burst>() }
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
                running = isCurrent,
                effects = !long
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(card.uid) {
                    detectTapGestures(
                        onPress = { currentOnPress() },
                        onDoubleTap = { at ->
                            val hex = currentOnDoubleTap()
                            if (hex != null && !reduceMotion) bursts += Burst(System.nanoTime(), at, hexToColor(hex))
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnLongPress()
                        }
                    )
                }
                .padding(cardPadding())
        ) {
            VerseStack(
                lines = content.lines,
                before = content.before,
                after = content.after,
                book = content.ref.book,
                label = content.label,
                refs = content.refs,
                long = long,
                colors = c,
                onScene = onScene,
                showTelugu = showTelugu,
                highlightHex = highlightHex,
                colorLabel = colorLabel,
                notePreview = notePreview,
                sweep = { sweep.value }
            )
        }
        bursts.forEach { burst ->
            key(burst.id) { BurstRing(burst) { bursts.remove(burst) } }
        }
    }
}

@Composable
private fun cardPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 24.dp, end = 24.dp, top = CardTopPadding + top, bottom = CardBottomPadding + bottom)
}

@Composable
private fun VerseStack(
    lines: List<VerseLine>,
    before: VerseLine?,
    after: VerseLine?,
    book: String,
    label: String,
    refs: List<VerseRef>,
    long: Boolean,
    colors: VsColors,
    onScene: Boolean,
    showTelugu: Boolean,
    highlightHex: (VerseRef) -> String?,
    colorLabel: (String) -> String,
    notePreview: String?,
    sweep: () -> Float
) {
    val words = lines.sumOf { line -> line.text.split(' ').count { it.isNotBlank() } }
    val telugu = if (showTelugu) lines.mapNotNull { it.telugu }.joinToString(" ") else ""
    val hexes = refs.map(highlightHex)
    // The chip next to the reference names the color once the whole card is highlighted in it.
    val allColor = hexes.firstOrNull()?.takeIf { first -> hexes.all { it == first } }

    FitToHeight(base = baseVerseSize(words, long, showTelugu), min = 18f, step = 1.5f) { size ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (long && onScene) Modifier.readingPanel(colors.dark) else Modifier)
        ) {
            Eyebrow(genreOf(book), colors)
            // A long verse keeps the card to itself: no lines from the verses around it.
            if (!long && before != null) ContextLine(before, colors, onScene)
            Column(verticalArrangement = Arrangement.spacedBy((size * 0.35f).dp)) {
                lines.forEachIndexed { i, line ->
                    VerseText(
                        line = line,
                        showNumber = lines.size > 1,
                        size = size,
                        long = long,
                        colors = colors,
                        onScene = onScene,
                        band = hexes[i]?.let { bandColor(it, colors.dark, onScene) },
                        sweep = sweep
                    )
                }
            }
            if (telugu.isNotEmpty()) {
                Text(
                    text = telugu,
                    color = colors.soft,
                    fontSize = 15.sp,
                    lineHeight = 1.65.em,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.uppercase(),
                    color = colors.gold,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.12.em
                )
                if (allColor != null) {
                    Text(
                        text = colorLabel(allColor).uppercase(),
                        color = Color(0xFF2C221E),
                        fontFamily = WorkSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.1.em,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(CircleShape)
                            .background(hexToColor(allColor))
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
            if (!long && after != null) ContextLine(after, colors, onScene)
        }
    }
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

/** The verse just before or after, small and dimmed, cut to two lines: a hint of where the verse sits. */
@Composable
private fun ContextLine(line: VerseLine, colors: VsColors, onScene: Boolean) {
    val number = SpanStyle(
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp,
        baselineShift = BaselineShift(0.5f),
        color = colors.soft
    )
    Text(
        text = buildAnnotatedString {
            withStyle(number) { append("${line.number} ") }
            append(line.text)
        },
        color = colors.soft.copy(alpha = colors.soft.alpha * (if (onScene) 1f else 0.8f)),
        fontFamily = GelasioFontFamily,
        fontSize = 13.5.sp,
        lineHeight = 1.45.em,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

// Gelasio's ascent and descent (1900 and 700 of 2048 units), plus a little padding: the height of the
// highlighter band around each line of text.
private const val BAND_ABOVE = 0.958f
private const val BAND_BELOW = 0.372f

/**
 * One verse in the card's big type. A highlight is a highlighter band behind each line, which sweeps
 * in from the left ([sweep] 0..1) when it's applied.
 */
@Composable
private fun VerseText(
    line: VerseLine,
    showNumber: Boolean,
    size: Float,
    long: Boolean,
    colors: VsColors,
    onScene: Boolean,
    band: Color?,
    sweep: () -> Float
) {
    val density = LocalDensity.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text: AnnotatedString = buildAnnotatedString {
        if (showNumber) {
            withStyle(
                SpanStyle(
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 0.5.em,
                    letterSpacing = 0.02.em,
                    baselineShift = BaselineShift(0.85f),
                    color = colors.soft
                )
            ) { append("${line.number} ") }
        }
        append(line.text)
    }
    val shadow = if (!onScene) null else with(density) {
        if (colors.dark) Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1.dp.toPx()), 12.dp.toPx())
        else Shadow(Color.White.copy(alpha = 0.6f), Offset(0f, 1.dp.toPx()), 10.dp.toPx())
    }
    Text(
        text = text,
        onTextLayout = { layout = it },
        style = TextStyle(
            color = colors.ink,
            fontFamily = GelasioFontFamily,
            fontWeight = if (long) FontWeight.Medium else FontWeight.Normal,
            fontSize = size.sp,
            lineHeight = (if (long) 1.5 else 1.4).em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            lineBreak = LineBreak.Paragraph,
            shadow = shadow
        ),
        modifier = Modifier.drawBehind {
            val l = layout ?: return@drawBehind
            // Read here, while drawing, so the sweep redraws the band without re-laying out the card.
            val progress = sweep()
            if (band == null || progress <= 0f) return@drawBehind
            val em = size.sp.toPx()
            for (i in 0 until l.lineCount) {
                val baseline = l.getLineBaseline(i)
                val left = l.getLineLeft(i) - 0.1f * em
                // Ends at the last visible character, not after a trailing space.
                val end = l.getLineEnd(i, visibleEnd = true)
                val right = (if (end > l.getLineStart(i)) l.getHorizontalPosition(end, true) else l.getLineRight(i)) + 0.1f * em
                val top = baseline - BAND_ABOVE * em
                drawRoundRect(
                    color = band,
                    topLeft = Offset(left, top),
                    size = Size((right - left) * progress, (BAND_ABOVE + BAND_BELOW) * em),
                    cornerRadius = CornerRadius(0.2f * em)
                )
            }
        }
    )
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

/**
 * Lays [content] out at the largest text size from [base] down to [min] (in [step]s) that fits the
 * height available, centered vertically — the way the design preview shrinks a long verse to fit.
 */
@Composable
private fun FitToHeight(base: Float, min: Float, step: Float, content: @Composable (Float) -> Unit) {
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        var size = base
        var attempt = 0
        val first = base
        var placeable = subcompose(attempt) { content(first) }.first().measure(loose)
        while (placeable.height > constraints.maxHeight && size > min && attempt < 16) {
            size = maxOf(min, size - step)
            attempt++
            val tryingSize = size
            placeable = subcompose(attempt) { content(tryingSize) }.first().measure(loose)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(0, ((constraints.maxHeight - placeable.height) / 2).coerceAtLeast(0))
        }
    }
}

/** The ring that pops under the finger on a double-tap, in the highlight's color. */
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
    onKeepGoing: () -> Unit
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
                PillButton("Keep scrolling", primary = false, colors = colors, onClick = onKeepGoing)
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
