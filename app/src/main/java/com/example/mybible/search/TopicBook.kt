package com.example.mybible.search

import com.example.mybible.model.TopicSource
import java.io.InputStream

/** One line under a topic: a heading with or without verses, or a "see" link. */
sealed class TopicEntry(val level: Int) {
    /** [ranges] holds each range's first and last verse, packed as in [BibleIndex.packRef]. */
    class Verses(level: Int, val label: String, val ranges: IntArray) : TopicEntry(level)

    /** See [topic], or its [section]-th main heading. */
    class Link(level: Int, val topic: Int, val section: Int?, val text: String) : TopicEntry(level)
}

class Topic(val id: Int, val name: String, val entries: List<TopicEntry>, val source: TopicSource = TopicSource.NAVES) {
    val referenceCount: Int = entries.sumOf { (it as? TopicEntry.Verses)?.ranges?.size?.div(2) ?: 0 }

    /** Its main headings, in order: what a link's section number counts. */
    val sections: List<TopicEntry.Verses> = entries.filter { it.level == 0 }.filterIsInstance<TopicEntry.Verses>()

    val links: List<TopicEntry.Link> = entries.filterIsInstance<TopicEntry.Link>()

    /** Nothing of its own, only "see" another topic: Anxiety, see Care. */
    val isRedirect: Boolean get() = referenceCount == 0 && links.isNotEmpty()
}

/**
 * Search's topic books: Nave's Topical Bible, from assets/search/topics.tsv (written by
 * tools/search/make_topics_data.py from BibleData's structured copy, which describes the format),
 * then Torrey's New Topical Textbook, from torrey.tsv in the same format (make_torrey_data.py), its
 * topics numbered on from Nave's.
 */
class TopicBook private constructor(val topics: List<Topic>) {

    operator fun get(id: Int): Topic = topics[id]

    companion object {
        const val TOPICS = "topics.tsv"
        const val TORREY = "torrey.tsv"

        fun load(open: (String) -> InputStream): TopicBook {
            val naves = topicsOf(open(TOPICS).bufferedReader().use { it.readText() }, TopicSource.NAVES, 0)
            val torrey = topicsOf(open(TORREY).bufferedReader().use { it.readText() }, TopicSource.TORREY, naves.size)
            return TopicBook(naves + torrey)
        }

        fun parse(text: String): TopicBook = TopicBook(topicsOf(text, TopicSource.NAVES, 0))

        // A file's topics, numbered from [first], as are the topics its "see" lines name.
        private fun topicsOf(text: String, source: TopicSource, first: Int): List<Topic> {
            val topics = ArrayList<Topic>()
            var name: String? = null
            var entries = ArrayList<TopicEntry>()
            for (line in text.lineSequence()) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val cols = line.split('\t')
                when (cols[0]) {
                    "T" -> {
                        name?.let { topics += Topic(first + topics.size, it, entries, source) }
                        name = cols[1]
                        entries = ArrayList()
                    }
                    "E" -> entries += TopicEntry.Verses(cols[1].toInt(), cols[2], decodeRanges(cols.getOrElse(3) { "" }))
                    "S" -> entries += TopicEntry.Link(cols[1].toInt(), first + cols[2].toInt(), cols[3].toIntOrNull(), cols[4])
                }
            }
            name?.let { topics += Topic(first + topics.size, it, entries, source) }
            return topics
        }

        /** "1iwwc,phj9-9": each range's first verse in base 36, and how far on its last is. */
        internal fun decodeRanges(encoded: String): IntArray {
            if (encoded.isEmpty()) return IntArray(0)
            val parts = encoded.split(',')
            val out = IntArray(parts.size * 2)
            for ((i, part) in parts.withIndex()) {
                val dash = part.indexOf('-')
                val first = (if (dash < 0) part else part.substring(0, dash)).toInt(36)
                out[2 * i] = first
                out[2 * i + 1] = if (dash < 0) first else first + part.substring(dash + 1).toInt(36)
            }
            return out
        }
    }
}
