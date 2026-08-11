package com.example.mybible.data

import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.CrossReferenceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the Treasury of Scripture Knowledge cross-reference dataset (the
 * same file/source the Capacitor app uses) and imports it into Room,
 * replacing the 5-hardcoded-verses + 4-generic-fallback-refs placeholder.
 *
 * File format is tab-separated, one row per cross-reference, e.g.:
 * `Gen.1.1\tJhn.1.1-Jhn.1.3\t...` (line 0 is a header row). Parsing logic
 * ported verbatim from the Capacitor app's `parseXrefLine`/`loadXrefDataset`.
 */
object CrossReferenceImporter {

    private const val XREF_SOURCE_URL =
        "https://raw.githubusercontent.com/anAgent/bible_databases/master/cross_references.txt"

    private val REF_REGEX = Regex("^(\\w+)\\.(\\d+)\\.(\\d+)$")
    private const val BATCH_SIZE = 4000

    private data class ParsedRef(
        val fromBook: String, val fromChapter: Int, val fromVerse: Int,
        val toBook: String, val toChapter: Int, val toVerse: Int, val toVerseEnd: Int
    )

    private fun parseLine(line: String): ParsedRef? {
        val parts = line.split("\t")
        if (parts.size < 2) return null

        val fm = REF_REGEX.find(parts[0].trim()) ?: return null
        val fromBook = resolveBookName(fm.groupValues[1]) ?: return null

        val toParts = parts[1].trim().split("-")
        val sm = REF_REGEX.find(toParts[0].trim()) ?: return null
        val toBook = resolveBookName(sm.groupValues[1]) ?: return null

        var toVerseEnd = sm.groupValues[3].toIntOrNull() ?: return null
        if (toParts.size > 1) {
            Regex("(\\d+)$").find(toParts[1].trim())?.let {
                toVerseEnd = it.groupValues[1].toIntOrNull() ?: toVerseEnd
            }
        }

        return ParsedRef(
            fromBook = fromBook,
            fromChapter = fm.groupValues[2].toIntOrNull() ?: return null,
            fromVerse = fm.groupValues[3].toIntOrNull() ?: return null,
            toBook = toBook,
            toChapter = sm.groupValues[2].toIntOrNull() ?: return null,
            toVerse = sm.groupValues[3].toIntOrNull() ?: return null,
            toVerseEnd = toVerseEnd
        )
    }

    /**
     * Downloads + parses the dataset and writes it into Room in batches.
     * Progress is reported as running totals since the file's row count
     * isn't known ahead of a full parse (shown as an indeterminate bar).
     */
    suspend fun importInto(
        dao: BibleDao,
        onProgress: (linesImported: Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val text = fetchTextOrNull(XREF_SOURCE_URL) ?: return@withContext false

        val batch = mutableListOf<CrossReferenceEntity>()
        var imported = 0
        val lines = text.splitToSequence("\n").drop(1) // line 0 is the header row
        for (line in lines) {
            val ref = parseLine(line) ?: continue
            batch.add(
                CrossReferenceEntity(
                    fromBook = ref.fromBook,
                    fromChapter = ref.fromChapter,
                    fromVerse = ref.fromVerse,
                    toBook = ref.toBook,
                    toChapter = ref.toChapter,
                    toVerse = ref.toVerse,
                    toVerseEnd = ref.toVerseEnd
                )
            )
            if (batch.size >= BATCH_SIZE) {
                dao.insertCrossReferences(batch)
                imported += batch.size
                onProgress(imported)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            dao.insertCrossReferences(batch)
            imported += batch.size
            onProgress(imported)
        }
        imported > 0
    }
}
