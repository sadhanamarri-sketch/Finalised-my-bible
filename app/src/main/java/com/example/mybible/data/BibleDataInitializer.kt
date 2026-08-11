package com.example.mybible.data

import android.content.Context
import com.example.mybible.data.local.BibleDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A full, correctly-imported KJV has 31,102 verses; used as a "did this finish" check
 *  that self-heals even if a previous run was interrupted partway through. */
private const val FULL_BIBLE_VERSE_THRESHOLD = 30_000

/** TAGNT covers ~138k Greek NT words once fully imported. */
private const val GREEK_WORD_THRESHOLD = 100_000

/** TAHOT covers ~283k Hebrew OT words once fully imported (verified against
 *  the 4 source files: ~76k+102k+30k+75k across Gen-Deu/Jos-Est/Job-Sng/Isa-Mal). */
private const val HEBREW_WORD_THRESHOLD = 200_000

/** The full Treasury of Scripture Knowledge dataset has on the order of
 *  300k+ rows; this is a conservative "did this actually finish" floor. */
private const val CROSS_REFERENCE_THRESHOLD = 150_000

/**
 * Bump this whenever the bundled Telugu JSON data under `assets/telugu` changes (new
 * source text, corrections, etc.) so [BibleDataInitializer] re-imports it
 * even on devices that already have a full verse count from an older
 * bundle. History:
 *  1 - original wordproject.org scrape
 *  2 - swapped to Telugu BSI XML source
 */
private const val TELUGU_DATA_VERSION = 2
private const val TELUGU_DATA_VERSION_KEY = "telugu_data_version"

/** Webster's 1828 dictionary has ~61.8k headwords once fully imported;
 *  used as a "did this finish" floor, same idea as [FULL_BIBLE_VERSE_THRESHOLD]. */
private const val WEBSTER_ENTRY_THRESHOLD = 50_000

/**
 * total <= 0 means "indeterminate" (row count not known ahead of a full
 * parse) — the UI should show a spinner-style bar rather than a fraction.
 */
data class ImportProgress(
    val label: String,
    val detail: String,
    val done: Int,
    val total: Int
)

/**
 * Runs the one-time bundled/downloaded data import, in order:
 *  1. English KJV text (network, ~10MB, once)
 *  2. Telugu translation (bundled assets, no network needed)
 *  3. Webster's 1828 dictionary (bundled assets, no network needed)
 *  4. Greek interlinear — STEPBible TAGNT (network, a few MB, once)
 *  5. Hebrew interlinear — STEPBible TAHOT (network, a few MB, once)
 *  6. Cross references — Treasury of Scripture Knowledge (network, once)
 *
 * All steps are idempotent and safe to re-run — each checks what's already
 * in Room before doing any work, so this can be called on every app launch
 * cheaply once everything's imported.
 *
 * [progress] reflects whatever's actively importing right now (null = idle).
 * [errorMessage] persists after a failed KJV download until [retry] succeeds,
 * independent of [progress], so later steps running afterward don't silently
 * overwrite/hide that specific failure. Greek/Hebrew/cross-reference
 * failures don't set [errorMessage] — they're bonus content on top of the reading
 * experience (same philosophy as the Capacitor app), so they fail quietly
 * and are simply retried on the next launch.
 */
