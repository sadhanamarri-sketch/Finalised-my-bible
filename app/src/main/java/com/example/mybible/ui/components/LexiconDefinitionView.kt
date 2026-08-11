package com.example.mybible.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mybible.data.LexiconDefinitionFormatter

/** Highlights Scripture-reference tokens (e.g. "Rom.6:19") within [text] in [refColor]. */
private fun highlightRefs(text: String, refColor: Color): AnnotatedString = buildAnnotatedString {
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

@Composable
private fun RefText(text: String, style: TextStyle, refColor: Color, modifier: Modifier = Modifier) {
    Text(text = highlightRefs(text, refColor), style = style, modifier = modifier)
}

/**
 * Renders a TBESG/TBESH definition with the visual hierarchy its
 * <BR>-separated source lines imply, instead of one dense paragraph:
 * numbered senses get their own block with a bold sense number, lettered
 * sub-senses indent underneath, the bracketed etymology/LXX note renders
 * in italic, the closing "SYN.:" list gets a small label, and Scripture
 * references are picked out in the accent color throughout.
 */
@Composable
fun LexiconDefinitionText(definition: String, modifier: Modifier = Modifier) {
    val lines = remember(definition) { LexiconDefinitionFormatter.parse(definition) }
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val refColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(modifier = modifier) {
        lines.forEachIndexed { idx, line ->
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
                        refColor = refColor
                    )
                    Spacer(Modifier.height(12.dp))
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
                            refColor = refColor
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
                            refColor = refColor
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
                            refColor = refColor
                        )
                    }
                }

                is LexiconDefinitionFormatter.Line.Plain -> {
                    if (idx > 0) Spacer(Modifier.height(8.dp))
                    RefText(
                        text = line.text,
                        style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = bodyColor),
                        refColor = refColor
                    )
                }
            }
        }
    }
}
