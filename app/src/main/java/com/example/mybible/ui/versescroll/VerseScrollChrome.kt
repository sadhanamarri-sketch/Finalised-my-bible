package com.example.mybible.ui.versescroll

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.mybible.audio.ReadAloudState
import com.example.mybible.model.HighlightColorDef
import com.example.mybible.model.MatchedWord
import com.example.mybible.ui.theme.GelasioFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.ui.theme.quotePurple
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BookListStep
import com.example.mybible.ui.components.ChapterGridStep
import com.example.mybible.ui.components.DsNote
import com.example.mybible.ui.components.DsOutlineAccentButton
import com.example.mybible.ui.components.DsSectionDivider
import com.example.mybible.ui.components.DsSectionLabel
import com.example.mybible.ui.components.DsSizeAdjustRow
import com.example.mybible.ui.components.DsToggleRow
import com.example.mybible.ui.components.PauseBetweenCardsRow
import com.example.mybible.ui.components.QuoteTag
import com.example.mybible.ui.components.SleepTimerMenu
import com.example.mybible.ui.components.rememberSleepLeft
import com.example.mybible.ui.components.sleepLeftLabel
import com.example.mybible.ui.components.PickerDarkGold
import com.example.mybible.ui.components.PickerPaperGold
import com.example.mybible.versescroll.VerseRef
import com.example.mybible.versescroll.VerseScrollController
import com.example.mybible.versescroll.VerseSource
import java.util.Locale

private val OnColorInk = Color(0xFF2C221E)

/** The strip behind the top bar: the theme background fading out, or a darker (lighter) wash over a scene. */
private fun topScrim(colors: VsColors, scenes: Boolean): Brush =
    if (!scenes) Brush.verticalGradient(0f to colors.bg, 0.62f to colors.bg, 1f to colors.bg.copy(alpha = 0f))
    else if (colors.dark) Brush.verticalGradient(listOf(Color(0x800A0808), Color(0x000A0808)))
    else Brush.verticalGradient(listOf(Color(0xBFFAF7F0), Color(0x00FAF7F0)))

private fun bottomScrim(colors: VsColors, scenes: Boolean): Brush =
    if (!scenes) Brush.verticalGradient(0f to colors.bg.copy(alpha = 0f), 0.28f to colors.bg, 1f to colors.bg)
    else if (colors.dark) Brush.verticalGradient(0f to Color(0x000A0808), 0.65f to Color(0x990A0808), 1f to Color(0x990A0808))
    else Brush.verticalGradient(0f to Color(0x00FAF7F0), 0.65f to Color(0xCCFAF7F0), 1f to Color(0xCCFAF7F0))

/**
 * Back, the feed's two tabs, the speaker and settings. The first tab is the mode, named for where its
 * verses come from ("Full", "Romans 8", "Highlighted"): tapped while showing, it opens the choice of
 * verses. The speaker reads each card aloud as it comes up. Inside a Rabbit hole, past its first verse,
 * a "Back to …" button underneath returns to the verse it started from.
 */
@Composable
internal fun BoxScope.VerseScrollTopBar(
    colors: VsColors,
    scenes: Boolean,
    rabbitHole: Boolean,
    backTo: String?,
    modeLabel: String,
    speakerOn: Boolean,
    onBack: () -> Unit,
    onDiscover: () -> Unit,
    onRabbitHole: () -> Unit,
    onBackTo: () -> Unit,
    onSpeaker: () -> Unit,
    onSettings: () -> Unit,
    sleepChip: (@Composable () -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(topScrim(colors, scenes))
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                VerseScrollIcons.Back,
                if (rabbitHole) "Leave the Rabbit hole" else "Back to the Reader",
                colors.ink,
                onBack
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                modifier = Modifier.weight(1f)
            ) {
                Tab(modeLabel, selected = !rabbitHole, colors = colors, menu = !rabbitHole, onClick = onDiscover)
                Tab("Rabbit hole", selected = rabbitHole, colors = colors, onClick = onRabbitHole)
            }
            RoundIconButton(
                if (speakerOn) VerseScrollIcons.Speaker else VerseScrollIcons.SpeakerOff,
                if (speakerOn) "Stop reading aloud" else "Read aloud",
                if (speakerOn) colors.gold else colors.ink,
                onSpeaker
            )
            RoundIconButton(VerseScrollIcons.Settings, "Verse Scroll settings", colors.ink, onSettings)
        }
        if (sleepChip != null) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) { sleepChip() }
        }
        if (backTo != null) {
            BackToChip(backTo, colors, scenes, onBackTo, Modifier.padding(top = 4.dp))
        }
    }
}

