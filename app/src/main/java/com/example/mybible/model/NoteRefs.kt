package com.example.mybible.model

/** Verses side by side in one chapter, shown as one: "John 15:1–8". */
data class RefRange(val book: String, val chapter: Int, val from: Int, val to: Int) {
    val label: String get() = if (from == to) "$book $chapter:$from" else "$book $chapter:$from–$to"

    fun covers(r: NoteReference): Boolean = r.book == book && r.chapter == chapter && r.verse in from..to
}

/**
 * A note's verses as the passages they make, in Bible order ([bookIndex] gives a book's place).
 * Each verse is still kept on its own, so the Reader marks each; verses side by side in a chapter
 * show as one.
 */
fun groupRefs(refs: List<NoteReference>, bookIndex: (String) -> Int): List<RefRange> {
    val sorted = refs.distinctBy { Triple(it.book, it.chapter, it.verse) }
        .sortedWith(compareBy<NoteReference>({ bookIndex(it.book) }, { it.book }, { it.chapter }, { it.verse }))
    val out = mutableListOf<RefRange>()
    for (r in sorted) {
        val last = out.lastOrNull()
        if (last != null && last.book == r.book && last.chapter == r.chapter && r.verse == last.to + 1) {
            out[out.lastIndex] = last.copy(to = r.verse)
        } else {
            out += RefRange(r.book, r.chapter, r.verse, r.verse)
        }
    }
    return out
}
