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

    // label: the lines further down that the search names, when it's those that found the topic.
    private class Found(val topic: Topic, val tier: Int, val exact: Boolean, val section: Int?, val size: Int, val label: String? = null)

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
                    for ((under, labels) in linesNaming(topic, ::names).entries.take(MAX_LINE_CARDS)) {
                        val shown = labels.take(2).joinToString(" · ", transform = ::shortLine)
                        found += Found(topic, 3, false, under, size, if (labels.size > 2) "$shown · …" else shown)
                    }
                }
            }
        }
        found.sortWith(compareBy<Found>({ it.tier }, { !it.exact }, { -it.size }, { it.topic.name.length }))

        val cards = ArrayList<TopicCard>()
        val shown = HashSet<Pair<Int, Int?>>()
        fun add(topic: Topic, section: Int?, via: String?, label: String? = null) {
            if (cards.size < limit && shown.add(topic.id to section) && topic.referenceCount > 0) {
                cards += card(topic, section, via, label)
            }
        }
        for (f in found) {
            if (f.topic.isRedirect) {
                f.topic.links.take(2).forEach { add(book[it.topic], it.section, via = f.topic.name) }
            } else {
                add(f.topic, f.section, via = null, label = f.label)
            }
            if (cards.size >= limit) break
        }
        return cards
    }

    /**
     * The lines below [topic]'s main headings that [names] says the search names, by the main
     * heading they're under (null: under none, after a "see" link): Intercession's "Paul for
     * Onesimus" is under "Instances of"; "Paul, for God's people" and "For Paul, by the
     * congregations" under "Additional instances of".
     */
    private fun linesNaming(topic: Topic, names: (String) -> Boolean): Map<Int?, List<String>> {
        val out = LinkedHashMap<Int?, MutableList<String>>()
        var under: Int? = null
        var headings = -1
        for (entry in topic.entries) {
            if (entry.level == 0) {
                under = if (entry is TopicEntry.Verses) ++headings else null
                continue
            }
            if (entry is TopicEntry.Verses && entry.label.isNotBlank() && names(entry.label)) {
                out.getOrPut(under) { ArrayList() } += entry.label
            }
        }
        return out
    }

    // A long line cut at a word, for a card: "Paul asked that the "thorn in the flesh" be removed…".
    private fun shortLine(label: String): String {
        if (label.length <= MAX_LINE_LENGTH) return label
        val cut = label.lastIndexOf(' ', MAX_LINE_LENGTH).takeIf { it > MAX_LINE_LENGTH / 2 } ?: MAX_LINE_LENGTH
        return label.substring(0, cut).trimEnd(' ', ',', ';', ':') + "…"
    }

    private fun card(topic: Topic, section: Int?, via: String?, label: String? = null) = TopicCard(
        topicId = topic.id,
        name = topic.name,
        section = section,
        sectionLabel = label ?: section?.let { topic.sections.getOrNull(it)?.label?.let(::shortLine) },
        via = via,
        referenceCount = topic.referenceCount,
        summary = topic.sections.map { it.label }.filter { it.isNotBlank() }.take(3).joinToString(" · ")
    )

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
        /** Cards one topic gets at most for lines below its main headings, one per heading they're under. */
        const val MAX_LINE_CARDS = 3
        /** A line's length on a card at most, before it's cut. */
        const val MAX_LINE_LENGTH = 60
    }
}
