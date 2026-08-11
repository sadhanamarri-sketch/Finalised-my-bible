package com.example.mybible.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Strong's-number entry from STEPBible's TBESG (Translators Brief
 * lexicon of Extended Strongs for Greek — CC BY 4.0, Tyndale House
 * Cambridge / STEPBible.org).
 *
 * TBESG is a structured TSV. Keep the source columns separate instead of
 * guessing which field is the gloss/definition from its length.
 */
@Entity(tableName = "lexicon_entries")
data class LexiconEntity(
    /** Extended Strong's key used for the lookup (eStrong#), e.g. G5463. */
    @PrimaryKey val strongs: String,
    /** Disambiguated Strong's form (dStrong#), e.g. G5463G. */
    val strongsDisambiguated: String = "",
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