// A chip's ground and edge under the top bar, over a photo or the plain page.
private fun chipBackground(colors: VsColors, scenes: Boolean): Color = when {
    !scenes -> colors.surface
    colors.dark -> Color(0x73141210)
    else -> Color.White.copy(alpha = 0.62f)
}

private fun chipBorder(colors: VsColors, scenes: Boolean): Color = when {
    !scenes -> colors.line
    colors.dark -> Color.White.copy(alpha = 0.16f)
    else -> Color.Black.copy(alpha = 0.08f)
}

/**
 * A Verse Scroll reading's sleep timer, under the top bar while one's set: a moon and the time left. A tap
 * opens its choices: so many minutes, or Cancel timer.
 */
@Composable
internal fun SleepChip(
    state: ReadAloudState,
    colors: VsColors,
    scenes: Boolean,
    sleepLeft: () -> Long?,
    onSleepIn: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val sleep = state.sleep ?: return
    val left = rememberSleepLeft(sleep, sleepLeft)
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(chipBackground(colors, scenes))
                .border(1.dp, chipBorder(colors, scenes), CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "Sleep timer: ${sleepLeftLabel(sleep, left)}" }
                .padding(start = 9.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = colors.gold, modifier = Modifier.size(15.dp))
            Text(
                text = sleepLeftLabel(sleep, left),
                color = colors.ink,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        SleepTimerMenu(open, { open = false }, state, left, onSleepIn, onChapterEnd = null, onCancel = onCancel)
    }
}

@Composable
private fun BackToChip(label: String, colors: VsColors, scenes: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val background = chipBackground(colors, scenes)
    val border = chipBorder(colors, scenes)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 13.dp, top = 7.dp, bottom = 7.dp)
    ) {
        Icon(VerseScrollIcons.Return, contentDescription = null, tint = colors.ink, modifier = Modifier.size(14.dp))
        Text(
            text = buildAnnotatedString {
                append("Back to ")
                withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold)) { append(label) }
            },
            color = colors.soft,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// [menu]: a small arrow after the label, for a tab that opens a choice when tapped.
