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
// SavedWordsScreen), the same way Tags are managed from Notes. Local-only
// for now: not part of BackupData, so saved words don't survive a
// backup/restore yet.
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

// BibleRepository.searchBible's full result. variantSuggestions (root-word
// forms, e.g. "walk" for a search of "walked") are shown as tappable chips
// rather than eagerly searched and displayed — tapping one runs a fresh
// search for that exact word (see SearchScreen). Not @Serializable: search
// results are always freshly computed, never cached to disk like Verse
// sometimes is. correctedQuery is null when the typed word was already
// recognized (or wasn't a single plain word to begin with, case-sensitive
// mode was on, or "Extensive search" is off), so the UI only shows a
// "Showing results for…" note when a real correction happened.
//
// Both correctedQuery and variantSuggestions are only ever populated when
// the opt-in "Extensive search" toggle is on (see MainViewModel/
// SearchScreen) — they require building an in-memory dictionary of every
// distinct word in the KJV, a scan over all verse text expensive enough
// that it's not worth paying by default for a feature most searches never
// need (a plain substring search already surfaces "loved"/"loving" for a
// search of "love" with no lookup at all).
//
// A separate Strong's-number-based "related words" feature (words sharing
// a Strong's number with the searched word) was tried and dropped for a
// different reason — Hebrew (OT) Strong's numbers frequently lump
// unrelated homonyms together, surfacing garbage like a proper name as a
// "related" suggestion with no reliable way to filter it out. That's a
// data-quality problem the Extensive search toggle doesn't fix, so it
// stays permanently out of scope rather than being offered behind it.
data class SearchOutcome(
    val correctedQuery: String? = null,
    val mainResults: List<Verse> = emptyList(),
    val variantSuggestions: List<String> = emptyList()
)

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
    val updatedAt: Long = System.currentTimeMillis()
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
    val tombstones: SyncTombstones = SyncTombstones()
)
