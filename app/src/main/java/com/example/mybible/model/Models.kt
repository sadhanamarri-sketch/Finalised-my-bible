package com.example.mybible.model

import kotlinx.serialization.Serializable

@Serializable
data class DictionaryMeaning(
    val partOfSpeech: String,
    val definitions: List<String>
)

@Serializable
data class EnglishDictionaryEntry(
    val word: String,
    val phonetic: String? = null,
    val meanings: List<DictionaryMeaning> = emptyList(),
    // Set when the lookup word itself isn't a Webster 1828 headword but a
    // suffix-stripped base form is (e.g. "tribulations" -> "tribulation",
    // "loveth" -> "love") — see BibleRepository.lookupEnglishWord. Null for
    // a direct headword match. Lets the UI note which word the shown
    // definition actually belongs to.
    val resolvedFrom: String? = null
)

@Serializable
data class LexiconEntry(
    val lemma: String,
    val transliteration: String = "",
    val morphology: String = "",
    val gloss: String,
    val definition: String
)

@Serializable
data class GreekWord(
    val greek: String,
    val transliteration: String,
    val englishGloss: String,
    val strongs: String? = null,
    val morphology: String = ""
)

@Serializable
data class HebrewWord(
    val hebrew: String,
    val transliteration: String,
    val englishGloss: String,
    val strongs: String? = null,
    val morphology: String = ""
)

enum class SavedWordLanguage { GREEK, HEBREW, ENGLISH }

// A word bookmarked from a Greek/Hebrew interlinear lookup or an English
// dictionary lookup (see BibleRepository.toggleSavedWord) — a personal
// glossary the user builds up over time, browsed from Search (see
// SavedWordsScreen), the same way Tags are managed from Notes. Part of
// BackupData (see exportBackupJson/importFromBackup), merged by dedupeKey
// the same tombstone-aware way as every other collection there.
//
// dedupeKey identifies "the same word" for toggling save/unsave: language
// plus the word text and transliteration, lowercased. Not Strong's number
// alone, since an English lookup has none and two genuinely different
// words can share a Strong's number in this data (see the dropped
// Strong's-based related-words feature's doc in git history for why that
// number alone isn't a safe identity to key on).
@Serializable
data class SavedWordItem(
    val id: Long = 0,
    val language: SavedWordLanguage,
    val word: String,
    val transliteration: String = "",
    val gloss: String = "",
    val definition: String = "",
    val morphology: String = "",
    val strongs: String? = null,
    val sourceBook: String = "",
    val sourceChapter: Int = 0,
    val sourceVerse: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun dedupeKey(): String = "$language|${word.lowercase()}|${transliteration.lowercase()}"
}

@Serializable
data class Verse(
    val book: String,
    val chapter: Int,
    val number: Int,
    val text: String,
    val isRedLetter: Boolean = false,
    val teluguText: String? = null,
    val greekWords: List<GreekWord>? = null,
    val hebrewWords: List<HebrewWord>? = null
)

/**
 * One verse a search found. [text] is what its card shows: the verse's English, or its Telugu
 * for a search typed in Telugu; [highlights] are the parts of it that matched.
 */
data class SearchHit(
    val verse: Verse,
    val text: String,
    val highlights: List<IntRange> = emptyList(),
    /**
     * True when the verse says it in other words: King James wording for a word of today
     * ("careful" for worry), or the same Greek or Hebrew word. [reasons] say which.
     */
    val related: Boolean = false,
    val reasons: List<String> = emptyList()
)

/**
 * Something a search also matched besides the words as typed, shown as a chip that switches it
 * off and on: the words' other forms, a King James wording, or a Greek or Hebrew word (several
 * small ones share a chip, hence [ids]).
 */
data class SearchSource(
    val ids: List<String>,
    val label: String,
    /** Verses it adds. */
    val count: Int,
    val enabled: Boolean,
    /** [count] by book, for counting them in one part of the Bible (see SearchFilters). */
    val countByBook: Map<String, Int> = emptyMap()
)

/**
 * BibleRepository.searchBible's result. [hits] holds the verses with the words themselves first
 * ([exactCount] of them), then the ones that say it in other words. Not @Serializable: search
 * results are always freshly computed, never saved.
 */
data class SearchOutcome(
    val hits: List<SearchHit> = emptyList(),
    val exactCount: Int = 0,
    val sources: List<SearchSource> = emptyList(),
    /** A spelling the Bible has, offered only when nothing was found ("fiath" → "faith"). */
    val suggestion: String? = null,
    /** No verse has every word: [hits] are the verses with all but one. */
    val closeMatches: Boolean = false,
    /** Nave's topics the search names, shown above the verses. */
    val topics: List<TopicCard> = emptyList(),
    /** For a search typed in Greek or Hebrew, or by Strong's number: the words it found. */
    val originalWords: List<OriginalWordCard> = emptyList(),
    /** What the hits past [exactCount] are. */
    val relatedKind: RelatedKind = RelatedKind.SAME_MEANING,
    /** A sentence on why they're here, under their heading: which Hebrew word a Greek one stands for. */
    val relatedNote: String? = null
) {
    val relatedCount: Int get() = hits.size - exactCount
}

