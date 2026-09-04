package com.example.mybible.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.NoteDocument
import com.example.mybible.model.RichAlign
import com.example.mybible.model.RichBlock
import com.example.mybible.model.RichBlockStyle
import com.example.mybible.model.ScaleRange
import com.example.mybible.model.StyleRange
import com.example.mybible.model.ThemeMode
import com.example.mybible.model.TextDiff
import com.example.mybible.model.computeTextDiff
import com.example.mybible.model.dominantScale
import com.example.mybible.model.isRangeFullyCovered
import com.example.mybible.model.mergeScaleRanges
import com.example.mybible.model.mergeStyleRanges
import com.example.mybible.model.reindexScaleRanges
import com.example.mybible.model.reindexStyleRanges
import com.example.mybible.model.setScaleRange
import com.example.mybible.model.splitScaleRangesAt
import com.example.mybible.model.splitStyleRangesAt
import com.example.mybible.model.toggleStyleRange
import com.example.mybible.ui.theme.NotoSerifFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

// Per-block editable state: a "state holder" object (each field its own
// mutableStateOf) rather than an immutable data class swapped out wholesale
// on every keystroke, so Compose only recomposes what actually reads a
// changed field. `id` is a client-side-only identity (never serialized) so
// FocusRequesters and recomposition keys stay stable across the inserts/
// removes/merges editing produces — see RichDocumentHolder below.
internal class EditableBlock(val id: Long, initial: RichBlock) {
    var style by mutableStateOf(initial.style)
    var align by mutableStateOf(initial.align)
    var indent by mutableStateOf(initial.indent)
    var bold by mutableStateOf(initial.bold)
    var italic by mutableStateOf(initial.italic)
    var underline by mutableStateOf(initial.underline)
    var highlight by mutableStateOf(initial.highlight)
    var fontScale by mutableStateOf(initial.fontScale)
    var fieldValue by mutableStateOf(TextFieldValue(initial.text))
    // Non-null for exactly one composition after this block is created or
    // touched by a split/merge — its row consumes this via LaunchedEffect to
    // grab focus and place the cursor, then clears it.
    var pendingFocusCursor by mutableStateOf<Int?>(null)
    val focusRequester = FocusRequester()

    fun toRichBlock(): RichBlock = RichBlock(
        text = fieldValue.text,
        style = style,
        align = align,
        indent = indent,
        bold = bold,
        italic = italic,
        underline = underline,
        highlight = highlight,
        fontScale = fontScale
    )
}

private var nextEditableBlockId = 0L

private fun RichBlock.toEditable(): EditableBlock = EditableBlock(nextEditableBlockId++, this)

// Owns the live list of blocks for one editing session — created once (via
// remember) from the NoteDocument the editor opened with. snapshot() reads
// the current state back out when the user taps Done.
internal class RichDocumentHolder(initial: NoteDocument) {
    val blocks = mutableStateListOf<EditableBlock>().apply {
        addAll(initial.blocks.ifEmpty { listOf(RichBlock()) }.map { it.toEditable() })
    }

    fun snapshot(): NoteDocument = NoteDocument(blocks.map { it.toRichBlock() })
}

private fun isListStyle(style: RichBlockStyle) =
    style == RichBlockStyle.BULLET_ITEM || style == RichBlockStyle.NUMBERED_ITEM

private fun numberedItemPosition(blocks: List<EditableBlock>, index: Int): Int {
    var n = 1
    var i = index - 1
    while (i >= 0 && blocks[i].style == RichBlockStyle.NUMBERED_ITEM) {
        n++
        i--
    }
    return n
}

private fun shiftStyleRanges(ranges: List<StyleRange>, by: Int) =
    ranges.map { StyleRange(it.start + by, it.end + by) }

private fun shiftScaleRanges(ranges: List<ScaleRange>, by: Int) =
    ranges.map { ScaleRange(it.start + by, it.end + by, it.scale) }

