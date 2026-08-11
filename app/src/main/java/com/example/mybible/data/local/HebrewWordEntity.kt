package com.example.mybible.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Hebrew word from STEPBible's TAHOT (Translators Amalgamated Hebrew
 * OT) dataset, imported by [com.example.mybible.data.HebrewImporter]. Real
 * data — same source, provider, and licence (CC BY 4.0, Tyndale House
 * Cambridge / STEPBible.org) and the same parsing approach as the existing
 * NT [GreekWordEntity]/GreekImporter, extended to cover the Old Testament.
 *
 * [hebrew] and [transliteration] keep TAHOT's own "/" (prefix/root/suffix)
 * and "\" (punctuation) separators exactly as published, since they carry
 * real information about how the word is composed — the reader chip splits
 * on "/" for display, it isn't stripped at import time.
 */
@Entity(
    tableName = "hebrew_words",
    indices = [Index(value = ["book", "chapter", "verse"])]
)
data class HebrewWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val orderIndex: Int,
    val hebrew: String,
    val transliteration: String,
    val gloss: String,
    /** Root dStrong extracted from the {braces} in TAHOT's dStrongs column, e.g. H7225G. */
    val strongs: String,
    /** ETCBC-derived morphology code as published by TAHOT, e.g. HR/Ncfsa. */
    val morphology: String = ""
)