enum class RelatedKind {
    /** Verses that say it in other words: King James wording, the same Greek or Hebrew word. */
    SAME_MEANING,
    /** A search in Telugu: verses with a word only inside a longer one (యేసు in క్రీస్తుయేసు). */
    INSIDE_LONGER_WORDS,
    /** A search for a Greek word: the Old Testament verses with the Hebrew word it stands for. */
    OLD_TESTAMENT,
    /** A search for a Hebrew word: the New Testament verses with the Greek word for it. */
    NEW_TESTAMENT
}

/**
 * What the Greek and Hebrew word pages' "Find every verse with this word" will find: the search it
 * runs (the word's Strong's number, G26), its verses, and the other Testament's verses with the
 * word in its language (35 in the Old Testament with אַהֲבָה, ahavah).
 */
data class WordSearchPreview(
    val query: String,
    val verseCount: Int,
    val otherVerseCount: Int = 0,
    /** "Old Testament" or "New Testament". */
    val otherTestament: String = "",
    /** The other Testament's words for it: "אַהֲבָה (ahavah)". */
    val otherWords: List<String> = emptyList()
)

/**
 * How a Greek or Hebrew word is used, for its page: what it means in the verse the page was opened
 * from, its meanings, the King James words for it and the books it's in most. From the same data as
 * Search (see OriginalSearch.study).
 */
data class WordStudy(
    /** The search for every verse with it: its Strong's number, G863. */
    val query: String,
    /** Its meaning in the verse its page was opened from, when it has several. */
    val meaningHere: String? = null,
    /** Its meanings, each with how many verses: the most used first. Empty when it has one. */
    val meanings: List<WordUse> = emptyList(),
    /** The King James words for it, each with the % of its verses that use it. */
    val kingJames: List<WordUse> = emptyList(),
    /** The books it's in most, each with how many verses. */
    val books: List<WordUse> = emptyList(),
    /** How many books it's in. */
    val bookCount: Int = 0
)

/**
 * A line of a [WordStudy]: a meaning, a King James word or a book, with how many (verses, or a %).
 * For a meaning, [offChips] are the search's chips to switch off for its verses alone.
 */
data class WordUse(val label: String, val count: Int, val offChips: Set<String> = emptySet())

/** A Greek or Hebrew word a search names (ἀγάπη, "agape", G26), shown above its verses. */
data class OriginalWordCard(
    /** Greek, Hebrew or Aramaic. */
    val language: String,
    val word: String,
    val transliteration: String,
    /** Its Strong's number as people write it: G26, H2617. */
    val number: String,
    /** What it means, its senses' meanings from the most used: to release, forgive, permit. */
    val meanings: List<String>,
    /** The King James words most often found where it is: love, charity. */
    val kingJames: List<String>,
    val verseCount: Int,
    /** Another word the same letters can be, after the likelier one (חסד: chasad after chesed). */
    val alternative: Boolean = false
)

/** A Nave's Topical Bible topic a search found, as a card above the verses. */
data class TopicCard(
    val topicId: Int,
    val name: String,
    /** The part of it the search named ("Of enemies" for "forgive enemies"), which it opens at. */
    val section: Int? = null,
    val sectionLabel: String? = null,
    /** The topic the search actually named, when that one only says "see" this: Anxiety for Care. */
    val via: String? = null,
    val referenceCount: Int,
    /** Its first few headings: "Worldly · Remedy for · Instances of". */
    val summary: String
)

/** A Nave's topic as its page shows it: its sections, each a heading that can fold, and "see" links. */
data class TopicPage(val topicId: Int, val name: String, val items: List<TopicItem>) {
    val referenceCount: Int get() = items.sumOf { (it as? TopicItem.Section)?.referenceCount ?: 0 }
}

sealed interface TopicItem {
    /**
     * One of Nave's main headings ("Worldly", "Remedy for") with everything under it. [index] is
     * its place among them, what a link's section means; negative for verses under no heading.
     */
    data class Section(val index: Int, val label: String, val referenceCount: Int, val rows: List<TopicRow>) : TopicItem

    data class Link(val text: String, val topicId: Int, val section: Int?) : TopicItem
}

sealed interface TopicRow {
    /** Nave's indent: 0 for a main heading's own verses, then 1 to 3 for those under a subheading. */
    val level: Int

    data class Heading(override val level: Int, val label: String) : TopicRow

    /** A verse or passage ("Matthew 6:25–34"), with its first verse's text. */
    data class Reference(override val level: Int, val label: String, val preview: String, val verse: Verse) : TopicRow

    data class Link(override val level: Int, val text: String, val topicId: Int, val section: Int?) : TopicRow
}

@Serializable
data class NoteReference(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val verseText: String = ""
)

