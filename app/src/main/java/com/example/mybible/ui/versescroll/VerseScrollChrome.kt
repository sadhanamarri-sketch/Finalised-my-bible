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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.mybible.model.HighlightColorDef
import com.example.mybible.ui.theme.GelasioFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily
import com.example.mybible.versescroll.VerseRef
import com.example.mybible.versescroll.VerseScrollController

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
 * Back, the feed's two modes (Discover and Rabbit hole), and settings. Inside a Rabbit hole, past its
 * first verse, a "Back to …" button underneath returns to the verse it started from.
 */
@Composable
internal fun BoxScope.VerseScrollTopBar(
    colors: VsColors,
    scenes: Boolean,
    rabbitHole: Boolean,
    backTo: String?,
    onBack: () -> Unit,
    onDiscover: () -> Unit,
    onRabbitHole: () -> Unit,
    onBackTo: () -> Unit,
    onSettings: () -> Unit
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
                Tab("Discover", selected = !rabbitHole, colors = colors, onClick = onDiscover)
                Tab("Rabbit hole", selected = rabbitHole, colors = colors, onClick = onRabbitHole)
            }
            RoundIconButton(VerseScrollIcons.Settings, "Verse Scroll settings", colors.ink, onSettings)
        }
        if (backTo != null) {
            BackToChip(backTo, colors, scenes, onBackTo, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BackToChip(label: String, colors: VsColors, scenes: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val background = when {
        !scenes -> colors.surface
        colors.dark -> Color(0x73141210)
        else -> Color.White.copy(alpha = 0.62f)
    }
    val border = when {
        !scenes -> colors.line
        colors.dark -> Color.White.copy(alpha = 0.16f)
        else -> Color.Black.copy(alpha = 0.08f)
    }
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

@Composable
private fun Tab(label: String, selected: Boolean, colors: VsColors, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.ink else colors.soft,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
        )
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
internal data class BarHighlight(val hex: String, val label: String)

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
            fill = highlight?.let { hexToColor(it.hex) },
            colors = colors,
            scenes = scenes,
            onClick = onHighlight
        )
        BarButton(VerseScrollIcons.Note, "Note", "Add a note", null, colors, scenes, onNote)
        BarButton(VerseScrollIcons.Read, "Read", "Read in context", null, colors, scenes, onRead)
        BarButton(
            icon = VerseScrollIcons.Links,
            label = if (linkCount == 1) "1 link" else "$linkCount links",
            description = "See the verses this one links to ($linkCount)",
            fill = null,
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
    fill: Color?,
    colors: VsColors,
    scenes: Boolean,
    onClick: () -> Unit
) {
    val background = fill ?: when {
        !scenes -> colors.surface
        colors.dark -> Color.White.copy(alpha = 0.1f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    val border = when {
        fill != null -> Color.Transparent
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
            Icon(icon, contentDescription = null, tint = if (fill != null) OnColorInk else colors.ink, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            color = colors.soft,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 1.15.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
 * The color box: after a double-tap it takes the bottom bar's place for a few seconds, with all twelve
 * colors and their names plus Note and Remove. It never dims or covers the verse.
 */
@Composable
internal fun ColorBox(
    colors: VsColors,
    defs: List<HighlightColorDef>,
    current: String?,
    onTouch: () -> Unit,
    onPick: (String) -> Unit,
    onNote: () -> Unit,
    onRemove: () -> Unit
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val touched by rememberUpdatedState(onTouch)
    val items: List<@Composable (Modifier) -> Unit> = defs.map { def ->
        @Composable { m: Modifier ->
            val selected = current.equals(def.colorHex, ignoreCase = true)
            BoxItem(
                label = def.label,
                selected = selected,
                colors = colors,
                modifier = m,
                description = if (selected) "${def.label}, current color" else def.label,
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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    // Any touch inside keeps the box up a while longer.
                    while (true) {
                        awaitPointerEvent()
                        touched()
                    }
                }
            }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

/** Shown once: how to highlight and unfold a verse, with rings pulsing where a thumb would tap. */
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
            text = "Double-tap a verse to highlight it",
            color = colors.ink,
            fontFamily = WorkSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
        Text(
            text = "Pick another color from the box that pops up, or long-press for a note. Tap once to see the verses around it, and swipe up for the next verse.",
            color = colors.soft,
            fontFamily = WorkSansFontFamily,
            fontSize = 13.sp,
            lineHeight = 1.45.em,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        PillButton("Got it", primary = true, colors = colors, onClick = onDismiss)
    }
}

/** Verse Scroll's own settings: backgrounds, motion, the double-tap color, and the Telugu line. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VerseScrollSettingsSheet(
    colors: VsColors,
    defs: List<HighlightColorDef>,
    paintedScenes: Boolean,
    motion: Boolean,
    reduceMotion: Boolean,
    doubleTapColor: String,
    showTelugu: Boolean,
    sceneCount: Int,
    onPaintedScenes: (Boolean) -> Unit,
    onMotion: (Boolean) -> Unit,
    onDoubleTapColor: (String) -> Unit,
    onShowTelugu: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            Text(
                text = "Verse Scroll",
                color = colors.ink,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            SettingGroup("Backgrounds", colors) {
                OptionPill("Painted scenes", paintedScenes, colors) { onPaintedScenes(true) }
                OptionPill("Plain", !paintedScenes, colors) { onPaintedScenes(false) }
            }
            SettingNote(
                (if (sceneCount > 0) "$sceneCount painted scenes" else "Painted scenes") +
                    ", each dimmed as much as it needs for the verse to stay easy to read. A verse about the sea, " +
                    "the night, snow, harvest, peace, a shepherd or mountains gets a matching scene. Check-ins " +
                    "stay plain, as a pause.",
                colors
            )
            SettingGroup("Motion", colors) {
                OptionPill("Gentle", motion, colors) { onMotion(true) }
                OptionPill("Off", !motion, colors) { onMotion(false) }
            }
            SettingNote(
                if (reduceMotion) "Your phone is set to reduce motion, so scenes stay still."
                else "Scenes drift and zoom very slowly, with a small moving touch of their own. Only the verse on " +
                    "screen moves.",
                colors
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingLabel("Double-tap color", colors)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    defs.forEach { def ->
                        val selected = def.colorHex.equals(doubleTapColor, ignoreCase = true)
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.RadioButton) { onDoubleTapColor(def.colorHex) }
                                .semantics {
                                    contentDescription = def.label
                                    this.selected = selected
                                }
                        ) { Swatch(hexToColor(def.colorHex), selected, colors) }
                    }
                }
                SettingNote(
                    "Double-tap highlights in ${defs.find { it.colorHex.equals(doubleTapColor, ignoreCase = true) }?.label ?: "this color"}.",
                    colors
                )
            }
            SettingGroup("Telugu under each verse", colors) {
                OptionPill("Off", !showTelugu, colors) { onShowTelugu(false) }
                OptionPill("On", showTelugu, colors) { onShowTelugu(true) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingGroup(label: String, colors: VsColors, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingLabel(label, colors)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options()
        }
    }
}

@Composable
private fun SettingLabel(label: String, colors: VsColors) {
    Text(label, color = colors.ink, fontFamily = WorkSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
}

@Composable
private fun SettingNote(text: String, colors: VsColors) {
    Text(
        text = text,
        color = colors.soft,
        fontFamily = WorkSansFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 1.45.em,
        modifier = Modifier.padding(top = 0.dp)
    )
}

@Composable
private fun OptionPill(label: String, selected: Boolean, colors: VsColors, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) colors.bg else colors.ink,
        fontFamily = WorkSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.ink else Color.Transparent)
            .border(1.dp, if (selected) colors.ink else colors.line, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
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
    highlightHex: (VerseRef) -> String?,
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
            val sub = when {
                previews == null -> "Finding the strongest links…"
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
                LinkItem(preview, colors, highlightHex(preview.link.ref), colorLabel) { onPick(preview) }
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
    highlight: String?,
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
            Text(
                text = content.label.uppercase(),
                color = colors.gold,
                fontFamily = WorkSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                letterSpacing = 0.1.em
            )
            if (highlight != null) {
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
