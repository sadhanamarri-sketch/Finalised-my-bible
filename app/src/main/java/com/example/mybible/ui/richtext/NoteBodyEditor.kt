package com.example.mybible.ui.richtext

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.model.CharStyle
import com.example.mybible.model.EditHistory
import com.example.mybible.model.MAX_INDENT
import com.example.mybible.model.NoteEditing
import com.example.mybible.model.NoteText
import com.example.mybible.model.ParaStyle
import com.example.mybible.model.RichAlign
import com.example.mybible.model.RichBlockStyle
import com.example.mybible.ui.theme.NotoSerifFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

/**
 * A note's text while it's written: the note, the cursor or selection, the word the keyboard is
 * still making (its composition, given back with every change so suggestions, autocorrection and
 * swipe typing work), the styles typing goes on in, and undo and redo. Kept with the note's draft
 * (see NoteDraft), so it lasts through turning the phone and a visit to the Reader to add a verse.
 */
@Stable
class NoteEditorState(initial: NoteText) {
    var note by mutableStateOf(initial)
        private set
    var selection by mutableStateOf(TextRange(initial.text.length))
        private set
    private var composition by mutableStateOf<TextRange?>(null)

    /** Styles for what's typed next at the cursor, when set from the toolbar; null: those of the text there. */
    var typing by mutableStateOf<Set<CharStyle>?>(null)
        private set

    /** Goes up with each change to the text or its look, for saving as it's written. */
    var version by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val history = EditHistory()
    val focusRequester = FocusRequester()

    val value: TextFieldValue get() = TextFieldValue(note.text, selection, composition)

    /** The paragraph the cursor is in (the first of a selection): its look. */
    val para: ParaStyle get() = note.paras[note.paraAt(selection.min)]

    /** The text box's change, [fixTap] placing a cursor put by a tap where the tap meant it. */
    internal fun change(v: TextFieldValue, fixTap: ((Int) -> Int)?) {
        var sel = v.selection
        if (v.text == note.text && sel.collapsed && fixTap != null) sel = TextRange(fixTap(sel.start))
        val e = NoteEditing.edit(note, selection.start, selection.end, v.text, sel.start, sel.end, typing)
        if (e.note != note) {
            val kind = if (e.note.text == note.text) EditHistory.Kind.OTHER else EditHistory.kindOf(note.text, e.note.text)
            history.record(EditHistory.State(note, selection.start, selection.end), kind, SystemClock.uptimeMillis())
            note = e.note
            version++
        }
        selection = TextRange(e.selStart, e.selEnd)
        composition = if (e.rewritten) null else v.composition
        typing = e.typing
        refreshHistory()
    }

    private fun format(f: (NoteText, Int, Int) -> NoteText) {
        val changed = f(note, selection.min, selection.max)
        if (changed == note) return
        history.record(EditHistory.State(note, selection.start, selection.end), EditHistory.Kind.OTHER, SystemClock.uptimeMillis())
        note = changed
        version++
        refreshHistory()
    }

    /** Whether [style] is on: over all of the selection, or at the cursor, for what's typed there. */
    fun isOn(style: CharStyle): Boolean =
        if (selection.collapsed) style in (typing ?: NoteEditing.stylesAt(note, selection.start))
        else NoteEditing.hasStyle(note, selection.min, selection.max, style)

    /** [style] on or off: over the selection, or at the cursor, for what's typed next. */
    fun toggle(style: CharStyle) {
        if (selection.collapsed) {
            val now = typing ?: NoteEditing.stylesAt(note, selection.start)
            typing = if (style in now) now - style else now + style
        } else {
            format { n, s, e -> NoteEditing.toggleStyle(n, s, e, style) }
        }
    }

    fun toggleParaStyle(style: RichBlockStyle) = format { n, s, e -> NoteEditing.toggleParaStyle(n, s, e, style) }
    fun setParaStyle(style: RichBlockStyle) = format { n, s, e -> NoteEditing.setParaStyle(n, s, e, style) }
    fun indent(by: Int) = format { n, s, e -> NoteEditing.indent(n, s, e, by) }
    fun align(align: RichAlign) = format { n, s, e -> NoteEditing.setAlign(n, s, e, align) }
    fun toggleChecked(para: Int) = format { n, _, _ -> NoteEditing.toggleChecked(n, para) }

    fun clearFormatting() {
        if (selection.collapsed) typing = emptySet()
        format { n, s, e -> NoteEditing.clearFormatting(n, s, e) }
    }

    /** Backspace with the cursor at the very start of the note: a list item there becomes plain. */
    fun backspaceAtStart(): Boolean {
        if (!selection.collapsed || selection.start != 0) return false
        val out = NoteEditing.backspaceAtStart(note) ?: return false
        format { _, _, _ -> out }
        return true
    }

