package com.example.mybible.data

import com.example.mybible.data.local.BibleDao
import com.example.mybible.data.local.GreekWordEntity
import com.example.mybible.ui.components.BIBLE_BOOKS
import com.example.mybible.ui.components.BOOK_CHAPTER_COUNTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads STEPBible's TAGNT (Translators Amalgamated Greek NT — CC BY 4.0,
 * Tyndale House Cambridge / STEPBible.org) and imports real interlinear
 * Greek/transliteration/gloss/Strong's data for every NT verse, replacing
 * the ~15-word hardcoded dictionary + hash-generated fake words.
 *
 * Source URLs, book-abbreviation table, chapter-block extraction, and the
 * per-word row parser are all ported verbatim from the Capacitor app's
 * `loadGreekForChapter`/`extractChapterBlock`/`parseTagntChapter`, which are
 * already proven against these exact files. Word filtering keeps anything
 * tagged 'N' or 'K' (modern critical text and/or Textus Receptus/KJV
 * tradition) and drops words that belong ONLY to other manuscript editions.
 */
object GreekImporter {

    private const val TAGNT_URL_GOSPELS =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/refs/heads/master/" +
            "Translators%20Amalgamated%20OT%2BNT/TAGNT%20Mat-Jhn%20-%20Translators%20Amalgamated%20Greek%20NT%20-%20STEPBible.org%20CC-BY.txt"
    private const val TAGNT_URL_ACTS_REV =
        "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/refs/heads/master/" +
            "Translators%20Amalgamated%20OT%2BNT/TAGNT%20Act-Rev%20-%20Translators%20Amalgamated%20Greek%20NT%20-%20STEPBible.org%20CC-BY.txt"

    // TAGNT's own book-abbreviation scheme, indexed to line up with
    // BIBLE_BOOKS[39..] (Matthew..Revelation — the 27 NT books).
    private val TAGNT_ABBR = listOf(
        "Mat", "Mrk", "Luk", "Jhn", "Act", "Rom", "1Co", "2Co", "Gal", "Eph",
        "Php", "Col", "1Th", "2Th", "1Ti", "2Ti", "Tit", "Phm", "Heb", "Jas",
        "1Pe", "2Pe", "1Jn", "2Jn", "3Jn", "Jud", "Rev"
    )
    private const val OT_COUNT = 39

    private val REF_REGEX = Regex("^[0-9A-Za-z]+\\.(\\d+)\\.(\\d+)#(\\d+)=(\\S*)$")
    private val GREEK_REGEX = Regex("^(\\S+)\\s*\\(([^)]+)\\)")
    private val HEADER_REGEX = Regex("\\n# ([0-9A-Za-z]+)\\.(\\d+)\\.")
    private val JESUS_WORD_TYPE = Regex("[NnKk]")

    private fun abbrFor(bookName: String): String? {
        val idx = BIBLE_BOOKS.indexOf(bookName) - OT_COUNT
        return TAGNT_ABBR.getOrNull(idx)
    }

    private fun filePartFor(abbr: String) =
        if (abbr in listOf("Mat", "Mrk", "Luk", "Jhn")) "gospels" else "actsRev"

    suspend fun importInto(
        dao: BibleDao,
        onBookImported: (bookName: String, booksDone: Int, booksTotal: Int) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val ntBooks = BIBLE_BOOKS.drop(OT_COUNT) // Matthew..Revelation, 27 books
        val booksTotal = ntBooks.size
        var booksDone = 0
        var anySucceeded = false

        for (part in listOf("gospels", "actsRev")) {
            val url = if (part == "gospels") TAGNT_URL_GOSPELS else TAGNT_URL_ACTS_REV
            val booksInPart = ntBooks.filter { name -> abbrFor(name)?.let { filePartFor(it) == part } == true }

            val fullText = fetchTextOrNull(url)
            if (fullText == null) {
                // This file failed — skip its books but keep going with the other file.
                booksDone += booksInPart.size
                booksInPart.forEach { onBookImported(it, booksDone, booksTotal) }
                continue
            }

            for (bookName in booksInPart) {
                val abbr = abbrFor(bookName) ?: continue
                val chapterCount = BOOK_CHAPTER_COUNTS[bookName] ?: 0
                val words = mutableListOf<GreekWordEntity>()
                for (chapter in 1..chapterCount) {
                    val block = extractChapterBlock(fullText, abbr, chapter) ?: continue
                    words.addAll(parseTagntChapter(block, bookName, chapter))
                }
                if (words.isNotEmpty()) {
                    dao.insertGreekWords(words)
                    anySucceeded = true
                }
                booksDone++
                onBookImported(bookName, booksDone, booksTotal)
            }
        }
        anySucceeded
    }

    // Slices out just the lines belonging to one chapter (from the
    // "# Abbr.C.1" verse-1 marker up to, but not including, the next
    // chapter/book's marker).
    private fun extractChapterBlock(fullText: String, abbr: String, chapter: Int): String? {
        val marker = "# $abbr.$chapter."
        val start = fullText.indexOf(marker)
        if (start == -1) return null
        var end = fullText.length
        for (m in HEADER_REGEX.findAll(fullText, start)) {
            val mAbbr = m.groupValues[1]
            val mChapter = m.groupValues[2].toIntOrNull()
            if (mAbbr != abbr || mChapter != chapter) {
                end = m.range.first
                break
            }
        }
        return fullText.substring(start, end)
    }

    // Parses per-word data rows like:
    // "Mat.1.1#01=NKO\tΒίβλος (Biblos)\t[The] book\tG0976=N-NSF\tβίβλος=book\tNA28+..."
    private fun parseTagntChapter(blockText: String, bookName: String, chapter: Int): List<GreekWordEntity> {
        val result = mutableListOf<GreekWordEntity>()
        val orderCounters = mutableMapOf<Int, Int>()
        for (line in blockText.split("\n")) {
            if (line.isEmpty() || line[0] == '#') continue
            val cols = line.split("\t")
            if (cols.size < 5) continue
            val m = REF_REGEX.find(cols[0].trim()) ?: continue
            val verse = m.groupValues[2].toIntOrNull() ?: continue
            val wordType = m.groupValues[4]
            if (!JESUS_WORD_TYPE.containsMatchIn(wordType)) continue // "other manuscripts only" word — not in KJV

            val greekCell = cols[1]
            val gm = GREEK_REGEX.find(greekCell.trim())
            val greek = gm?.groupValues?.get(1) ?: greekCell.trim()
            val translit = gm?.groupValues?.get(2) ?: ""

            val dStrongCell = cols.getOrElse(3) { "" }
            val dStrong = dStrongCell.substringBefore("=").trim()
            val morphology = dStrongCell.substringAfter("=", "").trim()

            val glossCell = cols.getOrElse(4) { "" }
            val gloss = if (glossCell.contains("=")) glossCell.split("=").drop(1).joinToString("=") else ""

            if (greek.isEmpty()) continue
            val orderIndex = orderCounters.getOrDefault(verse, 0)
            orderCounters[verse] = orderIndex + 1
            result.add(
                GreekWordEntity(
                    book = bookName,
                    chapter = chapter,
                    verse = verse,
                    orderIndex = orderIndex,
                    greek = greek,
                    transliteration = translit,
                    gloss = gloss,
                    strongs = dStrong,
                    morphology = morphology
                )
            )
        }
        return result
    }
}
