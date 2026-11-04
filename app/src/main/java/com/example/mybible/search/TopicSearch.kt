package com.example.mybible.search

import com.example.mybible.model.TopicCard
import com.example.mybible.model.TopicItem
import com.example.mybible.model.TopicPage
import com.example.mybible.model.TopicRow
import com.example.mybible.model.Verse

/**
 * Finds the Nave's topics a search names, and lays one out as a page.
 *
 * A topic matches when every word of the query (function words aside) is a word of its name, in
 * any form ("forgive": Forgiveness, "angels": Angel) or as the King James says it ("worry": Care).
 * When only some are, the rest can name one of its headings: "forgive enemies" opens Forgiveness
 * at "Of enemies"; or, failing that, lines further down: "Paul intercession" opens Intercession at
 * "Additional instances of", for its "Paul, for God's people". A topic that only says "see"
 * another (Anxiety, see Care) shows as that one.
 */
class TopicSearch(private val book: TopicBook, private val lexicon: SearchLexicon, private val index: BibleIndex) {

    private val inBible: (String) -> Boolean = index::contains

    // Each name's words without function words: "Sin, forgiveness of" is sin, forgiveness.
    private val nameWords: List<List<String>> = book.topics.map { topic -> wordsOf(topic.name).filter { it !in STOPWORDS } }

    private inner class QueryWord(val word: String) {
        private val kin = lexicon.kin(word, inBible)
        // happy: Happiness, holy: Holiness.
        private val stem = if (word.length >= 4 && word.endsWith("y")) word.dropLast(1) + "i" else word
        private val wording: Set<String> = lexicon.kingJamesWording(listOf(word), inBible).orEmpty()
            .filter { it.size == 1 }
            .flatMapTo(HashSet()) { lexicon.formsOf(it[0]) }

        /** How [name] matches this word: [DIRECT], [WORDING] (worry: care), or 0. */
        fun strength(name: String): Int = when {
            name in kin -> DIRECT
            // forgive: Forgiveness; pray: Prayer.
            word.length >= 4 && (name.startsWith(word) || name.startsWith(stem)) && name.length - word.length <= 4 -> DIRECT
            name.length >= 4 && word.startsWith(name) && word.length - name.length <= 3 -> DIRECT
            name in wording -> WORDING
            else -> 0
        }
    }

    // lines: the lines further down that the search names, when it's those that found the topic;
    // label, the words they're for ("Paul").
    private class Found(
        val topic: Topic,
        val tier: Int,
        val exact: Boolean,
        val section: Int?,
        val size: Int,
        val label: String? = null,
        val lines: List<Int> = emptyList()
    )

    fun find(query: String, limit: Int = MAX_TOPICS): List<TopicCard> {
        val words = wordsOf(query).filter { it.length > 1 && it !in STOPWORDS }.distinct().map(::QueryWord)
        if (words.isEmpty()) return emptyList()
        val found = ArrayList<Found>()
        for (topic in book.topics) {
            val name = nameWords[topic.id]
            if (name.isEmpty() || (topic.referenceCount == 0 && topic.links.isEmpty())) continue
            val strengths = words.map { w -> name.maxOf { w.strength(it) } }
            val size = if (topic.isRedirect) topic.links.maxOf { book[it.topic].referenceCount } else topic.referenceCount
            if (strengths.all { it > 0 }) {
                val direct = strengths.all { it == DIRECT }
                // A synonym's cross-reference reads wrong: love (KJV charity) → Charity, see Alms.
                if (!direct && topic.isRedirect) continue
                val exact = name.all { n -> words.any { it.strength(n) > 0 } }
                found += Found(topic, if (direct) 0 else 1, exact, null, size)
            } else if (strengths.any { it == DIRECT } && !topic.isRedirect) {
                val missing = words.filterIndexed { i, _ -> strengths[i] == 0 }
                fun names(label: String): Boolean {
                    val labelWords = wordsOf(label)
                    return missing.all { w -> labelWords.any { w.strength(it) == DIRECT } }
                }
                val section = topic.sections.indexOfFirst { names(it.label) }
                if (section >= 0) {
                    found += Found(topic, 2, false, section, size)
                } else {
                    val lines = linesNaming(topic, ::names)
                    if (lines.isNotEmpty()) {
                        val label = missing.joinToString(" ") { it.word.replaceFirstChar(Char::titlecase) }
                        found += Found(topic, 3, false, null, size, label, lines)
                    }
                }
            }
        }
        found.sortWith(compareBy<Found>({ it.tier }, { !it.exact }, { -it.size }, { it.topic.name.length }))

        val cards = ArrayList<TopicCard>()
        val shown = HashSet<Triple<Int, Int?, Boolean>>()
        fun add(topic: Topic, section: Int?, via: String?, label: String? = null, lines: List<Int> = emptyList()) {
            if (cards.size < limit && shown.add(Triple(topic.id, section, lines.isNotEmpty())) && topic.referenceCount > 0) {
                cards += card(topic, section, via, label, lines)
            }
        }
        for (f in found) {
            if (f.topic.isRedirect) {
                f.topic.links.take(2).forEach { add(book[it.topic], it.section, via = f.topic.name) }
            } else {
                add(f.topic, f.section, via = null, label = f.label, lines = f.lines)
            }
            if (cards.size >= limit) break
        }
        return cards
    }

