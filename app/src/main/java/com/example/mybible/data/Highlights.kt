package com.example.mybible.data

import com.example.mybible.model.HighlightItem

/**
 * A verse can be highlighted in more than one color — Promise and Prayer, say. Each color is its own
 * [HighlightItem] (with its own quick note and date), kept in the order the colors were added: the
 * first is shown on top. These are the rules for adding, taking off and merging them, kept apart from
 * where they're stored so they can be tested.
 */

/** The most colors one verse can be highlighted in. */
const val MAX_VERSE_COLORS = 3

private fun HighlightItem.isOn(book: String, chapter: Int, verse: Int) =
    this.book == book && this.chapter == chapter && this.verse == verse

private fun HighlightItem.isColor(hex: String) = colorHex.equals(hex, ignoreCase = true)

/** The verse's highlights, one per color, in the order they were added. */
fun List<HighlightItem>.onVerse(book: String, chapter: Int, verse: Int): List<HighlightItem> =
    filter { it.isOn(book, chapter, verse) }

/** Whether [colorHex] can go on the verse: it's one of its colors already, or there's room for another. */
fun List<HighlightItem>.hasRoomFor(book: String, chapter: Int, verse: Int, colorHex: String): Boolean {
    val colors = onVerse(book, chapter, verse)
    return colors.any { it.isColor(colorHex) } || colors.size < MAX_VERSE_COLORS
}

/**
 * [colorHex] added to the verse, after its other colors — or, already one of them, left where it is,
 * with [noteId] (a quick note) linked to it when given. Unchanged when the verse has no room for
 * another color.
 */
fun List<HighlightItem>.withColor(
    book: String,
    chapter: Int,
    verse: Int,
    colorHex: String,
    noteId: Long? = null,
    now: Long = System.currentTimeMillis()
): List<HighlightItem> {
    val existing = indexOfFirst { it.isOn(book, chapter, verse) && it.isColor(colorHex) }
    if (existing >= 0) {
        if (noteId == null) return this
        return toMutableList().also { it[existing] = it[existing].copy(noteId = noteId, updatedAt = now) }
    }
    return withItem(HighlightItem(book, chapter, verse, colorHex, updatedAt = now, noteId = noteId))
}

/** [item] put back as it was (an undo), after the verse's other colors: unless it's there, or there's no room. */
fun List<HighlightItem>.withItem(item: HighlightItem): List<HighlightItem> {
    val colors = onVerse(item.book, item.chapter, item.verse)
    if (colors.any { it.isColor(item.colorHex) } || colors.size >= MAX_VERSE_COLORS) return this
    return this + item
}

/** The verse without [colorHex] (its other colors stay). */
fun List<HighlightItem>.withoutColor(book: String, chapter: Int, verse: Int, colorHex: String): List<HighlightItem> =
    filterNot { it.isOn(book, chapter, verse) && it.isColor(colorHex) }

/** The verse with no highlight at all. */
fun List<HighlightItem>.withoutVerse(book: String, chapter: Int, verse: Int): List<HighlightItem> =
    filterNot { it.isOn(book, chapter, verse) }

/** The record that a verse's highlight was cleared (every color), for merging backups. */
fun highlightTombstoneKey(book: String, chapter: Int, verse: Int) = "highlight:$book:$chapter:$verse"

/** The record that one color was taken off a verse. */
fun highlightColorTombstoneKey(book: String, chapter: Int, verse: Int, colorHex: String) =
    "highlight:$book:$chapter:$verse:${colorHex.lowercase()}"

/**
 * A backup's highlights merged into [existing], color by color: one already here keeps whichever copy is
 * newer; one cleared or taken off since it was made ([tombstones]) stays off; and a verse that would end
 * up in more than [MAX_VERSE_COLORS] colors keeps its newest. Returns the merged list and how many
 * highlights the backup added.
 */
fun mergeHighlights(
    existing: List<HighlightItem>,
    incoming: List<HighlightItem>,
    tombstones: Map<String, Long>
): Pair<List<HighlightItem>, Int> {
    val merged = existing.toMutableList()
    var added = 0
    for (item in incoming) {
        val deletedAt = listOfNotNull(
            tombstones[highlightTombstoneKey(item.book, item.chapter, item.verse)],
            tombstones[highlightColorTombstoneKey(item.book, item.chapter, item.verse, item.colorHex)]
        ).maxOrNull()
        if (deletedAt != null && item.updatedAt <= deletedAt) continue
        val idx = merged.indexOfFirst { it.isOn(item.book, item.chapter, item.verse) && it.isColor(item.colorHex) }
        if (idx < 0) {
            merged.add(item)
            added++
        } else if (item.updatedAt >= merged[idx].updatedAt) {
            merged[idx] = item
        }
    }
    // Too many colors on a verse (a color added on each of two phones): its newest stay, in their order.
    val dropped = merged.indices
        .groupBy { merged[it].let { h -> Triple(h.book, h.chapter, h.verse) } }
        .values
        .filter { it.size > MAX_VERSE_COLORS }
        .flatMap { indices -> indices.sortedByDescending { merged[it].updatedAt }.drop(MAX_VERSE_COLORS) }
        .toSet()
    if (dropped.isEmpty()) return merged to added
    return merged.filterIndexed { i, _ -> i !in dropped } to (added - dropped.size).coerceAtLeast(0)
}