class BibleDataInitializer(
    private val context: Context,
    private val dao: BibleDao
) {
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Avoids hammering the network with retries every time this is called
    // within the same app process if a download failed once already (e.g.
    // no connection). The user can still force a retry via retry().
    private var kjvAttemptedThisSession = false
    private var greekAttemptedThisSession = false
    private var hebrewAttemptedThisSession = false
    private var xrefAttemptedThisSession = false

    suspend fun ensureImported() {
        maybeImportKjv()
        maybeImportTelugu()
        maybeImportWebster()
        maybeImportGreek()
        maybeImportHebrew()
        maybeImportCrossReferences()
        _progress.value = null
    }

    /** Force a retry of whichever step(s) haven't completed yet — used by a "Retry" action in the UI. */
    suspend fun retry() {
        kjvAttemptedThisSession = false
        greekAttemptedThisSession = false
        hebrewAttemptedThisSession = false
        xrefAttemptedThisSession = false
        _errorMessage.value = null
        ensureImported()
    }

    private suspend fun maybeImportKjv() {
        if (dao.countAllVerses() >= FULL_BIBLE_VERSE_THRESHOLD) return
        if (kjvAttemptedThisSession) return
        kjvAttemptedThisSession = true

        _progress.value = ImportProgress("Downloading Bible text (one-time, ~10MB)", "Connecting\u2026", 0, 1)
        try {
            KjvImporter.importInto(dao) { bookName, done, total ->
                _progress.value = ImportProgress("Downloading Bible text (one-time, ~10MB)", bookName, done, total)
            }
            _errorMessage.value = null
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Couldn't download Bible text (${e.message ?: "no connection"}). " +
                "Chapters will load individually instead until this succeeds."
        }
    }

    private suspend fun maybeImportTelugu() {
        val prefs = context.getSharedPreferences("my_bible_prefs", Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(TELUGU_DATA_VERSION_KEY, 0)
        val alreadyCurrent = storedVersion >= TELUGU_DATA_VERSION &&
            dao.countTeluguVerses() >= FULL_BIBLE_VERSE_THRESHOLD
        if (alreadyCurrent) return

        _progress.value = ImportProgress("Loading Telugu translation", "Starting\u2026", 0, 1)
        TeluguImporter.importInto(context, dao) { bookName, done, total ->
            _progress.value = ImportProgress("Loading Telugu translation", bookName, done, total)
        }
        prefs.edit().putInt(TELUGU_DATA_VERSION_KEY, TELUGU_DATA_VERSION).apply()
    }

    private suspend fun maybeImportWebster() {
        if (dao.countWebsterEntries() >= WEBSTER_ENTRY_THRESHOLD) return
        _progress.value = ImportProgress("Loading English dictionary", "Starting\u2026", 0, 1)
        WebsterImporter.importInto(context, dao) { done, total ->
            _progress.value = ImportProgress("Loading English dictionary", "$done of $total words", done, total)
        }
    }

    private suspend fun maybeImportGreek() {
        if (dao.countGreekWords() >= GREEK_WORD_THRESHOLD) return
        if (greekAttemptedThisSession) return
        greekAttemptedThisSession = true

        _progress.value = ImportProgress("Loading Greek interlinear (one-time)", "Connecting\u2026", 0, 1)
        try {
            GreekImporter.importInto(dao) { bookName, done, total ->
                _progress.value = ImportProgress("Loading Greek interlinear (one-time)", bookName, done, total)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Quiet failure — see class doc. Falls back to the old
            // hardcoded/hash-generated interlinear until this succeeds.
        }
    }

    private suspend fun maybeImportHebrew() {
        if (dao.countHebrewWords() >= HEBREW_WORD_THRESHOLD) return
        if (hebrewAttemptedThisSession) return
        hebrewAttemptedThisSession = true

        _progress.value = ImportProgress("Loading Hebrew interlinear (one-time)", "Connecting\u2026", 0, 1)
        try {
            HebrewImporter.importInto(dao) { bookName, done, total ->
                _progress.value = ImportProgress("Loading Hebrew interlinear (one-time)", bookName, done, total)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Quiet failure — see class doc. Falls back to no Hebrew
            // interlinear (same as before this feature existed) until this
            // succeeds on a later launch.
        }
    }

    private suspend fun maybeImportCrossReferences() {
        if (dao.countCrossReferences() >= CROSS_REFERENCE_THRESHOLD) return
        if (xrefAttemptedThisSession) return
        xrefAttemptedThisSession = true

        // total = -1: row count isn't known ahead of a full parse, so the
        // banner shows an indeterminate bar instead of a fraction.
        _progress.value = ImportProgress("Loading cross references (one-time)", "Downloading\u2026", 0, -1)
        try {
            CrossReferenceImporter.importInto(dao) { linesImported ->
                _progress.value = ImportProgress(
                    "Loading cross references (one-time)",
                    "$linesImported imported so far\u2026",
                    linesImported,
                    -1
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Quiet failure — falls back to the old hardcoded cross-references.
        }
    }
}
