package com.example.mybible.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small uppercase gold section header — Capacitor's ".ds-section-label"
 * (12.5px, letter-spacing 1.5px, uppercase, --gold, 22px top / 10px bottom
 * margin, no top margin on the very first one in a column).
 */
@Composable
fun DsSectionLabel(text: String, isFirst: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 12.5.sp,
        letterSpacing = 1.5.sp,
        fontFamily = FontFamily.SansSerif,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.padding(top = if (isFirst) 0.dp else 22.dp, bottom = 10.dp)
    )
}

/**
 * A label + optional description on the left, a pill switch on the right —
 * Capacitor's ".ds-toggle-row" (label: Georgia/serif 17px; sub: sans-serif
 * 13.5px, ink-soft).
 */
@Composable
fun DsToggleRow(
    label: String,
    subLabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        DsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Hand-built pill switch matching Capacitor's ".ds-switch" exactly: 46x28
 * rounded track, white 24dp thumb, off = outline/line color, on = the
 * theme's redletter/error tone (not the general accent).
 */
@Composable
fun DsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        label = "dsSwitchTrack"
    )
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White)
        )
    }
}

/**
 * Grouped "A ... value ... A" (or -/+ ) adjuster bar — Capacitor's
 * "#dsSizeRow" family: dim pill container, 44x44 buttons with the two
 * glyph sizes, centered sans-serif value label.
 */
@Composable
fun DsSizeAdjustRow(
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseGlyph: String = "A",
    increaseGlyph: String = "A",
    decreaseGlyphSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    increaseGlyphSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DsSizeBtn(glyph = decreaseGlyph, glyphSize = decreaseGlyphSize, onClick = onDecrease)
        Text(
            text = valueLabel,
            fontSize = 14.5.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        DsSizeBtn(glyph = increaseGlyph, glyphSize = increaseGlyphSize, onClick = onIncrease)
    }
}

@Composable
private fun DsSizeBtn(
    glyph: String,
    glyphSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            fontSize = glyphSize,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Full-width outline button in the accent color — Capacitor's
 * ".backup-action-btn" (1px accent border, 8dp radius, bold sans-serif).
 */
@Composable
fun DsOutlineAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = accent.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}

/**
 * Gold uppercase field label used throughout the Note editor — Capacitor's
 * ".ne-section-label" (12.5px, letter-spacing 1.5px, uppercase, --gold,
 * 18px top / 8px bottom margin). [optionalNote], when set, renders an
 * un-transformed "(optional)" suffix in ink-soft right after the label,
 * matching ".ne-optional-label".
 */
@Composable
fun NeSectionLabel(text: String, optionalNote: String? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Text(
            text = text.uppercase(),
            fontSize = 12.5.sp,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.tertiary
        )
        if (optionalNote != null) {
            Text(
                text = " $optionalNote",
                fontSize = 12.5.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Flat bordered single-line input — Capacitor's shared input look used by
 * ".title-input", ".date-input", "#neTagInput", etc: 1px line border, 8dp
 * radius, input-bg fill, no floating Material label (the label lives
 * above, via [NeSectionLabel]). [bold] matches ".title-input"'s
 * font-weight:600, used for the note title field specifically.
 */
@Composable
fun NeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction?.invoke() },
                    onNext = { onImeAction?.invoke() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailingContent()
        }
    }
}
