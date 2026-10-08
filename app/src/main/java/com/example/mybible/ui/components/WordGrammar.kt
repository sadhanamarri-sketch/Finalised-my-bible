package com.example.mybible.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.GrammarTerm
import com.example.mybible.data.HebrewWordPart
import com.example.mybible.ui.theme.LiterataFontFamily
import com.example.mybible.ui.theme.WorkSansFontFamily

/**
 * A word's grammar as a row of terms: Verb · Aorist · Active · Indicative · 3rd person singular.
 * Tapping a term says what it means under the row; tapping it again, or another, changes that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrammarTermsRow(terms: List<GrammarTerm>, modifier: Modifier = Modifier) {
    var open by remember(terms) { mutableStateOf<Int?>(null) }
    Column(modifier = modifier.animateContentSize()) {
        // No space between rows: each chip's touch target is taller than the chip.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            terms.forEachIndexed { i, term ->
                GrammarTermChip(
                    text = term.label,
                    selected = open == i,
                    onClick = term.explanation?.let { { open = if (open == i) null else i } }
                )
            }
        }
        open?.let(terms::getOrNull)?.let { term -> GrammarExplanation(term, Modifier.padding(top = 8.dp)) }
    }
}

/** What [term] means, under the term tapped: "Aorist: An action seen as a whole…". */
@Composable
fun GrammarExplanation(term: GrammarTerm, modifier: Modifier = Modifier) {
    val explanation = term.explanation ?: return
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(term.label) }
            append(": ")
            append(explanation)
        },
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontFamily = WorkSansFontFamily,
        letterSpacing = 0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** One grammar term, a tap away from what it means; [onClick] null when there's nothing to say. */
@Composable
fun GrammarTermChip(text: String, selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, hint: String? = null) {
    val shape = RoundedCornerShape(6.dp)
    val color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = BorderStroke(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
    val label: @Composable () -> Unit = {
        Text(
            text = buildAnnotatedString {
                append(text)
                if (hint != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · $hint") }
                }
            },
            fontSize = 13.sp,
            fontFamily = WorkSansFontFamily,
            fontWeight = if (hint != null) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
    if (onClick == null) {
        Surface(shape = shape, color = color, border = border, modifier = modifier, content = label)
    } else {
        Surface(onClick = onClick, shape = shape, color = color, border = border, modifier = modifier, content = label)
    }
}

/**
 * A Hebrew word in its parts, as the interlinear marks them: וַ "and" (a conjunction), then
 * יֹּאמֶר "he said" (a verb: Qal, narrative past, he). The word itself stands out from its
 * prefixes and endings.
 */
@Composable
fun HebrewWordParts(parts: List<HebrewWordPart>, modifier: Modifier = Modifier) {
    // The system serif, as the Reader's interlinear words use: it has Hebrew.
    val letters = TextStyle(fontSize = 24.sp, fontFamily = FontFamily.Serif)
    // The parts' letters in one column, as wide as the widest, so their glosses line up.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val column = remember(parts, density) {
        with(density) { parts.maxOf { measurer.measure(it.hebrew, letters).size.width }.toDp() }.coerceIn(40.dp, 140.dp)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (part in parts) {
            Row {
                Text(
                    text = part.hebrew,
                    style = letters,
                    textAlign = TextAlign.End,
                    color = if (part.main) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .width(column)
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (part.gloss.isNotBlank()) {
                        Text(
                            text = part.gloss,
                            fontSize = 15.sp,
                            fontFamily = LiterataFontFamily,
                            fontWeight = if (part.main) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    GrammarTermsRow(part.grammar)
                }
            }
        }
    }
}
