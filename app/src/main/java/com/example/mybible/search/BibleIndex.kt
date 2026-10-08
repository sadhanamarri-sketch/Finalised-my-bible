package com.example.mybible.search

import java.util.BitSet

/** One verse for [BibleIndex]: its book's place in the Bible (Genesis is 0) and its English text. */
class IndexedVerse(val book: String, val bookIndex: Int, val chapter: Int, val number: Int, val text: String)

/**
 * Every English verse in memory, in Bible order, with the verses each word is in. Built once
 * per app run from the verses Room holds, the first time Search opens (see
 * BibleRepository.searchEngine), so a search looks words up instead of scanning all 31,102
 * verses for each of them.
 */
class BibleIndex(verses: List<IndexedVerse>) {
    private val verses: Array<IndexedVerse> = verses
        .sortedWith(compareBy<IndexedVerse>({ it.bookIndex }, { it.chapter }, { it.number }))
        .toTypedArray()
    private val postings = HashMap<String, IntArray>(16_384)
    private val idByRef = HashMap<Int, Int>(this.verses.size * 2)

    init {
        val building = HashMap<String, IntList>(16_384)
        for ((id, verse) in this.verses.withIndex()) {
            idByRef[packRef(verse.bookIndex, verse.chapter, verse.number)] = id
            forEachWord(verse.text) { word, _, _ -> building.getOrPut(word) { IntList() }.addOnce(id) }
        }
        for ((word, ids) in building) postings[word] = ids.toArray()
    }

    val size: Int get() = verses.size

    operator fun get(id: Int): IndexedVerse = verses[id]

    /** Every word the King James text has, with how many verses it's in. */
    val vocabulary: Map<String, Int> by lazy { postings.mapValues { it.value.size } }

    fun contains(word: String): Boolean = word in postings

    fun verseCount(word: String): Int = postings[word]?.size ?: 0

    /** The verses with any of [words]. */
    fun versesWithAny(words: Collection<String>): BitSet {
        val out = BitSet(verses.size)
        for (word in words) postings[word]?.forEach { out.set(it) }
        return out
    }

    /** The verse at a packed reference (see [packRef]), or -1 when this Bible doesn't have it. */
    fun idOf(packedRef: Int): Int = idByRef[packedRef] ?: -1

    companion object {
        /** Book index * 65536 + chapter * 256 + verse, the form the bundled verse lists use. */
        fun packRef(bookIndex: Int, chapter: Int, verse: Int) = bookIndex * 65536 + chapter * 256 + verse
    }
}

/** A growable list of verse numbers, added in order, each once. */
private class IntList {
    private var items = IntArray(4)
    private var count = 0

    fun addOnce(value: Int) {
        if (count > 0 && items[count - 1] == value) return
        if (count == items.size) items = items.copyOf(count * 2)
        items[count++] = value
    }

    fun toArray(): IntArray = items.copyOf(count)
}
