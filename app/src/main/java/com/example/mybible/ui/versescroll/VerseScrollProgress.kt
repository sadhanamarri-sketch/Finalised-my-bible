package com.example.mybible.ui.versescroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.example.mybible.audio.ReadLanguage
import com.example.mybible.audio.VerseProgress
import com.example.mybible.versescroll.VerseFeedCard
import kotlinx.coroutines.delay
import kotlin.math.abs

// Where a tap or a drag on the bar sends the reading: a point in a part of the verse being read, or
// another of the card's verses, from its beginning.
private sealed interface BarTarget {
    data class Part(val part: Int, val at: Float) : BarTarget
    data class Verse(val number: Int) : BarTarget
}

/**
 * A card's bar laid out, 0 to 1 along it: each of its verses' stretch (a passage's), by how long it
 * takes (guessed from its text), and within the one being read ([now]), its parts, a language each.
 */
private class BarLayout(card: VerseFeedCard, language: ReadLanguage, now: VerseProgress?) {
    private val lines = card.content.lines
    private val estimates = lines.map { line ->
        val english = if (language != ReadLanguage.TELUGU) line.text.length / ENGLISH_CHARS_PER_SECOND else 0f
        // A verse's Telugu not on the card yet: about as long as its English, a tenth longer in letters.
        val letters = line.telugu?.length ?: (line.text.length * TELUGU_PER_ENGLISH).toInt()
        val telugu = if (language != ReadLanguage.ENGLISH) letters / TELUGU_CHARS_PER_SECOND else 0f
        english to telugu
    }

    /** Where each verse's stretch ends. */
    val ends: List<Float> = run {
        val weights = estimates.map { (english, telugu) -> (english + telugu).coerceAtLeast(1f) }
        val total = weights.sum()
        var sum = 0f
        weights.map { sum += it; sum / total }
    }

    fun start(i: Int): Float = if (i == 0) 0f else ends[i - 1]

    // The verse being read, and where in its stretch (0 to 1) each of its parts starts.
    private val current = now?.takeIf { it.verse.book == card.content.ref.book && it.verse.chapter == card.content.ref.chapter }
        ?.let { p -> lines.indexOfFirst { it.number == p.verse.verse } } ?: -1
    private val partStarts: List<Float> = now?.parts?.let { parts ->
        val total = parts.sumOf { it.seconds.toDouble() }.toFloat().coerceAtLeast(0.001f)
        var sum = 0f
        parts.map { part -> (sum / total).also { sum += part.seconds } }
    }.orEmpty()

    private fun along(i: Int, within: Float): Float = start(i) + (ends[i] - start(i)) * within

    /** How far along the bar the reading is; null before it's known. */
    val reached: Float? = now?.takeIf { current >= 0 }?.let { p ->
        val from = partStarts.getOrNull(p.part) ?: return@let null
        val to = partStarts.getOrNull(p.part + 1) ?: 1f
        along(current, from + (to - from) * p.at)
    }

    /** A dot where a verse's Telugu begins after its English (read in both), and where a tap on it goes. */
    val dots: List<Pair<Float, BarTarget>> = lines.indices.flatMap { i ->
        if (i == current) {
            (1 until partStarts.size).map { j -> along(i, partStarts[j]) to BarTarget.Part(j, 0f) }
        } else {
            val (english, telugu) = estimates[i]
            if (english > 0f && telugu > 0f) listOf(along(i, english / (english + telugu)) to BarTarget.Verse(lines[i].number)) else emptyList()
        }
    }