@Composable
private fun Tab(label: String, selected: Boolean, colors: VsColors, menu: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                if (menu) contentDescription = "$label: choose which verses"
            }
            .padding(horizontal = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
        ) {
            Text(
                text = label,
                color = if (selected) colors.ink else colors.soft,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            if (menu) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (selected) colors.ink else colors.soft,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Box(
            Modifier
                .height(2.5.dp)
                .width(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) colors.ink else Color.Transparent)
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** What the bottom bar's Highlight button shows: the card's color and its name, once it has one. */
internal data class BarHighlight(val hexes: List<String>, val label: String)

/** Acts on whichever verse is on screen. */
@Composable
internal fun BoxScope.VerseScrollBottomBar(
    colors: VsColors,
    scenes: Boolean,
    highlight: BarHighlight?,
    linkCount: Int,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onRead: () -> Unit,
    onLinks: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(bottomScrim(colors, scenes))
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)
    ) {
        BarButton(
            icon = VerseScrollIcons.Highlight,
            label = highlight?.label ?: "Highlight",
            description = if (highlight == null) "Highlight this verse" else "Change or remove the ${highlight.label} highlight",
            fill = highlight?.let { h -> h.hexes.map(::hexToColor) }.orEmpty(),
            colors = colors,
            scenes = scenes,
            onClick = onHighlight
        )
        BarButton(VerseScrollIcons.Note, "Note", "Add a note", emptyList(), colors, scenes, onNote)
        BarButton(VerseScrollIcons.Read, "Read", "Read in context", emptyList(), colors, scenes, onRead)
        BarButton(
            icon = VerseScrollIcons.Links,
            label = if (linkCount == 1) "1 link" else "$linkCount links",
            description = "See the verses this one links to ($linkCount)",
            fill = emptyList(),
            colors = colors,
            scenes = scenes,
            onClick = onLinks
        )
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    label: String,
    description: String,
    // The highlight's colors, top to bottom, filling the circle in bands; none for a plain button.
    fill: List<Color>,
    colors: VsColors,
    scenes: Boolean,
    onClick: () -> Unit
) {
    val plain = when {
        !scenes -> colors.surface
        colors.dark -> Color.White.copy(alpha = 0.1f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    val background: Brush = when (fill.size) {
        0 -> SolidColor(plain)
        1 -> SolidColor(fill.first())
        else -> Brush.verticalGradient(
            *fill.flatMapIndexed { i, c -> listOf(i.toFloat() / fill.size to c, (i + 1f) / fill.size to c) }.toTypedArray()
        )
    }
    val border = when {
        fill.isNotEmpty() -> Color.Transparent
        !scenes -> colors.line
        colors.dark -> Color.White.copy(alpha = 0.18f)
        else -> Color.Black.copy(alpha = 0.08f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(background)
                .border(1.dp, border, CircleShape)
        ) {
            Icon(icon, contentDescription = null, tint = if (fill.isNotEmpty()) OnColorInk else colors.ink, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            color = colors.soft,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 1.15.em,
            textAlign = TextAlign.Center,
            // The highlight's name can be two words: "Prayer request".
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(hyphens = Hyphens.Auto)
        )
    }
}

@Composable
internal fun BoxScope.VersesTodayCounter(count: Int, colors: VsColors) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Text(
        text = "$count verse${if (count == 1) "" else "s"} today",
        color = colors.soft,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 22.dp, bottom = 92.dp + bottom)
    )
}

/**
 * The color box: after a double-tap (or the bottom bar's Highlight) it takes the bottom bar's place, with
 * all twelve colors and their names plus Note and Remove, until a color is picked or the verse touched.
 * A color puts it on the verse or takes it off (a verse can be in up to three); Remove takes them all
 * off. It never dims or covers the verse.
 */
@Composable
internal fun ColorBox(
    colors: VsColors,
    defs: List<HighlightColorDef>,
    current: List<String>,
    onPick: (String) -> Unit,
    onNote: () -> Unit,
    onRemove: () -> Unit
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val items: List<@Composable (Modifier) -> Unit> = defs.map { def ->
        @Composable { m: Modifier ->
            val selected = current.any { it.equals(def.colorHex, ignoreCase = true) }
            BoxItem(
                label = def.label,
                selected = selected,
                colors = colors,
                modifier = m,
                description = if (selected) "${def.label}, on: take it off" else def.label,
                onClick = { onPick(def.colorHex) }
            ) { Swatch(hexToColor(def.colorHex), selected, colors) }
        }
    } + listOf(
        @Composable { m: Modifier ->
            BoxItem("Note", false, colors, m, "Add a note", onNote) { ToolCircle(VerseScrollIcons.Note, colors) }
        },
        @Composable { m: Modifier ->
            BoxItem("Remove", false, colors, m, "Remove the highlight", onRemove) { ToolCircle(VerseScrollIcons.RemoveHighlight, colors) }
        }
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(start = 6.dp, end = 6.dp, bottom = 8.dp + bottom)
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 2.dp, vertical = 10.dp)
    ) {
        items.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item -> item(Modifier.weight(1f)) }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BoxItem(
    label: String,
    selected: Boolean,
    colors: VsColors,
    modifier: Modifier,
    description: String,
    onClick: () -> Unit,
    circle: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
    ) {
        circle()
        Text(
            text = label,
            color = if (selected) colors.ink else colors.soft,
            fontFamily = WorkSansFontFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 9.5.sp,
            letterSpacing = (-0.01).em,
            lineHeight = 1.15.em,
            textAlign = TextAlign.Center,
            // A name of two words, "Prayer request", on two lines rather than cut off, and a word
            // too long for the line broken with a hyphen.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(hyphens = Hyphens.Auto)
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, colors: VsColors, size: Dp = 28.dp) {
    Canvas(Modifier.size(size + 8.dp)) {
        val r = size.toPx() / 2
        drawCircle(color, radius = r)
        drawCircle(Color.Black.copy(alpha = 0.08f), radius = r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        if (selected) drawCircle(colors.ink, radius = r + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun ToolCircle(icon: ImageVector, colors: VsColors) {
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.bg)
                .border(1.dp, colors.line, CircleShape)
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(15.dp))
        }
    }
}

internal data class VsToast(val id: Long, val message: String, val action: String? = null, val onAction: (() -> Unit)? = null)

@Composable
internal fun BoxScope.ToastBar(toast: VsToast, colors: VsColors, onDismiss: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 14.dp, end = 14.dp, bottom = 90.dp + bottom)
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(colors.toastBg)
            .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp)
    ) {
        Text(
            text = toast.message,
            color = colors.toastInk,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 1.3.em,
            modifier = Modifier.weight(1f)
        )
        if (toast.action != null) {
            Text(
                text = toast.action.uppercase(),
                color = colors.toastAction,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.08.em,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        onDismiss()
                        toast.onAction?.invoke()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** Shown once: how to highlight, with rings pulsing where a thumb would tap. */
@Composable
internal fun BoxScope.FirstTimeHint(colors: VsColors, reduceMotion: Boolean, onDismiss: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (!reduceMotion) {
        val pulse = rememberInfiniteTransition(label = "hintRings")
        val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1700)), label = "hintRingsProgress")
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.44f)
            for (offset in listOf(0f, 0.24f / 1.7f)) {
                val p = ((t - offset + 1f) % 1f) / 0.55f
                if (p > 1f) continue
                val radius = 27.dp.toPx() * (0.4f + 0.85f * p)
                drawCircle(colors.gold, radius = radius, center = center, alpha = 0.9f * (1f - p), style = Stroke(2.dp.toPx()))
            }
        }
    }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 18.dp, end = 18.dp, bottom = 118.dp + bottom)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Double-tap a verse to choose its color",
            color = colors.ink,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Text(
            text = "Pick one from the box that pops up, or long-press for a note. Swipe up for the next verse.",
            color = colors.soft,
            fontFamily = WorkSansFontFamily,
            fontSize = 13.sp,
            lineHeight = 1.45.em,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        PillButton("Got it", primary = true, colors = colors, onClick = onDismiss)
    }
}