@Serializable
data class TagDefinition(
    val name: String,
    // Optional free-text note shown under the tag name on the Tags screen
    // (e.g. "Verses about God's faithfulness in hard seasons"). Defaulted
    // so existing saved tags (and any old JSON without this field) decode
    // cleanly as "no description" rather than failing to parse.
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class NoteItem(
    val id: Long = 0,
    // Legacy primary-reference fields are retained for backwards-compatible
    // decoding of notes created by older Kotlin builds. New notes also store
    // their references in `refs`, which supports multiple Bible references.
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int = 0,
    val verseText: String = "",
    val text: String = "",
    val title: String = "",
    val noteDate: String = "",
    val refs: List<NoteReference> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // JSON-encoded NoteDocument (see model/RichText.kt) carrying this note's
    // formatting — heading levels, bold/italic/underline, highlight,
    // blockquote, lists, indent, alignment. Blank for every note saved
    // before the rich text editor existed, and for any note never reopened
    // in it since; `text` above stays the plain-text rendition either way
    // (kept in sync on every save) so old code paths — list previews,
    // search, backup — never need to know richText exists.
    val richText: String = ""
)

@Serializable
data class CompletedVerseItem(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val completedAt: Long = System.currentTimeMillis()
)

// A user-editable, labeled highlight color (e.g. "Key Verse", gold).
// Identity is the hex value itself, not a separate id — see
// model/HighlightColors.kt for why, and HighlightItem below stays
// unchanged (still stores colorHex directly), so nothing about existing
// saved highlights needs migrating when this is introduced.
//
// `enabled` lets the user hide a color from the picker (Manage Highlight
// Colors) without deleting it or losing any verses already highlighted
// with it — those verses still render with their color, they just can't
// be picked again while disabled. Defaults to true so old saved JSON
// (encoded before this field existed) decodes every color as enabled.
@Serializable
data class HighlightColorDef(
    val label: String,
    val colorHex: String,
    val enabled: Boolean = true
)

@Serializable
data class HighlightItem(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val colorHex: String,
    // Added so highlight tombstones can be compared by recency the same
    // way notes are (see BibleRepository.importFromBackup) — a re-highlight
    // that happened after a delete should survive a merge as an intentional
    // undelete, not get silently blocked forever just because a tombstone
    // exists. Defaults to "now" for any HighlightItem built before this
    // field existed (both in code and in already-saved/backed-up JSON).
    val updatedAt: Long = System.currentTimeMillis(),
    // Links this highlight to the optional "quick note" comment created
    // alongside it (e.g. "doubt", "prayer point") — a real NoteItem, found
    // by id in the same notes list, not a duplicate content store. Null for
    // every highlight created before this existed, and for any highlight
    // the user chose not to comment on.
    val noteId: Long? = null
)

@Serializable
data class CrossReferenceItem(
    val targetBook: String,
    val targetChapter: Int,
    val targetVerse: Int,
    val previewText: String
)

enum class ThemeMode {
    PAPER,        // Classic warm paper #F6F3EC
    SEPIA,        // Warm sepia #F5EBE0
    LIGHT,        // Clean light #FFFFFF
    DARK,         // Night mode #1C1A17
    CLASSIC_DARK  // Capacitor app's original dark theme, warm terracotta accent #E0836F
}

// A record that something was deleted, so a later merge (restore from an
// older backup, or sync from a device that never got the delete) doesn't
// silently resurrect it. `key` identifies the deleted item within its own
// entity type (see BibleRepository's *TombstoneKey builders — e.g.
// "note:1234", "highlight:Genesis:1:1", "color:#ff0000"); `deletedAt` is
// used both to decide which side wins when the same key is deleted on two
// devices, and to prune tombstones once they're old enough that keeping
// them forever isn't worth the growing backup size.
@Serializable
data class SyncTombstones(
    val entries: Map<String, Long> = emptyMap()
)

@Serializable
data class BackupData(
    val app: String = "my-bible-android",
    val backupVersion: Int = 1,
    val exportedAt: String = "",
    val notes: List<NoteItem> = emptyList(),
    // Legacy, name-only tag list — kept so a backup taken by an older
    // build of this app (before `tagDefs` existed) still decodes. Newer
    // backups populate both this and `tagDefs`; import prefers `tagDefs`
    // when present since it's the only one that carries `description`.
    val tags: List<String> = emptyList(),
    val tagDefs: List<TagDefinition> = emptyList(),
    val completed: List<CompletedVerseItem> = emptyList(),
    val highlights: List<HighlightItem> = emptyList(),
    // Defaults to empty so older backups (from before this field existed)
    // still decode fine — kotlinx.serialization fills in the default for
    // any field missing from the JSON instead of failing to parse.
    val highlightColorDefs: List<HighlightColorDef> = emptyList(),
    // Same "defaults to empty for older backups" reasoning as
    // highlightColorDefs above — added after saved words existed.
    val savedWords: List<SavedWordItem> = emptyList(),
    val tombstones: SyncTombstones = SyncTombstones()
)
