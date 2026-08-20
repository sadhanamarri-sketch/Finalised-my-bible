package com.example.mybible.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.LexiconDefinitionFormatter
import com.example.mybible.data.ScriptureRefResolver

private const val REF_ANNOTATION_TAG = "lexiconVerseRef"

/** Highlights bare Scripture-reference tokens (e.g. "Rom.6:19") in [refColor] — used for any text outside a "⟦key|display⟧" marker. */
private fun AnnotatedString.Builder.appendHighlighted(text: String, refColor: Color) {
    var last = 0
    for (match in LexiconDefinitionFormatter.SCRIPTURE_REF_RE.findAll(text)) {
        if (match.range.first > last) append(text.substring(last, match.range.first))
        withStyle(SpanStyle(color = refColor, fontWeight = FontWeight.Medium)) {
            append(match.value)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

// Matches the "⟦key|display⟧" markers TbesgImporter/TbeshImporter wrap
// Scripture citations in (see TbesgImporter's REF_TAG_RE doc) — U+27E6/
// U+27E7 brackets, verified absent from the source lexicon text itself.
private val REF_MARKER_RE = Regex("⟦([^|⟧]*)\\|([^⟧]*)⟧")

/**
 * Builds an [AnnotatedString] where "⟦key|display⟧" markers become their
 * display text, underlined and carrying a [REF_ANNOTATION_TAG] annotation
 * when [ScriptureRefResolver] can resolve the key to a navigable verse
 * (most citations — see its doc for the residue that can't be, like
 * Apocryphal references this KJV-only app has no text for), and any bare
 * reference-shaped text outside markers is still just highlighted, not
 * clickable, exactly as before.
 */
private fun buildRefAnnotatedString(text: String, refColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in REF_MARKER_RE.findAll(text)) {
        if (match.range.first > last) appendHighlighted(text.substring(last, match.range.first), refColor)
        val key = match.groupValues[1]
        val display = match.groupValues[2]
        val resolved = ScriptureRefResolver.resolve(key)
        val start = length
        withStyle(
            SpanStyle(
                color = refColor,
                fontWeight = FontWeight.Medium,
                textDecoration = if (resolved != null) TextDecoration.Underline else null
            )
        ) {
            append(display)
        }
        if (resolved != null) {
            addStringAnnotation(REF_ANNOTATION_TAG, "${resolved.book}|${resolved.chapter}|${resolved.verse}", start, length)
        }
        last = match.range.last + 1
    }
    if (last < text.length) appendHighlighted(text.substring(last), refColor)
}

@Composable
private fun RefText(
    text: String,
    style: TextStyle,
    refColor: Color,
    modifier: Modifier = Modifier,
    onReferenceClick: (book: String, chapter: Int, verse: Int) -> Unit = { _, _, _ -> }
) {
    val annotated = remember(text, refColor) { buildRefAnnotatedString(text, refColor) }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            val annotation = annotated.getStringAnnotations(REF_ANNOTATION_TAG, offset, offset).firstOrNull() ?: return@ClickableText
            val parts = annotation.item.split("|")
            val chapter = parts.getOrNull(1)?.toIntOrNull()
            val verse = parts.getOrNull(2)?.toIntOrNull()
            if (chapter != null && verse != null) onReferenceClick(parts[0], chapter, verse)
        }
    )
}

/**
 * Renders a TBESG/TBESH definition with the visual hierarchy its
 * <BR>-separated source lines imply, instead of one dense paragraph: a
 * leading principal-parts heading and bracketed etymology/LXX note (the
 * "scholarly" apparatus — Greek/Hebrew grammatical form, source-language
 * cross-references) are collapsed behind a "Show scholarly details" toggle
 * so the plain-English definition is the first thing a reader sees; below
 * that, Roman-numeral major divisions group numbered senses (for the
 * minority of richer entries that have them, e.g. λόγος's I/II/III), each
 * numbered sense gets its own block with a bold sense number, lettered
 * sub-senses indent underneath, the closing "SYN.:" list gets a small
 * label, and Scripture references are picked out in the accent color and
 * tappable throughout.
 */