/**
 * Verse Scroll's own settings, laid out like the app's Settings: the background and its motion, the
 * Telugu under each verse and its size, and reading aloud.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerseScrollSettingsSheet(
    photos: Boolean,
    motion: Boolean,
    reduceMotion: Boolean,
    showTelugu: Boolean,
    teluguSize: Int,
    sceneCount: Int,
    continueReading: Boolean,
    pauseBetweenCards: Int,
    onPhotos: (Boolean) -> Unit,
    onMotion: (Boolean) -> Unit,
    onShowTelugu: (Boolean) -> Unit,
    onTeluguSize: (Int) -> Unit,
    onContinueReading: (Boolean) -> Unit,
    onPauseBetweenCards: (Int) -> Unit,
    onReadAloudSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The page's own color, as the app's Settings: its size row and dividers show against it.
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Taller than some screens.
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text(
                text = "Verse Scroll",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            DsSectionLabel("Background", isFirst = true)
            DsToggleRow(
                label = "Nature photos",
                subLabel = (if (sceneCount > 0) "$sceneCount photos" else "Photos") + " from Unsplash, matched to the verse " +
                    "(the sea, the night, snow, harvest, mountains) and dimmed so it stays easy to read. Off: the plain page.",
                checked = photos,
                onCheckedChange = onPhotos
            )
            if (photos) {
                Spacer(Modifier.height(8.dp))
                DsToggleRow(
                    label = "Gentle motion",
                    subLabel = if (reduceMotion) "Your phone is set to reduce motion, so photos stay still."
                    else "Photos drift and zoom very slowly. Only the one on screen moves.",
                    checked = motion && !reduceMotion,
                    onCheckedChange = onMotion,
                    enabled = !reduceMotion
                )
            }
            DsSectionDivider()

            DsSectionLabel("Telugu")
            DsToggleRow(
                label = "Telugu under each verse",
                subLabel = "The Telugu Bible's verse, under the King James text.",
                checked = showTelugu,
                onCheckedChange = onShowTelugu
            )
            Spacer(Modifier.height(10.dp))
            DsSizeAdjustRow(
                valueLabel = "${teluguSize}px",
                onDecrease = { onTeluguSize(-1) },
                onIncrease = { onTeluguSize(1) }
            )
            DsSectionDivider()

            DsSectionLabel("Reading aloud")
            DsToggleRow(
                label = "Continue reading",
                subLabel = "With the speaker at the top on, each card is read aloud as it comes up" +
                    if (continueReading) ", then Verse Scroll moves on to the next by itself, hands-free, even with the screen off."
                    else ". On: Verse Scroll moves on to the next by itself, hands-free.",
                checked = continueReading,
                onCheckedChange = onContinueReading
            )
            if (continueReading) PauseBetweenCardsRow(pauseBetweenCards, onPauseBetweenCards, Modifier.padding(top = 14.dp))
            DsNote(
                "Earbuds and headsets: a tap pauses or plays; next and previous (often a double and a triple tap) go a card on or back.",
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(14.dp))
            DsOutlineAccentButton(text = "Language, voice and speed", onClick = onReadAloudSettings)
        }
    }
}

/**
 * Which verses Verse Scroll shows: Full, Focused, your highlighted or studied verses, the Reader's
 * chapter, or any book or chapter, read in order (picked from the books, then the chapters). A
 * search's or topic's verses show at the top while they're on; a search's, with the words that found
 * them to pick from, put to use as the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerseScrollSourceSheet(
    colors: VsColors,
    current: VerseSource,
    fullCount: Int,
    focusedCount: Int,
    highlightedCount: Int,
    studiedCount: Int,
    readerBook: String,
    readerChapter: Int,
    onChoose: (VerseSource) -> Unit,
    onDismiss: () -> Unit
) {
    // null: the list of choices; a book's name: its chapters; "": the books.
    var picking by remember { mutableStateOf<String?>(null) }
    // "1 verse", "2,000 verses"; [kind] goes before it: "5 highlighted verses".
    fun verses(n: Int, kind: String = "") = if (n == 1) "1 ${kind}verse" else String.format(Locale.US, "%,d ${kind}verses", n)
    // A search's verses: the words picked so far (every one of them is the same as none), and the
    // verses they'd show.
    val found = current as? VerseSource.Found
    var picked by remember(found) { mutableStateOf(found?.only.orEmpty()) }
    val pending = remember(found, picked) {
        found?.copy(only = picked.takeIf { it.size < found.words.size }.orEmpty())
    }
    // However the sheet closes: the words picked, when they've changed.
    val close = {
        if (pending != null && pending != found) onChoose(pending) else onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        val book = picking
        if (book == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Verses from",
                    color = colors.ink,
                    fontFamily = WorkSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
                if (found != null && pending != null) {
                    val pick = if (found.words.isNotEmpty() && picked.isEmpty()) " Pick words to see only the verses they found." else ""
                    SourceRow(found.label, pending.blurb.replaceFirstChar { it.uppercase() } + "." + pick, true, true, colors) { close() }
                    if (found.words.isNotEmpty()) {
                        WordFilter(
                            words = found.words,
                            picked = picked,
                            colors = colors,
                            onPick = { word -> picked = if (word in picked) picked - word else picked + word }
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)
                                .fillMaxWidth()
                        ) {
                            if (picked.isNotEmpty()) {
                                PillButton("Clear", primary = false, colors = colors, onClick = { picked = emptySet() })
                            }
                            val n = pending.shown.size
                            PillButton(
                                if (pending.only.isEmpty()) "Show all ${verses(n)}" else "Show ${verses(n)}",
                                primary = true,
                                colors = colors,
                                onClick = close,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Box(
                            Modifier
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.line)
                        )
                    }
                }
                SourceRow(
                    "Full",
                    (if (fullCount > 0) "All ${verses(fullCount)} that other verses point to" else "Every verse that other verses point to") +
                        ", the best known coming up most.",
                    current == VerseSource.Full, true, colors
                ) { onChoose(VerseSource.Full) }
                SourceRow(
                    "Focused",
                    if (focusedCount > 0) "Only the ${verses(focusedCount, "best-known ")}." else "Only the best-known verses.",
                    current == VerseSource.Focused, true, colors
                ) { onChoose(VerseSource.Focused) }
                SourceRow(
                    "Highlighted",
                    if (highlightedCount > 0) "Your ${verses(highlightedCount, "highlighted ")}, shuffled." else "No highlighted verses yet.",
                    current == VerseSource.Highlighted, highlightedCount > 0, colors
                ) { onChoose(VerseSource.Highlighted) }
                SourceRow(
                    "Studied",
                    if (studiedCount > 0) "Your ${verses(studiedCount, "studied ")}, shuffled." else "No verses marked studied yet.",
                    current == VerseSource.Studied, studiedCount > 0, colors
                ) { onChoose(VerseSource.Studied) }
                val here = VerseSource.Chapter(readerBook, readerChapter)
                if (readerBook.isNotBlank() && readerChapter > 0) {
                    SourceRow(
                        here.label,
                        "The chapter you're reading, verse by verse.",
                        current == here, true, colors
                    ) { onChoose(here) }
                }
                val other = current.takeIf { it.inOrder && it != here }
                SourceRow(
                    other?.label ?: "A book or chapter",
                    if (other != null) "In order. Tap to choose another book or chapter." else "Any book or chapter, read in order, verse by verse.",
                    other != null, true, colors, more = true
                ) { picking = "" }
            }
        } else {
            val isDark = colors.dark
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f)
                    .navigationBarsPadding()
            ) {
                if (book.isEmpty()) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { picking = null }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("\u2039 Verses from", color = colors.ink, fontFamily = WorkSansFontFamily, fontSize = 15.sp)
                        }
                        BookListStep(
                            listState = rememberLazyListState(),
                            otCount = BIBLE_BOOKS.indexOf("Matthew"),
                            currentBook = (current as? VerseSource.Book)?.book ?: (current as? VerseSource.Chapter)?.book ?: readerBook,
                            goldColor = if (isDark) PickerDarkGold else PickerPaperGold,
                            onBookSelected = { picking = it }
                        )
                    }
                } else {
                    ChapterGridStep(
                        bookName = book,
                        onBack = { picking = "" },
                        onChapterSelected = { onChoose(VerseSource.Chapter(book, it)) },
                        header = {
                            PillButton(
                                "All of $book, in order",
                                primary = true,
                                colors = colors,
                                onClick = { onChoose(VerseSource.Book(book)) },
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * What found a search's verses ("loveth", "charity", "Greek agapē"), each with how many: the word
 * searched for, King James wording, Greek and Hebrew. Picked words show only the verses they found.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordFilter(
    words: List<Pair<MatchedWord, Int>>,
    picked: Set<MatchedWord>,
    colors: VsColors,
    onPick: (MatchedWord) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)) {
        words.groupBy { it.first.kind }.entries.forEachIndexed { i, (kind, group) ->
            Text(
                text = when (kind) {
                    MatchedWord.Kind.WORD -> "Searched for"
                    MatchedWord.Kind.WORDING -> "King James wording"
                    MatchedWord.Kind.ORIGINAL -> "Greek & Hebrew"
                }.uppercase(),
                color = colors.gold,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.12.em,
                modifier = Modifier.padding(top = if (i == 0) 4.dp else 14.dp, bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.forEach { (word, count) ->
                    WordChip(word.label, count, word in picked, colors) { onPick(word) }
                }
            }
        }
    }
}

// A word to pick, with how many verses it found; a check, filled, when picked.
@Composable
private fun WordChip(label: String, count: Int, picked: Boolean, colors: VsColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (picked) colors.primary else Color.Transparent)
            .border(1.dp, if (picked) colors.primary else colors.line, CircleShape)
            .toggleable(value = picked, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(start = if (picked) 10.dp else 13.dp, end = 13.dp, top = 7.dp, bottom = 7.dp)
    ) {
        if (picked) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.padding(end = 5.dp).size(16.dp))
        }
        Text(
            text = label,
            color = if (picked) colors.onPrimary else colors.ink,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp
        )
        Text(
            text = "  $count",
            color = if (picked) colors.onPrimary.copy(alpha = 0.75f) else colors.soft,
            fontFamily = WorkSansFontFamily,
            fontSize = 13.sp
        )
    }
}

// One choice of verses: its name, what it holds, a check when it's the one showing.
@Composable
private fun SourceRow(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    colors: VsColors,
    more: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) colors.ink else colors.soft.copy(alpha = 0.6f),
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                color = if (enabled) colors.soft else colors.soft.copy(alpha = 0.6f),
                fontFamily = WorkSansFontFamily,
                fontSize = 13.sp,
                lineHeight = 1.4.em,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = "Showing", tint = colors.primary, modifier = Modifier.padding(start = 12.dp).size(20.dp))
            more -> Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = colors.soft, modifier = Modifier.padding(start = 12.dp).size(20.dp))
        }
    }
}

/**
 * A verse's strongest links: tap one to read it next (going down a Rabbit hole from here), follow
 * them all, or see every link in Cross References.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinksSheet(
    colors: VsColors,
    label: String,
    total: Int,
    previews: List<VerseScrollController.LinkPreview>?,
    highlightHexes: (VerseRef) -> List<String>,
    colorLabel: (String) -> String,
    onPick: (VerseScrollController.LinkPreview) -> Unit,
    onFollow: () -> Unit,
    onSeeAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            Text(
                text = "Links from $label".uppercase(),
                color = colors.gold,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.12.em
            )
            val shown = previews?.size ?: 0
            val quotes = previews.orEmpty().count { it.link.quote }
            val sub = when {
                previews == null -> "Finding the strongest links…"
                quotes > 0 && shown > quotes -> "Quotations first, then the strongest of its $total links. Tap one to read it next."
                quotes > 0 -> if (quotes == 1) "A quotation. Tap it to read it next." else "Quotations. Tap one to read it next."
                shown == 1 && total > 1 -> "The strongest of $total. Tap it to read it next."
                shown in 2 until total -> "The $shown strongest of $total. Tap one to read it next."
                shown == 1 -> "Tap it to read it next."
                shown > 1 -> "Tap one to read it next."
                total > 0 -> "None of its $total links is strong enough to suggest. Cross References lists them all."
                else -> "This verse has no links."
            }
            Text(
                text = sub,
                color = colors.soft,
                fontFamily = WorkSansFontFamily,
                fontSize = 13.sp,
                lineHeight = 1.4.em,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            previews.orEmpty().forEach { preview ->
                LinkItem(preview, colors, highlightHexes(preview.link.ref), colorLabel) { onPick(preview) }
            }
            Spacer(Modifier.height(8.dp))
            if (shown > 0) {
                PillButton("Follow this verse’s links", primary = true, colors = colors, onClick = onFollow, modifier = Modifier.fillMaxWidth())
            }
            if (total > 0) {
                PillButton("See all $total in Cross References", primary = false, colors = colors, onClick = onSeeAll, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LinkItem(
    preview: VerseScrollController.LinkPreview,
    colors: VsColors,
    highlights: List<String>,
    colorLabel: (String) -> String,
    onClick: () -> Unit
) {
    val content = preview.content
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 13.dp, end = 13.dp, top = 11.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A quotation: its reference in purple, tagged.
            Text(
                text = content.label.uppercase(),
                color = if (preview.link.quote) quotePurple(colors.dark) else colors.gold,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.1.em
            )
            if (preview.link.quote) QuoteTag(quotePurple(colors.dark))
            highlights.forEach { highlight ->
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(hexToColor(highlight))
                        .semantics { contentDescription = "Highlighted ${colorLabel(highlight)}" }
                )
            }
            Spacer(Modifier.weight(1f))
            if (preview.seen) {
                Text("Seen", color = colors.soft, fontFamily = WorkSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)
            }
        }
        val number = SpanStyle(
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            baselineShift = BaselineShift(0.5f),
            color = colors.soft
        )
        Text(
            text = buildAnnotatedString {
                content.lines.forEachIndexed { i, line ->
                    if (i > 0) append(' ')
                    if (content.lines.size > 1) withStyle(number) { append("${line.number} ") }
                    append(line.text)
                }
            },
            color = colors.ink,
            fontFamily = GelasioFontFamily,
            fontSize = 15.sp,
            lineHeight = 1.45.em,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