    /**
     * The lines below [topic]'s main headings that [names] says the search names (their places in
     * its entries), but not those under one of them already: Intercession's "Paul for Onesimus",
     * "Paul, for God's people", "For Paul, by the congregations", "By Paul, of the congregations".
     */
    private fun linesNaming(topic: Topic, names: (String) -> Boolean): List<Int> {
        val out = ArrayList<Int>()
        var within = Int.MAX_VALUE // the level of the line last taken, while still under it
        for ((i, entry) in topic.entries.withIndex()) {
            if (entry.level <= within) within = Int.MAX_VALUE
            if (entry.level == 0 || within != Int.MAX_VALUE) continue
            if (entry is TopicEntry.Verses && entry.label.isNotBlank() && names(entry.label)) {
                out += i
                within = entry.level
            }
        }
        return out
    }

    // The references of [topic]'s entry [at] and of the lines under it.
    private fun referencesUnder(topic: Topic, at: Int): Int {
        val level = topic.entries[at].level
        var count = 0
        for (i in at until topic.entries.size) {
            val entry = topic.entries[i]
            if (i > at && entry.level <= level) break
            if (entry is TopicEntry.Verses) count += entry.ranges.size / 2
        }
        return count
    }

    // The labels of the lines right under [topic]'s entry [at].
    private fun linesUnder(topic: Topic, at: Int): List<String> {
        val level = topic.entries[at].level
        val out = ArrayList<String>()
        for (i in at + 1 until topic.entries.size) {
            val entry = topic.entries[i]
            if (entry.level <= level) break
            if (entry.level == level + 1 && entry is TopicEntry.Verses && entry.label.isNotBlank()) out += entry.label
        }
        return out
    }

    // A long line cut at a word, for a card: "Paul asked that the "thorn in the flesh" be removed…".
    private fun shortLine(label: String): String {
        if (label.length <= MAX_LINE_LENGTH) return label
        val cut = label.lastIndexOf(' ', MAX_LINE_LENGTH).takeIf { it > MAX_LINE_LENGTH / 2 } ?: MAX_LINE_LENGTH
        return label.substring(0, cut).trimEnd(' ', ',', ';', ':') + "…"
    }

    // A topic's card: for a heading, or lines further down, with their references alone, and what's under the heading.
    private fun card(topic: Topic, section: Int?, via: String?, label: String? = null, lines: List<Int> = emptyList()): TopicCard {
        val headings = topic.sections.map { it.label }.filter { it.isNotBlank() }.take(3).joinToString(" · ")
        val heading = section?.let { topic.sections.getOrNull(it) }?.let { topic.entries.indexOf(it) }?.takeIf { it >= 0 }
        return TopicCard(
            topicId = topic.id,
            name = topic.name,
            section = section,
            sectionLabel = label ?: heading?.let { shortLine((topic.entries[it] as TopicEntry.Verses).label) },
            via = via,
            referenceCount = when {
                lines.isNotEmpty() -> lines.sumOf { referencesUnder(topic, it) }
                heading != null -> referencesUnder(topic, heading)
                else -> topic.referenceCount
            },
            summary = when {
                lines.isNotEmpty() -> lines.joinToString(" · ") { shortLine((topic.entries[it] as TopicEntry.Verses).label) }
                heading != null -> linesUnder(topic, heading).take(3).joinToString(" · ").ifEmpty { headings }
                else -> headings
            },
            lines = lines
        )
    }

