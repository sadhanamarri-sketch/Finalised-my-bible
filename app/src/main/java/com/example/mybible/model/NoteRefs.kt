package com.example.mybible.model

import com.example.mybible.data.findVerseMentions

/** Verses side by side in one chapter, shown as one: "John 15:1–8"; or a whole chapter, [from] 0: "John 3". */
data class RefRange(val book: String, val chapter: Int, val from: Int, val to: Int) {
    val wholeChapter: Boolean get() = from == 0

    val label: String get() = when {
        wholeChapter -> "$book $chapter"
        from == to -> "$book $chapter:$from"
        else -> "$book $chapter:$from–$to"
    }

    fun covers(r: NoteReference): Boolean = r.book == book && r.chapter == chapter && r.verse in from..to
}

/**
 * A note's verses as the passages they make, in Bible order ([bookIndex] gives a book's place).
 * Each verse is still kept on its own, so the Reader marks each; verses side by side in a chapter
 * show as one. A whole chapter stays on its own, before its chapter's verses.
 */
fun groupRefs(refs: List<NoteReference>, bookIndex: (String) -> Int): List<RefRange> {
    val sorted = refs.distinctBy { Triple(it.book, it.chapter, it.verse) }
        .sortedWith(compareBy<NoteReference>({ bookIndex(it.book) }, { it.book }, { it.chapter }, { it.verse }))
    val out = mutableListOf<RefRange>()
    for (r in sorted) {
        val last = out.lastOrNull()
        if (last != null && !last.wholeChapter && last.book == r.book && last.chapter == r.chapter && r.verse == last.to + 1) {
            out[out.lastIndex] = last.copy(to = r.verse)
        } else {
            out += RefRange(r.book, r.chapter, r.verse, r.verse)
        }
    }
    return out
}

/** The longest passage taken from one mention when the chapter's length isn't known. */
private const val LONGEST_NAMED = 176

/**
 * The verses named in a note's [title] and [text] ("John 3:16", "Rom 8:28-30" for three), each on
 * its own and marked [NoteReference.named]: the note's verses too, for as long as they're named.
 * [verses] gives how many verses a chapter has, null when that isn't known (then any verse is
 * taken), so "John 3:99" is left out.
 */
fun namedRefs(title: String, text: String, verses: (book: String, chapter: Int) -> Int?): List<NoteReference> {
    val found = LinkedHashSet<Triple<String, Int, Int>>()
    for (m in findVerseMentions(title) + findVerseMentions(text)) {
        if (m.chapter < 1 || m.verse < 1) continue
        val last = verses(m.book, m.chapter)?.let { minOf(m.toVerse, it) } ?: minOf(m.toVerse, m.verse + LONGEST_NAMED - 1)
        for (v in m.verse..last) found += Triple(m.book, m.chapter, v)
    }
    return found.map { (book, chapter, verse) -> NoteReference(book, chapter, verse, named = true) }
}

/**
 * A note's verses: those added with + Verse ([added]), then those [named] in its text that they
 * don't already hold (a whole chapter holds each of its verses).
 */
fun withNamed(added: List<NoteReference>, named: List<NoteReference>): List<NoteReference> {
    val verses = added.map { Triple(it.book, it.chapter, it.verse) }.toHashSet()
    val chapters = added.filter { it.verse == 0 }.map { it.book to it.chapter }.toHashSet()
    return added + named.filter { Triple(it.book, it.chapter, it.verse) !in verses && (it.book to it.chapter) !in chapters }
}

/**
 * How many verses a chapter has, for [namedRefs], from [counts] (each book's chapters: Genesis to
 * 31, 25, 24…): 0 for a chapter the book doesn't have, null for a book not in [counts].
 */
fun chapterSizes(counts: Map<String, List<Int>>): (String, Int) -> Int? = { book, chapter ->
    counts[book]?.let { it.getOrNull(chapter - 1) ?: 0 }
}