// Applies a plain (no-newline) text edit reported by one block's
// BasicTextField: reindexes every style category over the same
// [deleteStart,deleteEnd)->insertText edit the text itself just went
// through, so existing formatting survives typing/deleting around it, and
// freshly typed text continues whatever style immediately surrounds the
// insertion point (see reindexStyleRanges' extendIfAbuts).
private fun applyPlainEdit(block: EditableBlock, diff: TextDiff, newValue: TextFieldValue) {
    val extend = diff.insertText.isNotEmpty()
    block.bold = reindexStyleRanges(block.bold, diff.deleteStart, diff.deleteEnd, diff.insertText.length, extend)
    block.italic = reindexStyleRanges(block.italic, diff.deleteStart, diff.deleteEnd, diff.insertText.length, extend)
    block.underline = reindexStyleRanges(block.underline, diff.deleteStart, diff.deleteEnd, diff.insertText.length, extend)
    block.highlight = reindexStyleRanges(block.highlight, diff.deleteStart, diff.deleteEnd, diff.insertText.length, extend)
    block.fontScale = reindexScaleRanges(block.fontScale, diff.deleteStart, diff.deleteEnd, diff.insertText.length, extend)
    block.fieldValue = TextFieldValue(newValue.text, newValue.selection, newValue.composition)
}

// Handles a newline landing in a block's text — from pressing Enter (the
// overwhelmingly common case: a single "\n", nothing else) or from pasting
// multi-line text. Splits the block's styling cleanly at the break instead
// of losing it, and a list item's style (bullet/numbered) continues into
// the new block the same way any word processor does — unless the item was
// empty, in which case Enter exits the list instead of adding another item,
// also matching the usual convention.
private fun applySplit(holder: RichDocumentHolder, blockIndex: Int, diff: TextDiff) {
    val block = holder.blocks[blockIndex]
    val oldText = block.fieldValue.text
    val textAfterDelete = oldText.removeRange(diff.deleteStart, diff.deleteEnd)
    val boldAD = reindexStyleRanges(block.bold, diff.deleteStart, diff.deleteEnd, 0, false)
    val italicAD = reindexStyleRanges(block.italic, diff.deleteStart, diff.deleteEnd, 0, false)
    val underlineAD = reindexStyleRanges(block.underline, diff.deleteStart, diff.deleteEnd, 0, false)
    val highlightAD = reindexStyleRanges(block.highlight, diff.deleteStart, diff.deleteEnd, 0, false)
    val fontScaleAD = reindexScaleRanges(block.fontScale, diff.deleteStart, diff.deleteEnd, 0, false)
    val splitPoint = diff.deleteStart
    val pieces = diff.insertText.split("\n")

    if (pieces.size == 2 && textAfterDelete.isEmpty() && isListStyle(block.style)) {
        block.style = RichBlockStyle.PARAGRAPH
        block.fieldValue = TextFieldValue("", TextRange(0))
        block.bold = emptyList(); block.italic = emptyList(); block.underline = emptyList()
        block.highlight = emptyList(); block.fontScale = emptyList()
        return
    }

    val (boldLeftBase, boldRightBase) = splitStyleRangesAt(boldAD, splitPoint)
    val (italicLeftBase, italicRightBase) = splitStyleRangesAt(italicAD, splitPoint)
    val (underlineLeftBase, underlineRightBase) = splitStyleRangesAt(underlineAD, splitPoint)
    val (highlightLeftBase, highlightRightBase) = splitStyleRangesAt(highlightAD, splitPoint)
    val (fontScaleLeftBase, fontScaleRightBase) = splitScaleRangesAt(fontScaleAD, splitPoint)
    val headText = textAfterDelete.substring(0, splitPoint)
    val tailText = textAfterDelete.substring(splitPoint)

    val firstPiece = pieces.first()
    block.bold = reindexStyleRanges(boldLeftBase, splitPoint, splitPoint, firstPiece.length, true)
    block.italic = reindexStyleRanges(italicLeftBase, splitPoint, splitPoint, firstPiece.length, true)
    block.underline = reindexStyleRanges(underlineLeftBase, splitPoint, splitPoint, firstPiece.length, true)
    block.highlight = reindexStyleRanges(highlightLeftBase, splitPoint, splitPoint, firstPiece.length, true)
    block.fontScale = reindexScaleRanges(fontScaleLeftBase, splitPoint, splitPoint, firstPiece.length, true)
    val newHeadText = headText + firstPiece
    block.fieldValue = TextFieldValue(newHeadText, TextRange(newHeadText.length))

    val continuedStyle = if (isListStyle(block.style)) block.style else RichBlockStyle.PARAGRAPH
    val middlePieces = pieces.subList(1, pieces.size - 1)
    val newBlocks = mutableListOf<EditableBlock>()
    middlePieces.forEach { piece ->
        val mid = EditableBlock(
            nextEditableBlockId++,
            RichBlock(text = piece, style = continuedStyle, align = block.align, indent = block.indent)
        )
        mid.fieldValue = TextFieldValue(piece, TextRange(piece.length))
        newBlocks.add(mid)
    }

    val lastPiece = pieces.last()
    val lastText = lastPiece + tailText
    val lastBlock = EditableBlock(
        nextEditableBlockId++,
        RichBlock(
            text = lastText,
            style = continuedStyle,
            align = block.align,
            indent = block.indent,
            bold = shiftStyleRanges(boldRightBase, lastPiece.length),
            italic = shiftStyleRanges(italicRightBase, lastPiece.length),
            underline = shiftStyleRanges(underlineRightBase, lastPiece.length),
            highlight = shiftStyleRanges(highlightRightBase, lastPiece.length),
            fontScale = shiftScaleRanges(fontScaleRightBase, lastPiece.length)
        )
    )
    lastBlock.fieldValue = TextFieldValue(lastText, TextRange(lastPiece.length))
    newBlocks.add(lastBlock)

    holder.blocks.addAll(blockIndex + 1, newBlocks)
    newBlocks.last().pendingFocusCursor = lastPiece.length
}

