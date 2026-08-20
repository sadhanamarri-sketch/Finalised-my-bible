package com.example.mybible.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Greek word from STEPBible's TAGNT (Translators Amalgamated Greek NT)
 * dataset, imported by [com.example.mybible.data.GreekImporter]. Real data —
 * ported from the same source and parsing logic the Capacitor app uses,
 * replacing the ~15-word hardcoded dictionary + hash-generated fake words.
 */
@Entity(
    tableName = "greek_words",
    // unique on (book, chapter, verse, orderIndex) — see the identical
    // note on HebrewWordEntity's indices for why this is required for
    // @Insert(onConflict = REPLACE) in insertGreekWords() to actually
    // overwrite instead of duplicate on a retried import.
    indices = [Index(value = ["book", "chapter", "verse", "orderIndex"], unique = true)]
)
data class GreekWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val orderIndex: Int,
    val greek: String,
    val transliteration: String,
    val gloss: String,
    val strongs: String,
    /** Robinson-style TAGNT morphology code, e.g. V-PAN. */
    val morphology: String = ""
)
