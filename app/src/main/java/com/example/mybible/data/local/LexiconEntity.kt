package com.example.mybible.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Strong's-number entry from STEPBible's TBESG (Translators Brief
 * lexicon of Extended Strongs for Greek — CC BY 4.0, Tyndale House
 * Cambridge / STEPBible.org).
 *
 * TBESG is a structured TSV. Keep the source columns separate instead of
 * guessing which field is the gloss/definition from its length.
 *
 * Primary key is [strongsDisambiguated] (dStrong#, e.g. G4613H), not the
 * bare [strongs] (eStrong#, e.g. G4613) as it used to be. TBESG can list
 * several disambiguated rows under one eStrong — sometimes genuine
 * duplicates (e.g. G4613's nine "Simon" rows all share one combined
 * "which Simon" essay), but sometimes two *entirely unrelated* words that
 * happen to share a bare Strong's number, e.g. G0001G is "Α" (the letter
 * alpha, as a numeral) and G0001H is "ἔα" (an interjection, "ah!/ha!").
 * Keying on the bare number and discarding every row but one — the old
 * behavior — silently dropped the second word's definition entirely; any
 * verse tagged with dStrong G0001H would have shown the "alpha" entry
 * instead. TAGNT/TAHOT's own per-occurrence tagging already carries the
 * full disambiguated code (see GreekImporter/HebrewImporter's dStrong
 * parsing), so looking that up directly instead of stripping it away
 * first is both more correct and no more expensive.
 * [strongs] stays as a regular indexed column for the bare-number fallback
 * lookup (BibleDao.getLexiconEntryByBareStrongs) used when the exact
 * disambiguated form somehow isn't tagged on the occurrence.
 */
@Entity(
    tableName = "lexicon_entries",
    indices = [Index(value = ["strongs"])]
)
data class LexiconEntity(
    /** Disambiguated Strong's form (dStrong#), e.g. G5463G — see class doc. */
    @PrimaryKey val strongsDisambiguated: String,
    /** Extended Strong's key (eStrong#), e.g. G5463. */
    val strongs: String,
    /** Unified Strong's form (uStrong#). */
    val strongsUnified: String = "",
    /** Greek citation/lexical form. */
    val lemma: String,
    /** Romanized form supplied by TBESG. */
    val transliteration: String = "",
    /** TBESG morphology, e.g. G:V. */
    val morphology: String = "",
    /** Brief meaning/gloss. */
    val gloss: String,
    /** Full TBESG meaning/definition. */
    val definition: String
)