    fun undo() {
        history.undo(EditHistory.State(note, selection.start, selection.end))?.let(::restore)
    }

    fun redo() {
        history.redo(EditHistory.State(note, selection.start, selection.end))?.let(::restore)
    }

    private fun restore(s: EditHistory.State) {
        note = s.note
        selection = TextRange(s.selStart.coerceIn(0, s.note.text.length), s.selEnd.coerceIn(0, s.note.text.length))
        composition = null
        typing = null
        version++
        refreshHistory()
    }

    fun moveCursorToEnd() {
        selection = TextRange(note.text.length)
        typing = null
    }

    private fun refreshHistory() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }
}

/**
 * The note's text in one text box, so a selection can cross paragraphs: list marks, tick boxes
 * and quote bars drawn in each paragraph's room on the left, a tap on a tick box ticking it,
 * capitals at the start of sentences, and on a physical keyboard Ctrl+B, I, U, Z, Y, Tab and
 * Shift+Tab. [placeholder] shows while it's empty.
 */
@Composable
fun NoteBodyField(
    state: NoteEditorState,
    look: NoteLook,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The last tap: where (its height) and when, to place the cursor it gives (see cursorForTap).
    val tapY = remember { floatArrayOf(Float.NaN) }
    val tapAt = remember { longArrayOf(0L) }
    val visual = remember(state.note, look) { NoteVisual(state.note, look) }
    Box(modifier) {
        if (state.note.text.isEmpty()) {
            Text(placeholder, style = noteTextStyle(look).copy(color = look.muted))
        }
        BasicTextField(
            value = state.value,
            onValueChange = { v ->
                val l = layout
                val y = tapY[0]
                val recent = SystemClock.uptimeMillis() - tapAt[0] < 700
                state.change(v, if (recent && !y.isNaN() && l != null) { o -> cursorForTap(state.note, l, y, o) } else null)
                tapY[0] = Float.NaN
            },
            textStyle = noteTextStyle(look),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text),
            visualTransformation = visual,
            cursorBrush = SolidColor(look.accent),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(state.focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .pointerInput(state, look) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        tapY[0] = down.position.y
                        tapAt[0] = SystemClock.uptimeMillis()
                        val l = layout ?: return@awaitEachGesture
                        val box = checkBoxAt(state.note, l, look, density, down.position) ?: return@awaitEachGesture
                        // A tick box: ticked or unticked, the cursor left where it was.
                        down.consume()
                        val up = waitForUpOrCancellation(PointerEventPass.Initial)
                        if (up != null) {
                            up.consume()
                            state.toggleChecked(box)
                        }
                    }
                }
                .onPreviewKeyEvent { keys(it, state) }
                .drawBehind { layout?.let { drawNoteMarks(state.note, it, look, measurer) } }
        )
    }
}

// A physical keyboard's shortcuts, and Backspace at the very start of the note.
private fun keys(e: KeyEvent, state: NoteEditorState): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    if (e.isCtrlPressed) {
        when (e.key) {
            Key.B -> state.toggle(CharStyle.BOLD)
            Key.I -> state.toggle(CharStyle.ITALIC)
            Key.U -> state.toggle(CharStyle.UNDERLINE)
            Key.Z -> if (e.isShiftPressed) state.redo() else state.undo()
            Key.Y -> state.redo()
            else -> return false
        }
        return true
    }
    return when (e.key) {
        Key.Tab -> {
            state.indent(if (e.isShiftPressed) -1 else 1)
            true
        }
        Key.Backspace -> state.backspaceAtStart()
        else -> false
    }
}

/**
 * The toolbar over the keyboard while the note's text is being written, in one row that fits a
 * phone: Aa (the text styles panel), bold, italic, highlight, bullets, checklist, Shift-Tab and
 * Tab, and, kept in view at the right, hide the keyboard. Each lights up for the style at the
 * cursor or over the selection, and tells TalkBack what it is and whether it's on.
 */