private fun mergeWithPrevious(holder: RichDocumentHolder, index: Int) {
    if (index <= 0) return
    val block = holder.blocks[index]
    val prev = holder.blocks[index - 1]
    val offset = prev.fieldValue.text.length
    val mergedText = prev.fieldValue.text + block.fieldValue.text
    prev.bold = mergeStyleRanges(prev.bold + shiftStyleRanges(block.bold, offset))
    prev.italic = mergeStyleRanges(prev.italic + shiftStyleRanges(block.italic, offset))
    prev.underline = mergeStyleRanges(prev.underline + shiftStyleRanges(block.underline, offset))
    prev.highlight = mergeStyleRanges(prev.highlight + shiftStyleRanges(block.highlight, offset))
    prev.fontScale = mergeScaleRanges(prev.fontScale + shiftScaleRanges(block.fontScale, offset))
    prev.fieldValue = TextFieldValue(mergedText, TextRange(offset))
    holder.blocks.removeAt(index)
    prev.pendingFocusCursor = offset
}

// Full replacement for the note body's old single-BasicTextField writer:
// a formatting toolbar pinned above a scrollable column of per-block rows.
@Composable
internal fun RichNoteBodyEditor(
    holder: RichDocumentHolder,
    themeMode: ThemeMode,
    baseFontSizeSp: Float = 19f,
    modifier: Modifier = Modifier
) {
    var activeBlockId by remember { mutableStateOf(holder.blocks.first().id) }
    val accentColor = MaterialTheme.colorScheme.primary
    val (highlightBg, highlightInk) = highlightColors(themeMode)

    LaunchedEffect(Unit) {
        val first = holder.blocks.first()
        first.pendingFocusCursor = first.fieldValue.text.length
    }

    Column(modifier = modifier.fillMaxSize()) {
        FormattingToolbar(
            holder = holder,
            activeBlockId = activeBlockId,
            accentColor = accentColor,
            highlightBg = highlightBg,
            baseFontSizeSp = baseFontSizeSp,
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            holder.blocks.forEachIndexed { index, block ->
                key(block.id) {
                    BlockRow(
                        holder = holder,
                        block = block,
                        index = index,
                        accentColor = accentColor,
                        highlightBg = highlightBg,
                        highlightInk = highlightInk,
                        baseFontSizeSp = baseFontSizeSp,
                        onFocused = { activeBlockId = block.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    holder: RichDocumentHolder,
    block: EditableBlock,
    index: Int,
    accentColor: Color,
    highlightBg: Color,
    highlightInk: Color,
    baseFontSizeSp: Float,
    onFocused: () -> Unit
) {
    LaunchedEffect(block.pendingFocusCursor) {
        val pos = block.pendingFocusCursor ?: return@LaunchedEffect
        block.focusRequester.requestFocus()
        block.fieldValue = block.fieldValue.copy(selection = TextRange(pos.coerceIn(0, block.fieldValue.text.length)))
        block.pendingFocusCursor = null
    }

    val fontFamily = if (block.style == RichBlockStyle.HEADING_3) WorkSansFontFamily else NotoSerifFontFamily
    val fontWeight = when (block.style) {
        RichBlockStyle.HEADING_1, RichBlockStyle.HEADING_2 -> FontWeight.Bold
        RichBlockStyle.HEADING_3 -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val fontStyle = if (block.style == RichBlockStyle.BLOCKQUOTE) FontStyle.Italic else FontStyle.Normal
    val fontSizeSp = when (block.style) {
        RichBlockStyle.HEADING_1 -> baseFontSizeSp + 5f
        RichBlockStyle.HEADING_2 -> baseFontSizeSp + 2f
        RichBlockStyle.HEADING_3 -> (baseFontSizeSp - 6f).coerceAtLeast(11f)
        else -> baseFontSizeSp
    }
    val textColor = when (block.style) {
        RichBlockStyle.HEADING_1, RichBlockStyle.HEADING_2 -> accentColor
        RichBlockStyle.HEADING_3 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val letterSpacingSp = if (block.style == RichBlockStyle.HEADING_3) 1f else 0f
    val textAlign = when (block.align) {
        RichAlign.CENTER -> TextAlign.Center
        RichAlign.END -> TextAlign.End
        RichAlign.START -> TextAlign.Start
    }

    val displayValue = TextFieldValue(
        annotatedString = buildStyledAnnotatedString(
            block.fieldValue.text, block.bold, block.italic, block.underline,
            block.highlight, block.fontScale, fontSizeSp, highlightBg, highlightInk
        ),
        selection = block.fieldValue.selection
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (block.indent * 20).dp,
                bottom = if (block.style == RichBlockStyle.HEADING_1 || block.style == RichBlockStyle.HEADING_2) 10.dp else 6.dp
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (block.style == RichBlockStyle.BLOCKQUOTE) blockquoteBarModifier(accentColor) else Modifier)
        ) {
            when (block.style) {
                RichBlockStyle.BULLET_ITEM -> Text(
                    "•",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSizeSp.sp,
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                )
                RichBlockStyle.NUMBERED_ITEM -> Text(
                    "${numberedItemPosition(holder.blocks, index)}.",
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fontSizeSp.sp,
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                )
                else -> {}
            }
            BasicTextField(
                value = displayValue,
                onValueChange = { newValue ->
                    val diff = computeTextDiff(block.fieldValue.text, newValue.text)
                    if (diff.insertText.contains("\n")) {
                        val idx = holder.blocks.indexOfFirst { it.id == block.id }
                        if (idx >= 0) applySplit(holder, idx, diff)
                    } else {
                        applyPlainEdit(block, diff, newValue)
                    }
                },
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    fontSize = fontSizeSp.sp,
                    color = textColor,
                    textAlign = textAlign,
                    letterSpacing = letterSpacingSp.sp,
                    lineHeight = (fontSizeSp * 1.55f).sp
                ),
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(block.focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace &&
                            block.fieldValue.selection.collapsed && block.fieldValue.selection.start == 0
                        ) {
                            when {
                                isListStyle(block.style) -> {
                                    block.style = RichBlockStyle.PARAGRAPH
                                    true
                                }
                                block.indent > 0 -> {
                                    block.indent -= 1
                                    true
                                }
                                else -> {
                                    val idx = holder.blocks.indexOfFirst { it.id == block.id }
                                    if (idx > 0) {
                                        mergeWithPrevious(holder, idx)
                                        true
                                    } else false
                                }
                            }
                        } else false
                    }
            )
        }
    }
}

@Composable
private fun FormattingToolbar(
    holder: RichDocumentHolder,
    activeBlockId: Long,
    accentColor: Color,
    highlightBg: Color,
    baseFontSizeSp: Float,
    modifier: Modifier = Modifier
) {
    val active = holder.blocks.firstOrNull { it.id == activeBlockId } ?: holder.blocks.first()
    val sel = active.fieldValue.selection
    val hasSelection = !sel.collapsed

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarButton(
            label = "B",
            fontWeight = FontWeight.Bold,
            selected = hasSelection && isRangeFullyCovered(active.bold, sel.min, sel.max),
            enabled = hasSelection,
            accentColor = accentColor
        ) { active.bold = toggleStyleRange(active.bold, sel.min, sel.max) }
        Spacer(Modifier.width(6.dp))
        ToolbarButton(
            label = "I",
            fontStyle = FontStyle.Italic,
            selected = hasSelection && isRangeFullyCovered(active.italic, sel.min, sel.max),
            enabled = hasSelection,
            accentColor = accentColor
        ) { active.italic = toggleStyleRange(active.italic, sel.min, sel.max) }
        Spacer(Modifier.width(6.dp))
        ToolbarButton(
            label = "U",
            underline = true,
            selected = hasSelection && isRangeFullyCovered(active.underline, sel.min, sel.max),
            enabled = hasSelection,
            accentColor = accentColor
        ) { active.underline = toggleStyleRange(active.underline, sel.min, sel.max) }

        ToolbarDivider()

        FontSizeStepper(active, hasSelection, baseFontSizeSp)

        ToolbarDivider()

        val headingLabel = when (active.style) {
            RichBlockStyle.HEADING_1 -> "H1"
            RichBlockStyle.HEADING_2 -> "H2"
            RichBlockStyle.HEADING_3 -> "H3"
            else -> "H"
        }
        ToolbarButton(
            label = headingLabel,
            fontWeight = FontWeight.Bold,
            selected = active.style == RichBlockStyle.HEADING_1 || active.style == RichBlockStyle.HEADING_2 || active.style == RichBlockStyle.HEADING_3,
            accentColor = accentColor
        ) {
            active.style = when (active.style) {
                RichBlockStyle.PARAGRAPH -> RichBlockStyle.HEADING_1
                RichBlockStyle.HEADING_1 -> RichBlockStyle.HEADING_2
                RichBlockStyle.HEADING_2 -> RichBlockStyle.HEADING_3
                RichBlockStyle.HEADING_3 -> RichBlockStyle.PARAGRAPH
                else -> RichBlockStyle.HEADING_1
            }
        }
        Spacer(Modifier.width(6.dp))
        ToolbarButton(label = "⇥", accentColor = accentColor) {
            active.indent = (active.indent + 1).coerceAtMost(4)
        }
        Spacer(Modifier.width(6.dp))
        ToolbarButton(
            label = "Ctr",
            selected = active.align == RichAlign.CENTER,
            accentColor = accentColor
        ) {
            active.align = if (active.align == RichAlign.CENTER) RichAlign.START else RichAlign.CENTER
        }

        ToolbarDivider()

        ToolbarButton(
            label = "“",
            fontWeight = FontWeight.Bold,
            selected = active.style == RichBlockStyle.BLOCKQUOTE,
            accentColor = accentColor
        ) {
            active.style = if (active.style == RichBlockStyle.BLOCKQUOTE) RichBlockStyle.PARAGRAPH else RichBlockStyle.BLOCKQUOTE
        }
        Spacer(Modifier.width(6.dp))
        HighlightButton(
            selected = hasSelection && isRangeFullyCovered(active.highlight, sel.min, sel.max),
            enabled = hasSelection,
            highlightBg = highlightBg
        ) { active.highlight = toggleStyleRange(active.highlight, sel.min, sel.max) }
        Spacer(Modifier.width(6.dp))
        ToolbarButton(
            label = "•",
            fontWeight = FontWeight.Bold,
            selected = active.style == RichBlockStyle.BULLET_ITEM,
            accentColor = accentColor
        ) {
            active.style = if (active.style == RichBlockStyle.BULLET_ITEM) RichBlockStyle.PARAGRAPH else RichBlockStyle.BULLET_ITEM
        }
        Spacer(Modifier.width(6.dp))
        ToolbarButton(
            label = "1.",
            selected = active.style == RichBlockStyle.NUMBERED_ITEM,
            accentColor = accentColor
        ) {
            active.style = if (active.style == RichBlockStyle.NUMBERED_ITEM) RichBlockStyle.PARAGRAPH else RichBlockStyle.NUMBERED_ITEM
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    underline: Boolean = false,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HighlightButton(
    selected: Boolean,
    enabled: Boolean,
    highlightBg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) highlightBg.copy(alpha = 0.4f) else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("A", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .width(14.dp)
                    .height(3.dp)
                    .background(highlightBg, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun FontSizeStepper(active: EditableBlock, enabled: Boolean, baseFontSizeSp: Float) {
    val sel = active.fieldValue.selection
    val current = dominantScale(active.fontScale, sel.min, sel.max)
    val idx = FONT_SCALE_STEPS.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }.let { if (it < 0) 2 else it }
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton("−", enabled = enabled && idx > 0) {
            active.fontScale = setScaleRange(active.fontScale, sel.min, sel.max, FONT_SCALE_STEPS[idx - 1])
        }
        Text(
            text = "${(baseFontSizeSp * current).toInt()}",
            fontSize = 11.sp,
            modifier = Modifier.width(22.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StepperButton("+", enabled = enabled && idx < FONT_SCALE_STEPS.size - 1) {
            active.fontScale = setScaleRange(active.fontScale, sel.min, sel.max, FONT_SCALE_STEPS[idx + 1])
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 26.dp, height = 34.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
