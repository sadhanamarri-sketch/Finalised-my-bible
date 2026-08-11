package com.example.mybible.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * A single Bible-reference mention found in free text, e.g. "John 3:16" or
 * "1 Cor. 13:4-7". Ported verbatim from Capacitor's XREF_RE — same pattern,
 * same capture groups (optional leading book number, book name, chapter,
 * verse). The end-verse range (m[5] in Capacitor) is matched but, like
 * Capacitor's linkifyXrefs, not used for navigation — only the start verse
 * is followed.
 */
data class VerseMention(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val range: IntRange // character range in the original text
)

// Matches Capacitor's XREF_RE = /\b([1-3])?\s?([A-Za-z]{2,})\.?\s(\d{1,3}):(\d{1,3})(?:[-–](\d{1,3}))?/g
private val XREF_REGEX = Regex(
    "\\b([1-3])?\\s?([A-Za-z]{2,})\\.?\\s(\\d{1,3}):(\\d{1,3})(?:[-\u2013](\\d{1,3}))?"
)

/**
 * Scans [text] for Bible-reference-shaped substrings and resolves each
 * against the same book-alias table the note-reference field uses
 * ([resolveBookName]), so "1 Cor 13:4" and "1 Corinthians 13:4" both
 * resolve. Non-matches (unrecognized book names) are left as plain text,
 * exactly like Capacitor's linkifyXrefs falling through to escapeHtml(full)
 * when BOOK_ALIASES has no entry.
 */
fun findVerseMentions(text: String): List<VerseMention> {
    val results = mutableListOf<VerseMention>()
    for (match in XREF_REGEX.findAll(text)) {
        val num = match.groupValues[1]
        val name = match.groupValues[2]
        val chStr = match.groupValues[3]
        val vStr = match.groupValues[4]
        // Capacitor builds its lookup key as `(num||'') + name` before
        // normalizing — do the same here rather than trying name-alone as a
        // fallback, which could resolve "2 Sam" as "Samuel" (wrong) if a
        // name-only alias ever existed for it.
        val canonical = resolveBookName(num + name) ?: continue
        val chapter = chStr.toIntOrNull() ?: continue
        val verse = vStr.toIntOrNull() ?: continue
        results.add(VerseMention(canonical, chapter, verse, match.range))
    }
    return results
}

/**
 * Builds a Compose [AnnotatedString] with each recognized verse mention
 * styled as a link (primary color, underlined) and tagged with a clickable
 * annotation carrying "book|chapter|verse" — the Compose equivalent of
 * Capacitor's linkifyXrefs wrapping matches in
 * `<span class="xref-link" data-book=... data-chapter=... data-verse=...>`.
 * Callers pair this with ClickableText/pointerInput to read the annotation
 * and open the verse-preview sheet.
 */
const val VERSE_MENTION_TAG = "verse_mention"

fun buildLinkifiedNoteText(text: String): AnnotatedString {
    val mentions = findVerseMentions(text)
    if (mentions.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var lastIndex = 0
        for (mention in mentions) {
            append(text.substring(lastIndex, mention.range.first))
            val start = length
            append(text.substring(mention.range.first, mention.range.last + 1))
            addStyle(
                SpanStyle(fontWeight = FontWeight.SemiBold),
                start,
                length
            )
            addStringAnnotation(
                tag = VERSE_MENTION_TAG,
                annotation = "${mention.book}|${mention.chapter}|${mention.verse}",
                start = start,
                end = length
            )
            lastIndex = mention.range.last + 1
        }
        append(text.substring(lastIndex))
    }
}
