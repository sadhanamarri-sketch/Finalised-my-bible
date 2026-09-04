package com.example.mybible.model

// Pure range-manipulation helpers behind the rich text editor (see
// ui/richtext/RichTextEditor.kt for where these get called from). Kept
// Compose-free and separate from RichText.kt's data classes so the actual
// editing logic — toggling a style over a selection, reindexing existing
// styled ranges after a keystroke, splitting a block's styling in half on
// Enter — is plain, testable Kotlin.
//
// Every category (bold, italic, underline, highlight) is stored as its own
// list of non-overlapping, half-open [start,end) StyleRanges. Keeping them
// non-overlapping is an invariant these functions maintain, not something
// the caller has to think about — toggle/reindex/split always return a
// clean, merged, non-overlapping list.

internal fun isRangeFullyCovered(ranges: List<StyleRange>, start: Int, end: Int): Boolean {
    if (start >= end) return false
    var covered = 0
    for (r in ranges) {
        val s = maxOf(r.start, start)
        val e = minOf(r.end, end)
        if (s < e) covered += (e - s)
    }
    return covered >= (end - start)
}

internal fun mergeStyleRanges(ranges: List<StyleRange>): List<StyleRange> {
    val sorted = ranges.filter { it.end > it.start }.sortedBy { it.start }
    if (sorted.isEmpty()) return emptyList()
    val result = mutableListOf(sorted[0])
    for (i in 1 until sorted.size) {
        val last = result.last()
        val cur = sorted[i]
        if (cur.start <= last.end) {
            result[result.size - 1] = StyleRange(last.start, maxOf(last.end, cur.end))
        } else {
            result.add(cur)
        }
    }
    return result
}

// Toggles a boolean style (bold/italic/underline/highlight) over
// [start,end): turns it on everywhere in that span if any part of it is
// currently unstyled, or off everywhere in that span if the whole span is
// already styled — the same "select then click Bold" behavior as any word
// processor.
internal fun toggleStyleRange(ranges: List<StyleRange>, start: Int, end: Int): List<StyleRange> {
    if (start >= end) return ranges
    val turningOn = !isRangeFullyCovered(ranges, start, end)
    val rebuilt = mutableListOf<StyleRange>()
    for (r in ranges) {
        if (r.end <= start || r.start >= end) {
            rebuilt.add(r)
            continue
        }
        if (r.start < start) rebuilt.add(StyleRange(r.start, start))
        if (r.end > end) rebuilt.add(StyleRange(end, r.end))
    }
    if (turningOn) rebuilt.add(StyleRange(start, end))
    return mergeStyleRanges(rebuilt)
}

internal fun mergeScaleRanges(ranges: List<ScaleRange>): List<ScaleRange> {
    val sorted = ranges.filter { it.end > it.start }.sortedBy { it.start }
    if (sorted.isEmpty()) return emptyList()
    val result = mutableListOf(sorted[0])
    for (i in 1 until sorted.size) {
        val last = result.last()
        val cur = sorted[i]
        if (cur.start <= last.end && cur.scale == last.scale) {
            result[result.size - 1] = ScaleRange(last.start, maxOf(last.end, cur.end), last.scale)
        } else {
            result.add(cur)
        }
    }
    return result
}

// Font size isn't a toggle — it's a step through a fixed scale (see
// RichTextEditor's FONT_SCALE_STEPS) — so this always sets the given scale
// explicitly over [start,end), replacing whatever was there. A scale of 1f
// (the default/no-op size) is never stored: setting back to 1f just clears
// that region instead of leaving a redundant explicit entry.
internal fun setScaleRange(ranges: List<ScaleRange>, start: Int, end: Int, newScale: Float): List<ScaleRange> {
    if (start >= end) return ranges
    val rebuilt = mutableListOf<ScaleRange>()
    for (r in ranges) {
        if (r.end <= start || r.start >= end) {
            rebuilt.add(r)
            continue
        }
        if (r.start < start) rebuilt.add(ScaleRange(r.start, start, r.scale))
        if (r.end > end) rebuilt.add(ScaleRange(end, r.end, r.scale))
    }
    if (newScale != 1f) rebuilt.add(ScaleRange(start, end, newScale))
    return mergeScaleRanges(rebuilt)
}

// The scale to step up/down from when the toolbar's font-size buttons are
// tapped: the scale at the selection start, or at the character just before
// a collapsed cursor (matching "what would typing here continue").
internal fun dominantScale(ranges: List<ScaleRange>, start: Int, end: Int): Float {
    val probe = if (start < end) start else maxOf(0, start - 1)
    return ranges.firstOrNull { probe >= it.start && probe < it.end }?.scale ?: 1f
}

