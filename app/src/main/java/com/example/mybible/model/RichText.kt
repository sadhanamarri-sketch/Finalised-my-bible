package com.example.mybible.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// The structured format behind a note's rich-text body (NoteItem.richText).
// Deliberately flat rather than nested spans: each block is one paragraph
// (or heading/list-item/blockquote line) of plain text, plus separate
// half-open [start,end) range lists per character-level style. Ranges never
// overlap within their own category — see RichTextRanges.kt for the
// toggle/merge logic that keeps that invariant while editing. Rendering
// (both the live editor and the read-only note view) just layers each
// category's SpanStyle onto the text independently, so bold+highlight on
// the same run compose naturally without needing a combined "style" enum.
//
// No literal colors are stored here — a highlighted run, for instance, is
// just a set of character ranges; the actual highlight/accent colors come
// from whichever of the app's five themes is active when it's rendered, the
// same as every other themed surface in the app.
// CHECK_ITEM is a checklist line, ticked or not ([RichBlock.checked]).
enum class RichBlockStyle { PARAGRAPH, HEADING_1, HEADING_2, HEADING_3, BLOCKQUOTE, BULLET_ITEM, NUMBERED_ITEM, CHECK_ITEM }

enum class RichAlign { START, CENTER, END }

@Serializable
data class StyleRange(val start: Int, val end: Int)

@Serializable
data class ScaleRange(val start: Int, val end: Int, val scale: Float)

@Serializable
data class RichBlock(
    val text: String = "",
    val style: RichBlockStyle = RichBlockStyle.PARAGRAPH,
    val align: RichAlign = RichAlign.START,
    // Tab-indent level, 0 = none: how far a paragraph is indented, or how deep
    // a list or checklist item is nested (◦ under •, a. under 1.).
    val indent: Int = 0,
    val bold: List<StyleRange> = emptyList(),
    val italic: List<StyleRange> = emptyList(),
    val underline: List<StyleRange> = emptyList(),
    val highlight: List<StyleRange> = emptyList(),
    val fontScale: List<ScaleRange> = emptyList(),
    // A checklist item's tick (CHECK_ITEM only).
    val checked: Boolean = false
)

@Serializable
data class NoteDocument(val blocks: List<RichBlock> = listOf(RichBlock())) {
    fun toPlainText(): String = blocks.joinToString("\n") { it.text }

    fun toJson(): String = richTextJson.encodeToString(this)

    companion object {
        // A brand-new note (or any note whose richText hasn't been touched
        // by the rich editor yet) has no saved document — see fromPlainText.
        fun fromJsonOrNull(raw: String): NoteDocument? {
            if (raw.isBlank()) return null
            return try {
                richTextJson.decodeFromString<NoteDocument>(raw)
            } catch (e: Exception) {
                null
            }
        }

        // Lazily migrates a legacy plain-text note (or one saved by an app
        // build that predates rich text) into an unstyled document, one
        // paragraph block per line — so every existing note opens and edits
        // normally in the rich editor with no separate "old note" code path
        // and no batch migration on upgrade.
        fun fromPlainText(text: String): NoteDocument {
            val lines = if (text.isEmpty()) listOf("") else text.split("\n")
            return NoteDocument(lines.map { RichBlock(text = it) })
        }
    }
}

private val richTextJson = Json { ignoreUnknownKeys = true }