    /** Where [f] along the bar sends the reading; a dot within [snap] of it, to that dot's language. */
    fun target(f: Float, snap: Float): BarTarget? {
        dots.minByOrNull { abs(it.first - f) }?.takeIf { abs(it.first - f) <= snap }?.let { return it.second }
        val i = ends.indexOfFirst { f <= it }.takeIf { it >= 0 } ?: ends.lastIndex
        if (i < 0) return null
        if (i != current) return BarTarget.Verse(lines[i].number)
        val within = ((f - start(i)) / (ends[i] - start(i)).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        val j = partStarts.indexOfLast { it <= within }.takeIf { it >= 0 } ?: return null
        val span = (partStarts.getOrNull(j + 1) ?: 1f) - partStarts[j]
        return BarTarget.Part(j, ((within - partStarts[j]) / span.coerceAtLeast(0.001f)).coerceIn(0f, 1f))
    }
}

/**
 * The card's progress bar while it's read aloud, at its foot: it fills as the verse is read, with a dot
 * where its English gives way to its Telugu (read in both). A tap or a drag moves the reading there, a
 * recording from that moment, the phone's voice from the word there; a tap on a dot, to that
 * language's start. On a card of a few verses each has its stretch, and one dragged into is read from
 * its beginning. Held, the bar thickens, as a reel's does; it takes its own touches, apart from the
 * card's taps and the swipes between cards.
 */
@Composable
internal fun BoxScope.CardProgress(
    card: VerseFeedCard,
    language: ReadLanguage,
    progress: () -> VerseProgress?,
    colors: VsColors,
    onSeek: (part: Int, at: Float) -> Unit,
    onVerse: (Int) -> Unit
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Kept up to date a few times a second while the card is shown and read (with the screen off, not
    // at all); between its verses, as it last was.
    var now by remember(card.uid) { mutableStateOf<VerseProgress?>(null) }
    LaunchedEffect(card.uid) {
        while (true) {
            withFrameMillis { }
            progress()?.let { now = it }
            delay(POLL_MS)
        }
    }
    val layout = BarLayout(card, language, now)
    val currentLayout by rememberUpdatedState(layout)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnVerse by rememberUpdatedState(onVerse)
    var held by remember { mutableStateOf(false) }
    var heldAt by remember { mutableFloatStateOf(0f) }
    val snapPx = with(LocalDensity.current) { DOT_SNAP.toPx() }

    fun go(f: Float, snap: Float) {
        when (val target = currentLayout.target(f, snap)) {
            is BarTarget.Part -> currentOnSeek(target.part, target.at)
            is BarTarget.Verse -> currentOnVerse(target.number)
            null -> Unit
        }
    }

    Canvas(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = 112.dp + bottom)
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                contentDescription = "How far the verse has been read: drag to move"
                progressBarRangeInfo = ProgressBarRangeInfo(layout.reached ?: 0f, 0f..1f)
                setProgress { f ->
                    go(f, snap = 0f)
                    true
                }
            }
            .pointerInput(card.uid) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    held = true
                    heldAt = (down.position.x / size.width).coerceIn(0f, 1f)
                    var released = false
                    var dragged = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        heldAt = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) dragged = true
                        change.consume()
                        if (!change.pressed) {
                            released = true
                            break
                        }
                    }
                    held = false
                    // A tap by a dot goes to its language's start; a drag, just where it's let go.
                    if (released) go(heldAt, snap = if (dragged) 0f else snapPx / size.width)
                }
            }
    ) {
        val thick = (if (held) HELD_THICKNESS else THICKNESS).toPx()
        val y = size.height / 2
        val gap = VERSE_GAP.toPx()
        val track = colors.ink.copy(alpha = 0.24f)
        val fill = if (held) heldAt else layout.reached
        for (i in layout.ends.indices) {
            val x0 = layout.start(i) * size.width + if (i > 0) gap / 2 else 0f
            val x1 = layout.ends[i] * size.width - if (i < layout.ends.lastIndex) gap / 2 else 0f
            if (x1 <= x0) continue
            val corner = CornerRadius(thick / 2)
            drawRoundRect(track, Offset(x0, y - thick / 2), Size(x1 - x0, thick), corner)
            val filled = (fill ?: 0f) * size.width
            if (filled > x0) drawRoundRect(colors.gold, Offset(x0, y - thick / 2), Size(minOf(filled, x1) - x0, thick), corner)
        }
        val dot = (if (held) HELD_DOT else DOT).toPx()
        for ((at, _) in layout.dots) drawCircle(colors.ink, radius = dot, center = Offset(at * size.width, y))
        if (held) drawCircle(colors.gold, radius = THUMB.toPx(), center = Offset(heldAt * size.width, y))
    }
}

// The phone's voice's pace, roughly, in characters a second (as ReadAloud guesses it): a verse's stretch.
private const val ENGLISH_CHARS_PER_SECOND = 15f
private const val TELUGU_CHARS_PER_SECOND = 13f
private const val TELUGU_PER_ENGLISH = 1.1f

private val THICKNESS = 3.dp
private val HELD_THICKNESS = 8.dp
private val DOT = 3.5.dp
private val HELD_DOT = 5.dp
private val THUMB = 9.dp
private val VERSE_GAP = 3.dp

// A tap this close to a dot goes to that language's start.
private val DOT_SNAP = 14.dp

// How often the bar catches up with the reading, at most (milliseconds).
private const val POLL_MS = 80L