@Composable
fun NoteFormattingBar(
    state: NoteEditorState,
    look: NoteLook,
    onHideKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var panel by rememberSaveable { mutableStateOf(false) }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(color = line)
        if (panel) {
            TextStylesPanel(state)
            HorizontalDivider(color = line.copy(alpha = 0.5f))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            // As wide as nine fit, from 40dp to 48dp; on a narrower screen the left part scrolls.
            val slot = (maxWidth / 9).coerceIn(40.dp, 48.dp)
            val p = state.para
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolButton(Icons.Filled.TextFields, "Text styles", panel, slot) { panel = !panel }
                    ToolButton(Icons.Filled.FormatBold, "Bold", state.isOn(CharStyle.BOLD), slot) { state.toggle(CharStyle.BOLD) }
                    ToolButton(Icons.Filled.FormatItalic, "Italic", state.isOn(CharStyle.ITALIC), slot) { state.toggle(CharStyle.ITALIC) }
                    HighlightButton(state.isOn(CharStyle.HIGHLIGHT), look, slot) { state.toggle(CharStyle.HIGHLIGHT) }
                    ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bulleted list", p.style == RichBlockStyle.BULLET_ITEM, slot) {
                        state.toggleParaStyle(RichBlockStyle.BULLET_ITEM)
                    }
                    ToolButton(Icons.Outlined.CheckBox, "Checklist", p.style == RichBlockStyle.CHECK_ITEM, slot) {
                        state.toggleParaStyle(RichBlockStyle.CHECK_ITEM)
                    }
                    ToolButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Shift-Tab, indent less", null, slot, enabled = p.indent > 0) {
                        state.indent(-1)
                    }
                    ToolButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Tab, indent more", null, slot, enabled = p.indent < MAX_INDENT) {
                        state.indent(1)
                    }
                }
                Box(Modifier.width(1.dp).height(24.dp).background(line))
                ToolButton(Icons.Filled.KeyboardHide, "Hide keyboard", null, slot, onClick = onHideKeyboard)
            }
        }
    }
}

// Aa: the paragraph's kind (Title, Heading, Subheading, Text), underline, numbered list, quote,
// alignment and Clear formatting.
@Composable
private fun TextStylesPanel(state: NoteEditorState) {
    val p = state.para
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleChip("Title", p.style == RichBlockStyle.HEADING_1, 18.sp, FontWeight.Bold, NotoSerifFontFamily) {
                state.setParaStyle(RichBlockStyle.HEADING_1)
            }
            StyleChip("Heading", p.style == RichBlockStyle.HEADING_2, 16.sp, FontWeight.Bold, NotoSerifFontFamily) {
                state.setParaStyle(RichBlockStyle.HEADING_2)
            }
            StyleChip("Subheading", p.style == RichBlockStyle.HEADING_3, 15.sp, FontWeight.SemiBold, WorkSansFontFamily) {
                state.setParaStyle(RichBlockStyle.HEADING_3)
            }
            StyleChip("Text", p.style == RichBlockStyle.PARAGRAPH, 15.sp, FontWeight.Normal, NotoSerifFontFamily) {
                state.setParaStyle(RichBlockStyle.PARAGRAPH)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Icons.Filled.FormatUnderlined, "Underline", state.isOn(CharStyle.UNDERLINE)) { state.toggle(CharStyle.UNDERLINE) }
            ToolButton(Icons.Filled.FormatListNumbered, "Numbered list", p.style == RichBlockStyle.NUMBERED_ITEM) {
                state.toggleParaStyle(RichBlockStyle.NUMBERED_ITEM)
            }
            ToolButton(Icons.Filled.FormatQuote, "Quote", p.style == RichBlockStyle.BLOCKQUOTE) {
                state.toggleParaStyle(RichBlockStyle.BLOCKQUOTE)
            }
            ToolButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "Align left", p.align == RichAlign.START) { state.align(RichAlign.START) }
            ToolButton(Icons.Filled.FormatAlignCenter, "Centre", p.align == RichAlign.CENTER) { state.align(RichAlign.CENTER) }
            ToolButton(Icons.AutoMirrored.Filled.FormatAlignRight, "Align right", p.align == RichAlign.END) { state.align(RichAlign.END) }
            ToolButton(Icons.Filled.FormatClear, "Clear formatting", null) { state.clearFormatting() }
        }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, family: FontFamily, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() })
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = size, fontWeight = weight, fontFamily = family, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
    }
}

/** A toolbar button: [on] null for an action, true or false for a style (shown lit, and told to TalkBack). */
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    on: Boolean?,
    width: Dp = 44.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(10.dp)
    val action = if (on != null) {
        Modifier.toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    Box(
        Modifier
            .size(width, 44.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (on == true) accent.copy(alpha = 0.14f) else Color.Transparent)
            .then(action),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> ink.copy(alpha = 0.32f)
                on == true -> accent
                else -> ink
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

// The highlighter: an "A" over a bar of the highlight's own colour.
@Composable
private fun HighlightButton(on: Boolean, look: NoteLook, width: Dp, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(width, 44.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) accent.copy(alpha = 0.14f) else Color.Transparent)
            .semantics { contentDescription = "Highlight" }
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center
    ) {
        // Drawn, not said: TalkBack says "Highlight".
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("A", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (on) accent else MaterialTheme.colorScheme.onSurface)
            Box(Modifier.padding(top = 1.dp).width(16.dp).height(4.dp).background(look.highlightBg, RoundedCornerShape(1.dp)))
        }
    }
}