    /**
     * Only [lines] of [id] (see TopicCard.lines), each with what's under it, as a section headed by
     * it, and a link to all of the topic; its name with [label]: "Intercession › Paul".
     */
    fun page(id: Int, lines: List<Int>, label: String?): TopicPage {
        if (lines.isEmpty()) return page(id)
        val topic = book[id]
        val items = ArrayList<TopicItem>()
        for ((k, at) in lines.withIndex()) {
            val line = topic.entries.getOrNull(at) as? TopicEntry.Verses ?: continue
            val rows = SectionRows(LINE_SECTIONS + k, line.label)
            rows.add(0, line.ranges)
            for (i in at + 1 until topic.entries.size) {
                val entry = topic.entries[i]
                if (entry.level <= line.level) break
                val level = entry.level - line.level
                when (entry) {
                    is TopicEntry.Verses -> {
                        if (entry.label.isNotBlank()) rows.rows += TopicRow.Heading(level, entry.label)
                        rows.add(level, entry.ranges)
                    }
                    is TopicEntry.Link -> rows.rows += TopicRow.Link(level, entry.text, entry.topic, entry.section)
                }
            }
            items += rows.build()
        }
        items += TopicItem.Link("All of ${topic.name} (${topic.referenceCount} references)", id, null)
        return TopicPage(id, label?.let { "${topic.name} › $it" } ?: topic.name, items, lines)
    }

    /** [id]'s page: each main heading with what's under it, its verses with their text. */
    fun page(id: Int): TopicPage {
        val topic = book[id]
        val items = ArrayList<TopicItem>()
        var section: SectionRows? = null
        var heading = -1
        var unnamed = 0
        for (entry in topic.entries) {
            if (entry.level == 0) {
                section?.let { items += it.build() }
                section = null
                when (entry) {
                    is TopicEntry.Verses -> section = SectionRows(++heading, entry.label).also { it.add(0, entry.ranges) }
                    is TopicEntry.Link -> items += TopicItem.Link(entry.text, entry.topic, entry.section)
                }
                continue
            }
            // Under no main heading (after a link): a section without a heading of its own.
            val rows = section ?: SectionRows(--unnamed, "").also { section = it }
            when (entry) {
                is TopicEntry.Verses -> {
                    if (entry.label.isNotBlank()) rows.rows += TopicRow.Heading(entry.level, entry.label)
                    rows.add(entry.level, entry.ranges)
                }
                is TopicEntry.Link -> rows.rows += TopicRow.Link(entry.level, entry.text, entry.topic, entry.section)
            }
        }
        section?.let { items += it.build() }
        return TopicPage(id, topic.name, items)
    }

    private inner class SectionRows(val index: Int, val label: String) {
        val rows = ArrayList<TopicRow>()
        private var references = 0

        fun add(level: Int, ranges: IntArray) {
            for (i in ranges.indices step 2) {
                reference(level, ranges[i], ranges[i + 1])?.let {
                    rows += it
                    references++
                }
            }
        }

        fun build() = TopicItem.Section(index, label, references, rows)
    }

    private fun reference(level: Int, first: Int, last: Int): TopicRow.Reference? {
        val id = index.idOf(first)
        if (id < 0) return null
        val verse = index[id]
        return TopicRow.Reference(
            level = level,
            label = rangeLabel(verse, first, last),
            preview = verse.text,
            verse = Verse(verse.book, verse.chapter, verse.number, verse.text)
        )
    }

    // "Psalms 39:6", "Matthew 6:25–34", "Exodus 28" (all of it), "Genesis 1:1–2:3".
    private fun rangeLabel(verse: IndexedVerse, first: Int, last: Int): String {
        val start = "${verse.book} ${verse.chapter}:${verse.number}"
        if (first == last) return start
        val lastChapter = (last shr 8) and 0xFF
        val lastVerse = last and 0xFF
        return when {
            lastChapter == verse.chapter && verse.number == 1 && index.idOf(last + 1) < 0 -> "${verse.book} ${verse.chapter}"
            lastChapter == verse.chapter -> "$start–$lastVerse"
            else -> "$start–$lastChapter:$lastVerse"
        }
    }

    companion object {
        const val DIRECT = 2
        const val WORDING = 1
        /** Topic cards a search shows at most. */
        const val MAX_TOPICS = 8
        /** Where a page of lines (see page) numbers its sections from, apart from a topic's own. */
        const val LINE_SECTIONS = 100_000
        /** A line's length on a card at most, before it's cut. */
        const val MAX_LINE_LENGTH = 60
    }
}