// Reindexes a category's ranges after replacing [deleteStart,deleteEnd) of
// the OLD text with insertLength characters of new text — the general form
// of every edit BasicTextField reports (a plain keystroke is just the case
// deleteStart==deleteEnd, or deleteEnd==deleteStart+1). A range's portion
// inside the edited span is dropped; what's on either side keeps its style
// and shifts to match the new text length. When extendIfAbuts is true and
// the insertion point falls inside or right at the edge of an existing
// range, the newly typed/pasted text is folded into that same range — so
// continuing to type after (or in the middle of) bold text stays bold,
// rather than leaving an unstyled gap.
internal fun reindexStyleRanges(
    ranges: List<StyleRange>,
    deleteStart: Int,
    deleteEnd: Int,
    insertLength: Int,
    extendIfAbuts: Boolean
): List<StyleRange> {
    val delta = insertLength - (deleteEnd - deleteStart)
    val rebuilt = mutableListOf<StyleRange>()
    for (r in ranges) {
        if (r.start < deleteStart) {
            val beforeEnd = minOf(r.end, deleteStart)
            if (beforeEnd > r.start) rebuilt.add(StyleRange(r.start, beforeEnd))
        }
        if (r.end > deleteEnd) {
            val afterStart = maxOf(r.start, deleteEnd)
            if (r.end > afterStart) rebuilt.add(StyleRange(afterStart + delta, r.end + delta))
        }
    }
    if (extendIfAbuts && insertLength > 0) {
        val touching = ranges.any { it.start <= deleteStart && deleteStart <= it.end }
        if (touching) rebuilt.add(StyleRange(deleteStart, deleteStart + insertLength))
    }
    return mergeStyleRanges(rebuilt)
}

internal fun reindexScaleRanges(
    ranges: List<ScaleRange>,
    deleteStart: Int,
    deleteEnd: Int,
    insertLength: Int,
    extendIfAbuts: Boolean
): List<ScaleRange> {
    val delta = insertLength - (deleteEnd - deleteStart)
    val rebuilt = mutableListOf<ScaleRange>()
    for (r in ranges) {
        if (r.start < deleteStart) {
            val beforeEnd = minOf(r.end, deleteStart)
            if (beforeEnd > r.start) rebuilt.add(ScaleRange(r.start, beforeEnd, r.scale))
        }
        if (r.end > deleteEnd) {
            val afterStart = maxOf(r.start, deleteEnd)
            if (r.end > afterStart) rebuilt.add(ScaleRange(afterStart + delta, r.end + delta, r.scale))
        }
    }
    if (extendIfAbuts && insertLength > 0) {
        val touching = ranges.firstOrNull { it.start <= deleteStart && deleteStart <= it.end }
        if (touching != null) rebuilt.add(ScaleRange(deleteStart, deleteStart + insertLength, touching.scale))
    }
    return mergeScaleRanges(rebuilt)
}

// Splits a category's ranges at `at`, for breaking one block into two on
// Enter — everything before stays with the left block untouched, everything
// after is re-based to start from 0 for the new right-hand block, and a
// range straddling the split point is cut in two so both halves keep the
// style.
internal fun splitStyleRangesAt(ranges: List<StyleRange>, at: Int): Pair<List<StyleRange>, List<StyleRange>> {
    val left = mutableListOf<StyleRange>()
    val right = mutableListOf<StyleRange>()
    for (r in ranges) {
        when {
            r.end <= at -> left.add(r)
            r.start >= at -> right.add(StyleRange(r.start - at, r.end - at))
            else -> {
                left.add(StyleRange(r.start, at))
                right.add(StyleRange(0, r.end - at))
            }
        }
    }
    return left to right
}

internal fun splitScaleRangesAt(ranges: List<ScaleRange>, at: Int): Pair<List<ScaleRange>, List<ScaleRange>> {
    val left = mutableListOf<ScaleRange>()
    val right = mutableListOf<ScaleRange>()
    for (r in ranges) {
        when {
            r.end <= at -> left.add(r)
            r.start >= at -> right.add(ScaleRange(r.start - at, r.end - at, r.scale))
            else -> {
                left.add(StyleRange(r.start, at).let { ScaleRange(it.start, it.end, r.scale) })
                right.add(ScaleRange(0, r.end - at, r.scale))
            }
        }
    }
    return left to right
}

internal data class TextDiff(val deleteStart: Int, val deleteEnd: Int, val insertText: String)

// The common-prefix/common-suffix diff behind every reindex call above.
// Covers a single keystroke, backspace/delete, and IME
// autocorrect/predictive-text replacements — anything that isn't a single
// contiguous replacement (e.g. two separate edits landing in one callback)
// degrades to treating the whole string as replaced, which is still
// correct, just loses style continuity for that one edit.
internal fun computeTextDiff(old: String, new: String): TextDiff {
    if (old == new) return TextDiff(0, 0, "")
    val maxPrefix = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++
    var oldEnd = old.length
    var newEnd = new.length
    while (oldEnd > prefix && newEnd > prefix && old[oldEnd - 1] == new[newEnd - 1]) {
        oldEnd--
        newEnd--
    }
    return TextDiff(prefix, oldEnd, new.substring(prefix, newEnd))
}
