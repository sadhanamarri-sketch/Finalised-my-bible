package com.example.mybible.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One headword from Noah Webster's 1828 American Dictionary of the
 * English Language (public domain; period-correct for the archaic KJV
 * vocabulary this app's readers actually look up — "wist", "durst",
 * "leasing", "carriage", etc.), imported by
 * [com.example.mybible.data.WebsterImporter] from the bundled
 * `assets/webster1828.json`. Backs the English word lookup sheet,
 * replacing the old live per-word calls to the Free Dictionary API
 * (api.dictionaryapi.dev) with a one-time bulk import — so lookups work
 * fully offline, with no live fallback.
 *
 * [definition] is a small pre-formatted text block, not raw source HTML
 * (the HTML/etymology/quotation cleanup happens once at asset-build time,
 * not on-device): the first line is the part of speech (may be blank),
 * followed by one line per numbered sense ("1. ... ", "2. ... "). Parsed
 * back apart at lookup time in
 * [com.example.mybible.data.BibleRepository.parseWebsterDefinition]
 * rather than at import time, since Room only stores it as text either way.
 */
@Entity(tableName = "webster_entries")
data class WebsterEntity(
    @PrimaryKey val word: String,
    val definition: String
)