@Composable
fun LexiconDefinitionText(
    definition: String,
    modifier: Modifier = Modifier,
    onReferenceClick: (book: String, chapter: Int, verse: Int) -> Unit = { _, _, _ -> }
) {
    val lines = remember(definition) { LexiconDefinitionFormatter.parse(definition) }
    // The scholarly apparatus is always the leading run of Heading/Note
    // lines (verified against real TBESG/TBESH entries — the principal
    // parts always come first, the bracketed LXX/etymology note, if any,
    // right after); once the first Sense/MajorDivision/Plain line appears,
    // everything from there on is the actual definition.
    val preambleCount = remember(lines) {
        lines.indexOfFirst { it !is LexiconDefinitionFormatter.Line.Heading && it !is LexiconDefinitionFormatter.Line.Note }
            .let { if (it == -1) lines.size else it }
    }
    var scholarlyDetailsExpanded by remember(definition) { mutableStateOf(false) }
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val refColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(modifier = modifier) {
        if (preambleCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scholarlyDetailsExpanded = !scholarlyDetailsExpanded }
                    .padding(bottom = if (scholarlyDetailsExpanded) 10.dp else 4.dp)
            ) {
                Text(
                    text = if (scholarlyDetailsExpanded) "Hide scholarly details" else "Show scholarly details",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = refColor
                )
                Icon(
                    imageVector = if (scholarlyDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = refColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        lines.forEachIndexed { idx, line ->
            if (idx < preambleCount && !scholarlyDetailsExpanded) return@forEachIndexed
            when (line) {
                is LexiconDefinitionFormatter.Line.Heading -> {
                    Text(
                        text = line.text,
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        color = bodyColor,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                is LexiconDefinitionFormatter.Line.Note -> {
                    RefText(
                        text = line.text,
                        style = TextStyle(
                            fontSize = 14.5.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 21.sp,
                            color = bodyColor.copy(alpha = 0.85f)
                        ),
                        refColor = refColor,
                        onReferenceClick = onReferenceClick
                    )
                    Spacer(Modifier.height(12.dp))
                }

                is LexiconDefinitionFormatter.Line.MajorDivision -> {
                    Column(modifier = Modifier.padding(top = if (idx == preambleCount) 0.dp else 18.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text(
                                text = line.numeral,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 7.dp)
                            )
                            RefText(
                                text = line.body,
                                style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, color = bodyColor),
                                refColor = refColor,
                                onReferenceClick = onReferenceClick
                            )
                        }
                    }
                }

                is LexiconDefinitionFormatter.Line.Sense -> {
                    Row(modifier = Modifier.padding(top = if (idx == 0) 0.dp else 12.dp)) {
                        Text(
                            text = "${line.number}.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 7.dp)
                        )
                        RefText(
                            text = line.body,
                            style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = bodyColor),
                            refColor = refColor,
                            onReferenceClick = onReferenceClick
                        )
                    }
                }

                is LexiconDefinitionFormatter.Line.SubSense -> {
                    Row(modifier = Modifier.padding(start = 22.dp, top = 7.dp)) {
                        Text(
                            text = "(${line.label})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bodyColor,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        RefText(
                            text = line.body,
                            style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = bodyColor),
                            refColor = refColor,
                            onReferenceClick = onReferenceClick
                        )
                    }
                }

                is LexiconDefinitionFormatter.Line.Synonyms -> {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "SYNONYMS",
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor
                        )
                        Spacer(Modifier.height(4.dp))
                        RefText(
                            text = line.body,
                            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = bodyColor.copy(alpha = 0.85f)),
                            refColor = refColor,
                            onReferenceClick = onReferenceClick
                        )
                    }
                }

                is LexiconDefinitionFormatter.Line.Plain -> {
                    if (idx > 0) Spacer(Modifier.height(8.dp))
                    RefText(
                        text = line.text,
                        style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = bodyColor),
                        refColor = refColor,
                        onReferenceClick = onReferenceClick
                    )
                }
            }
        }
    }
}
